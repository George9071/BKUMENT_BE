import psycopg2
from typing import List, Dict, Any
import numpy as np
from typing import List
from app.config import get_settings

settings = get_settings()

class VectorService:
    def __init__(self, embedding_model):
        self.model = embedding_model

    def get_embedding(self, text: str, is_query: bool = False) -> List[float]:
        if not text or not text.strip():
            return []

        if is_query:
            return self.model.encode("query: " + text).tolist()

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
            
        chunk_vectors = self.model.encode(chunks)
        avg_vector = np.mean(chunk_vectors, axis=0)
        norm = np.linalg.norm(avg_vector)
        if norm > 0:
            avg_vector = avg_vector / norm
            
        return avg_vector.tolist()
    
    # later
    async def search_documents(self, query_text: str, limit: int = 5) -> List[Dict[str, Any]]:
        """Tìm kiếm ngữ nghĩa trong DB"""
        conn = None
        try:
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
                SELECT id,
                       1 - (embedding <=> %s::vector) AS similarity
                FROM document
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> %s::vector
                LIMIT %s;
            """

            cur.execute(sql, (query_vector, query_vector, limit))
            rows = cur.fetchall()

            documents = [
                {"id": row[0], "similarity": float(row[1])} 
                for row in rows
            ]
            return documents

        except Exception as e:
            print(f"Vector DB Error: {e}")
            raise e
        finally:
            if conn:
                conn.close()