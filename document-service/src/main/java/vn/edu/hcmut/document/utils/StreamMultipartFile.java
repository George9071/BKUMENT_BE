package vn.edu.hcmut.document.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.lang.NonNull;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

public class StreamMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final InputStream inputStream;

    public StreamMultipartFile(
            String name, String originalFilename, String contentType, long size, InputStream inputStream) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.inputStream = inputStream;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    @NonNull
    public byte[] getBytes() throws IOException {
        // Cảnh báo: Không nên gọi hàm này với file lớn
        return FileCopyUtils.copyToByteArray(inputStream);
    }

    @Override
    @NonNull
    public InputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        FileCopyUtils.copy(inputStream, java.nio.file.Files.newOutputStream(dest.toPath()));
    }
}
