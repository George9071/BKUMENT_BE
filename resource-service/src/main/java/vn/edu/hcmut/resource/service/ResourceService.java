package vn.edu.hcmut.resource.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
        Map<String, String> formData = minioService.getPresignedPostFormData(assetId);

        return PresignResponse.builder()
                .assetId(assetId)
                .url(formData.get("url"))
                .formData(formData)
                .build();
    }
}
