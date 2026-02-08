#!/bin/bash

# CẤU HÌNH KẾT NỐI ONLINE DB
# Bạn thay thế bằng thông tin thực tế của bạn
DB_HOST="206.189.92.152"
DB_USER="bkument"
DB_NAME="bkument_db"
DB_PORT="5432"

# File dump đầu vào
INPUT_FILE="localdb.sql"

# Kiểm tra file tồn tại
if [ ! -f "$INPUT_FILE" ]; then
    echo "Lỗi: Không tìm thấy file $INPUT_FILE"
    exit 1
fi

echo "Dang xu ly du lieu..."

# --- BƯỚC 1: TRÍCH XUẤT DỮ LIỆU TỪ FILE SQL ---
# Trích xuất dữ liệu bảng RESOURCE (bỏ dòng COPY và dòng \.)
sed -n '/^COPY public.resource/,/^\\./p' "$INPUT_FILE" | sed '1d;$d' > resource_data.dat

# Trích xuất dữ liệu bảng DOCUMENT (bỏ dòng COPY và dòng \.)
sed -n '/^COPY public.document/,/^\\./p' "$INPUT_FILE" | sed '1d;$d' > document_data.dat

echo "Da trich xuat xong du lieu ra file tam."

# --- BƯỚC 2: TẠO FILE SQL ĐỂ IMPORT ---
cat <<EOF > import_script.sql
-- Bắt đầu transaction
BEGIN;

-- 1. Xử lý bảng RESOURCE
-- Tạo bảng tạm có cấu trúc y hệt bảng thật
CREATE TEMP TABLE tmp_resource AS SELECT * FROM public.resource WITH NO DATA;

-- Copy dữ liệu từ file vào bảng tạm
\copy tmp_resource (id, created_at, owner_id, title, type, updated_at, visibility) FROM 'resource_data.dat' WITH NULL AS '\N';

-- Thực hiện UPSERT (Chèn mới hoặc Cập nhật nếu trùng ID)
INSERT INTO public.resource (id, created_at, owner_id, title, type, updated_at, visibility)
SELECT id, created_at, owner_id, title, type, updated_at, visibility FROM tmp_resource
ON CONFLICT (id) DO UPDATE SET
    created_at = EXCLUDED.created_at,
    owner_id = EXCLUDED.owner_id,
    title = EXCLUDED.title,
    type = EXCLUDED.type,
    updated_at = EXCLUDED.updated_at,
    visibility = EXCLUDED.visibility;

-- Dọn dẹp bảng tạm
DROP TABLE tmp_resource;


-- 2. Xử lý bảng DOCUMENT
-- Tạo bảng tạm
CREATE TEMP TABLE tmp_document AS SELECT * FROM public.document WITH NO DATA;

-- Copy dữ liệu (Lưu ý: đảm bảo extension vector đã bật trên DB online)
\copy tmp_document (asset_id, description, document_type, download_count, is_downloadable, summary, id, course, university, embedding, content, keywords, preview_image_url) FROM 'document_data.dat' WITH NULL AS '\N';

-- Thực hiện UPSERT
INSERT INTO public.document (asset_id, description, document_type, download_count, is_downloadable, summary, id, course, university, embedding, content, keywords, preview_image_url)
SELECT asset_id, description, document_type, download_count, is_downloadable, summary, id, course, university, embedding, content, keywords, preview_image_url FROM tmp_document
ON CONFLICT (id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    description = EXCLUDED.description,
    document_type = EXCLUDED.document_type,
    download_count = EXCLUDED.download_count,
    is_downloadable = EXCLUDED.is_downloadable,
    summary = EXCLUDED.summary,
    course = EXCLUDED.course,
    university = EXCLUDED.university,
    embedding = EXCLUDED.embedding,
    content = EXCLUDED.content,
    keywords = EXCLUDED.keywords,
    preview_image_url = EXCLUDED.preview_image_url;

-- Dọn dẹp
DROP TABLE tmp_document;

COMMIT;
EOF

echo "Dang day du lieu len Online DB..."

# --- BƯỚC 3: CHẠY LỆNH PSQL ---
# Yêu cầu nhập mật khẩu nếu không dùng .pgpass
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f import_script.sql

# Xóa file tạm
rm resource_data.dat document_data.dat import_script.sql

echo "Hoan tat cap nhat!"