import psycopg2
from typing import List, Dict, Any
from app.config import get_settings

settings = get_settings()

class VectorService:
    def __init__(self, embedding_model):
        self.model = embedding_model

    def get_embedding(self, text: str) -> List[float]:
        if not text or not text.strip():
            return []
        return self.model.encode(text).tolist()

    # later
    async def search_documents(self, query_text: str, limit: int = 5) -> List[Dict[str, Any]]:
        """Tìm kiếm ngữ nghĩa trong DB"""
        conn = None
        try:
            query_vector = self.get_embedding(query_text)

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