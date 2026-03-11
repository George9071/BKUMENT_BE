import psycopg2
from typing import List, Dict, Any
import numpy as np
from typing import List
import asyncio
from app.config import get_settings
import math
from app.repository.httpClient.ProfileClient import ProfileClient

settings = get_settings()

class VectorService:
    def __init__(self, embedding_model):
        self.model = embedding_model

    def get_embedding(self, text: str, is_query: bool = False) -> List[float]:
        if not text or not text.strip():
            return []

        if is_query:
            return self.model.encode(
                "query: " + text,
                normalize_embeddings=True
            ).tolist()

        chunk_size = 250  
        overlap = 30 
        
        words = text.split()
        
        if len(words) <= chunk_size:
            return self.model.encode("passage: " + text).tolist()
        
        chunks = []
        for i in range(0, len(words), chunk_size - overlap):
            chunk_words = words[i : i + chunk_size]
            chunk_text = " ".join(chunk_words)
            
            chunks.append("passage: " + chunk_text)
            

        chunk_vectors = self.model.encode(
            chunks,
            normalize_embeddings=True
        )
        avg_vector = np.mean(chunk_vectors, axis=0)
        avg_vector = avg_vector / np.linalg.norm(avg_vector)
            
        return avg_vector.tolist()
    
    async def search_documents(self, query_text: str, page: int = 1, limit: int = 10) -> Dict[str, Any]:
        """Tìm kiếm ngữ nghĩa có phân trang chuẩn Spring Boot"""
        conn = None
        try:
            offset = (page - 1) * limit
            query_vector = self.get_embedding(query_text, True)
            if hasattr(query_vector, "tolist"):
                query_vector = query_vector.tolist()

            conn = psycopg2.connect(
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                dbname=settings.DB_NAME,
                user=settings.DB_USER,
                password=settings.DB_PASSWORD,
            )
            cur = conn.cursor()

            sql = """
                WITH hybrid_scores AS (
                    SELECT 
                        d.id,
                        d.preview_image_url,
                        d.description,
                        r.created_at,
                        r.title,
                        r.owner_id,
                        (1 - (d.embedding <=> %s::vector)) AS vector_score,
                        ts_rank_cd(
                            to_tsvector('simple', COALESCE(r.title, '') || ' ' || COALESCE(d.keywords, '')), 
                            plainto_tsquery('simple', %s)
                        ) AS keyword_score
                    FROM document d
                    JOIN resource r ON d.id = r.id
                    WHERE d.embedding IS NOT NULL
                )
                SELECT 
                    id,                 -- index 0
                    title,              -- index 1
                    preview_image_url,  -- index 2
                    description,        -- index 3
                    created_at,         -- index 4
                    vector_score,       -- index 5
                    keyword_score,      -- index 6
                    final_score,        -- index 7
                    owner_id,           -- index 8
                    COUNT(*) OVER() AS total_count -- index 9
                FROM (
                    SELECT *, (vector_score * 0.7) + (keyword_score * 0.3) AS final_score 
                    FROM hybrid_scores
                ) sub
                ORDER BY final_score DESC
                LIMIT %s OFFSET %s;
            """

            cur.execute(sql, (query_vector, query_text, limit, offset))
            rows = cur.fetchall()

            def safe_float(val):
                if val is None: return 0.0
                try:
                    f_val = float(val)
                    if math.isnan(f_val) or math.isinf(f_val): return 0.0
                    return f_val
                except (ValueError, TypeError): return 0.0

            total_elements = rows[0][9] if rows else 0

            documents = [
                {
                    "id": str(row[0]), 
                    "title": row[1],
                    "preview_image_url": row[2],
                    "description": row[3],
                    "createdAt": row[4],
                    "vector_score": safe_float(row[5]), 
                    "keyword_score": safe_float(row[6]),
                    "score": safe_float(row[7]),
                    "owner_id": str(row[8]) if row[8] else None, 
                    "author": None 
                } 
                for row in rows
            ]

            profile_client = ProfileClient(base_url=settings.PROFILE_SERVICE_URL) 
            
            async def fetch_and_map_profile(doc: dict):
                owner_id = doc.get("owner_id")
                if not owner_id:
                    return
                try:
                    profile_resp = await profile_client.find_user_profile_by_id(owner_id)
                    if profile_resp and profile_resp.result:
                        doc["author"] = {
                            "id": profile_resp.result.id,
                            "name": profile_resp.result.fullName,
                            "avatarUrl": profile_resp.result.avatarUrl
                        }
                except Exception as e:
                    print(f"Lỗi khi gọi profile-service cho user {owner_id}: {e}")
                finally:
                    doc.pop("owner_id", None)

            try:
                await asyncio.gather(*(fetch_and_map_profile(doc) for doc in documents))
            finally:
                await profile_client.close()

            spring_page_number = page - 1 
            total_pages = math.ceil(total_elements / limit) if limit > 0 else 0
            current_elements = len(documents)

            page_result = {
                "content": documents,
                "pageable": {
                    "pageNumber": spring_page_number,
                    "pageSize": limit,
                    "sort": [],
                    "offset": offset,
                    "paged": True,
                    "unpaged": False
                },
                "totalPages": total_pages,
                "totalElements": total_elements,
                "last": spring_page_number >= (total_pages - 1) if total_pages > 0 else True,
                "numberOfElements": current_elements,
                "first": spring_page_number == 0,
                "size": limit,
                "number": spring_page_number,
                "sort": [],
                "empty": current_elements == 0
            }

            return page_result

        except Exception as e:
            print(f"Hybrid Search Error: {e}")
            raise e
        finally:
            if conn:
                conn.close()