import asyncio
import functools
import math
import logging
import asyncpg
from concurrent.futures import ThreadPoolExecutor
from typing import List, Dict, Any

import numpy as np
import psycopg2

from app.config import get_settings
from app.repository.httpClient.ProfileClient import ProfileClient

logger = logging.getLogger(__name__)

settings = get_settings()

_embedding_executor = ThreadPoolExecutor(
    max_workers=int(getattr(settings, "EMBEDDING_MAX_WORKERS", 2)),
    thread_name_prefix="embedding-worker",
)

# ---------------------------------------------------------------------------
# Module-level thread pool for CPU-bound embedding inference
# ---------------------------------------------------------------------------
# Sizing guidance:
#   - On CPU: set max_workers to the number of physical cores available for
#     inference (usually 1–4 to avoid thrashing).
#   - On GPU: a single worker is typically sufficient since GPU inference is
#     already highly parallelised internally; multiple threads would contend.
#   - Default 2; tune via EMBEDDING_MAX_WORKERS env var.

class VectorService:
    """
    Provides 2 capabilities for the recommendation and semantic-search pipeline:
 
    1. get_embedding() — synchronous; converts text to a normalised 768-dim vector.
       Supports sliding-window chunking for documents that exceed the model's
       512-token context window.
 
    2. search_documents() — async; hybrid semantic + full-text search against PostgreSQL (pgvector)
 
    The embedding model is injected at construction time for testability and
    to support model-swapping without changing this class.
    """
    def __init__(self, embedding_model):
        self.model = embedding_model
        self.pool = None

    async def _get_pool(self):
        if self.pool is None:
            self.pool = await asyncpg.create_pool(
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                database=settings.DB_NAME,
                user=settings.POSTGRES_USERNAME,
                password=settings.POSTGRES_PASSWORD,
                min_size=5, 
                max_size=20 
            )
        return self.pool

    def get_embedding(self, text: str, is_query: bool = False) -> List[float]:
        """
        Converts a text string into a normalised 768-dimensional embedding vector.
 
        Uses the E5 instruction-tuned prefix convention:
          - "query: <text>"   for search queries  (is_query=True)
          - "passage: <text>" for document passages (is_query=False)
 
        This asymmetric prompting is required by E5 models so that query and
        passage vectors are comparable via cosine similarity.
 
        Short texts (≤ chunk_size words) are encoded in a single forward pass.
        Long texts use a sliding-window strategy:
          - Split into overlapping chunks of `chunk_size` words.
          - Each chunk is encoded separately in one batched call.
          - Chunk vectors are averaged then re-normalised to unit length.
 
        Chunking parameters:
          chunk_size = 250 words  (≈ 350-400 tokens; safely within 512-token limit)
          overlap    = 30  words  (preserves sentence context across boundaries)
 
        OPTIMIZATION — normalize_embeddings=True on short-text path:
          Previously: 
            - encode() was called WITHOUT normalize_embeddings on the short path, producing a vector of arbitrary magnitude.
            - When compared with cosine similarity against normalised vectors (produced by the chunked path and by the query path), 
            un-normalised vectors give incorrect similarity scores      
            — a document's score depends partly on its embedding magnitude rather than purely on semantic direction.
 
          OPTIMIZE: normalize_embeddings=True is now passed on ALL paths. 
          All vectors are unit-length, making cosine similarity consistent regardless of whether
          the text was short or long enough to trigger chunking.
 
        Args:
            text:     Text to embed. Returns [] for empty or whitespace-only input.
            is_query: True for user search queries; False for document content.
 
        Returns:
            list[float] of length 768 (unit-length), or [] for empty input.
        """        
        if not text or not text.strip():
            return []

        if is_query:
            return self.model.encode(
                "query: " + text,
                normalize_embeddings=True
            ).tolist()

        chunk_size = 250  # words per chunk
        overlap = 30      # words shared between consecutive chunks
        
        words = text.split()
        
        if len(words) <= chunk_size:
            # Without normalisation, short-document vectors had arbitrary magnitude,
            # producing inconsistent cosine-similarity scores against normalised vectors.
            return self.model.encode(
                "passage: " + text,
                normalize_embeddings=True,
            ).tolist()

        # Long document: sliding-window chunking.
        chunks = [
            "passage: " + " ".join(words[i : i + chunk_size])
            for i in range(0, len(words), chunk_size - overlap)
        ]
            
        # Batched encode: all chunks in a single forward pass for efficiency.
        chunk_vectors = self.model.encode(chunks, normalize_embeddings=True)

        # Average the chunk vectors then re-normalise
        # np.mean(axis=0) produces the centroid
        # dividing by its L2 norm maps it back onto the unit sphere so cosine similarity remains valid.
        avg_vector = np.mean(chunk_vectors, axis=0)
        norm = np.linalg.norm(avg_vector)
        if norm > 0:
            avg_vector = avg_vector / norm
 
        return avg_vector.tolist()
    
    async def search_documents(self, query_text: str, page: int = 1, limit: int = 10) -> Dict[str, Any]:
        """
        Hybrid semantic + full-text search against PostgreSQL (pgvector)

        Search strategy:
          - Vector score (weight 0.7): cosine similarity via pgvector <=> operator. score = 1 - cosine_distance, range [0, 1].
          - Keyword score (weight 0.3): ts_rank_cd over title + keywords tsvector.
          - final_score = 0.7 * vector_score + 0.3 * keyword_score

        Response shape:
          Matches Spring Boot's Page<T> JSON schema so the Java gateway can deserialise it without a transformation layer.  

        Args:
            query_text: Natural-language search query from the user.
            page:       1-based page number (converted to 0-based for Spring Boot).
            limit:      Number of results per page.
 
        Returns:
            Dict matching Spring Boot's Page<T> JSON schema.
 
        Raises:
            Exception: Re-raises DB or unexpected errors after logging.    
        """
        offset = (page - 1) * limit

        loop = asyncio.get_running_loop()

        # query_vector = self.get_embedding(query_text, True)
        query_vector = await loop.run_in_executor(
            _embedding_executor,
            functools.partial(self.get_embedding, query_text, is_query=True),
        )

        # Guard: if the query produced no vector (empty input), skip the search
        if not query_vector:
            return _empty_page(page, limit, offset)    

        if hasattr(query_vector, "tolist"):
            query_vector = query_vector.tolist()

        # NOTE: replace with asyncpg connection pool for non-blocking DB I/O.
        # conn = psycopg2.connect(
        #     host=settings.DB_HOST,
        #     port=settings.DB_PORT,
        #     dbname=settings.DB_NAME,
        #     user=settings.POSTGRES_USERNAME,
        #     password=settings.POSTGRES_PASSWORD,
        # )
        # cur = conn.cursor()

        # --- CONTEXTUAL HYBRID LOGIC ---
        word_count = len(query_text.split())
        w_vector, w_keyword = (0.8, 0.2) if word_count > 5 else (0.4, 0.6)

        sql = f"""
            WITH hybrid_scores AS (
                SELECT
                    d.id,
                    d.preview_image_url,
                    d.description,
                    r.created_at,
                    r.title,
                    r.owner_id,
                    (1 - (d.embedding <=> $1::vector))              AS vector_score,
                    ts_rank_cd(
                        to_tsvector('simple',
                            COALESCE(r.title, '') || ' ' ||
                            COALESCE(array_to_string(d.keywords, ' '), '')),
                        plainto_tsquery('simple', $2)
                    )                                               AS keyword_score
                FROM document d
                JOIN resource r ON d.id = r.id
                WHERE d.embedding IS NOT NULL
            )
            SELECT
                id,
                title,
                preview_image_url,
                description,
                created_at,
                vector_score,
                keyword_score,
                (vector_score * {w_vector}) + (keyword_score * {w_keyword}) AS final_score,
                owner_id,
                COUNT(*) OVER()                               AS total_count
            FROM hybrid_scores
            ORDER BY final_score DESC
            LIMIT $3 OFFSET $4
        """            

        vector_str = str(query_vector)

        try:
            async with self._db.acquire() as conn:
                rows = await conn.fetch(sql, vector_str, query_text, limit, offset)

        except Exception as e:
            logger.error(
                "DB query failed in search_documents (query='%s'): %s",
                query_text,
                str(e),
            )
            raise

        # cur.execute(sql, (query_vector, query_text, limit, offset))
        # rows = cur.fetchall()

        # total_elements = rows[0][9] if rows else 0
        total_elements = rows[0]["total_count"] if rows else 0

        documents = [
            {
                "id": str(row["id"]),
                "title": row["title"],
                "preview_image_url": row["preview_image_url"],
                "description": row["description"],
                "createdAt": row["created_at"],
                "vector_score": _safe_float(row["vector_score"]),
                "keyword_score": _safe_float(row["keyword_score"]),
                "score": _safe_float(row["final_score"]),
                "owner_id": str(row["owner_id"]) if row["owner_id"] else None,
                "author": None,  # populated by profile enrichment below
            }
            for row in rows
        ]

        profile_client = ProfileClient(base_url=settings.PROFILE_SERVICE_URL) 

        async def fetch_and_map_profile(doc: dict) -> None:
            """
            Fetches the author profile and writes it into the document dict.
            """
            owner_id = doc.get("owner_id")
            if not owner_id:
                return
            try:
                profile_resp = await profile_client.find_user_profile_by_id(owner_id)
                # print(profile_resp)
                if profile_resp:
                    doc["author"] = {
                        "id": profile_resp.id,
                        "name": profile_resp.fullName,
                        "avatarUrl": profile_resp.avatarUrl
                    }
            except Exception as e:
                # print(f"Lỗi khi gọi profile-service cho user {owner_id}: {e}")
                logger.warning("Failed to fetch profile for owner %s: %s", owner_id, str(e))
            finally:
                doc.pop("owner_id", None)

        try:
            await asyncio.gather(*(fetch_and_map_profile(doc) for doc in documents))
        finally:
            await profile_client.close()

        # Convert 1-based page to 0-based for Spring Boot Page<T> schema.
        spring_page = page - 1
        total_pages = math.ceil(total_elements / limit) if limit > 0 else 0

        return {
            "content": documents,
            "pageable": {
                "pageNumber": spring_page,
                "pageSize": limit,
                "sort": [],
                "offset": offset,
                "paged": True,
                "unpaged": False,
            },
            "totalPages": total_pages,
            "totalElements": total_elements,
            "last": spring_page >= (total_pages - 1) if total_pages > 0 else True,
            "numberOfElements": len(documents),
            "first": spring_page == 0,
            "size": limit,
            "number": spring_page,
            "sort": [],
            "empty": len(documents) == 0,
        }

def _safe_float(val) -> float:
    """
    Converts a DB column value to float, returning 0.0 for None, NaN, or Infinity.
    pgvector can return NaN for cosine distance on all-zero embedding vectors.
    """
    if val is None:
        return 0.0
    try:
        f = float(val)
        return 0.0 if (math.isnan(f) or math.isinf(f)) else f
    except (ValueError, TypeError):
        return 0.0                

def _empty_page(page: int, limit: int, offset: int) -> Dict[str, Any]:
    """Returns a Spring Boot-shaped empty Page<T> response."""
    spring_page = page - 1
    return {
        "content": [],
        "pageable": {
            "pageNumber": spring_page,
            "pageSize": limit,
            "sort": [],
            "offset": offset,
            "paged": True,
            "unpaged": False,
        },
        "totalPages": 0,
        "totalElements": 0,
        "last": True,
        "numberOfElements": 0,
        "first": spring_page == 0,
        "size": limit,
        "number": spring_page,
        "sort": [],
        "empty": True,
    }