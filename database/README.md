docker-compose up -d
chạy lệnh này để cài EXTENSION VECTOR cho database
docker exec -it postgres_db psql -U postgres -d postgres -c "CREATE EXTENSION IF NOT EXISTS vector;"
chạy lệnh này để thêm cột vector cho bảng document
docker exec -it postgres_db psql -U postgres -d postgres -c "
CREATE TABLE IF NOT EXISTS document (
    id bigserial PRIMARY KEY,
    content text,
    metadata jsonb,
    embedding vector(768)
);"
docker exec -it postgres_db psql -U postgres -d postgres -c "ALTER TABLE document ADD COLUMN embedding vector(768);"
docker exec -it postgres_db psql -U postgres -d postgres -c "CREATE INDEX ON document USING hnsw (embedding vector_cosine_ops);"