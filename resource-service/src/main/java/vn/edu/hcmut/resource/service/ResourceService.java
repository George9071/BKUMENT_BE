package vn.edu.hcmut.resource.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.stereotype.Service;

import io.minio.StatObjectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.resource.dto.response.FileInfoResponse;
import vn.edu.hcmut.resource.dto.response.PresignResponse;
import vn.edu.hcmut.resource.dto.response.ResourceDownloadResponse;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceService {
    MinioService minioService;

    public List<String> listAllFiles() {
        return minioService.listAllFiles();
    }

    public ResourceDownloadResponse downloadByAssetId(String assetId) {
        StatObjectResponse stat = minioService.getFileMetadata(assetId);
        InputStream stream = minioService.getFileInputStream(assetId);

        return ResourceDownloadResponse.builder()
                .fileName(assetId)
                .contentType(stat.contentType())
                .fileSize(stat.size())
                .inputStream(stream)
                .build();
    }

    public FileInfoResponse getFileInfo(String assetId) {
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return FileInfoResponse.builder()
                .fileName(assetId)
                .size(stat.size())
                .contentType(stat.contentType())
                .lastModified(stat.lastModified())
                .build();
    }

    public void deleteFile(String assetId) {
        minioService.deleteFile(assetId);
    }

    public PresignResponse generatePresignedUrl(String fileName) {
        String assetId = minioService.generateUniqueAssetName(fileName);

        // Đoán Content-Type từ đuôi file (Mặc định là octet-stream nếu không đoán được)
        String contentType = "application/octet-stream";
        try {
            String probedType = Files.probeContentType(Paths.get(fileName));
            if (probedType != null) {
                contentType = probedType;
            }
        } catch (Exception e) {
            log.warn("Could not probe content type for file: {}", fileName);
        }

        // Truyền contentType xuống MinioService
        String url = minioService.getPresignedUrl(assetId, contentType);

        return PresignResponse.builder().assetId(assetId).url(url).build();
    }
}
