import psycopg2
from typing import List, Dict, Any
import numpy as np
from typing import List
from app.config import get_settings
import math

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
    
    async def search_documents(self, query_text: str, page: int = 1, limit: int = 10) -> List[Dict[str, Any]]:
        """Tìm kiếm ngữ nghĩa có phân trang"""
        conn = None
        try:
            # 1. Tính toán Offset
            offset = (page - 1) * limit

            query_vector = self.get_embedding(query_text, True)

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
                        r.title,
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
                    id,
                    title,
                    (vector_score * 0.7) + (keyword_score * 0.3) AS final_score,
                    vector_score,
                    keyword_score
                FROM hybrid_scores
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

            documents = [
                {
                    "id": str(row[0]), 
                    "title": row[1],
                    "score": safe_float(row[2]),        
                    "vector_score": safe_float(row[3]), 
                    "keyword_score": safe_float(row[4]) 
                } 
                for row in rows
            ]
            return documents

        except Exception as e:
            print(f"Hybrid Search Error: {e}")
            raise e
        finally:
            if conn:
                conn.close()