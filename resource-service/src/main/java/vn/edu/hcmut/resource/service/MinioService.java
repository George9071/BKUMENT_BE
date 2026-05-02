package vn.edu.hcmut.resource.service;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.minio.*;
import io.minio.messages.Item;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.resource.configuration.MinioProperties;
import vn.edu.hcmut.resource.exception.AppException;
import vn.edu.hcmut.resource.exception.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioService {
    MinioClient minioClient;
    MinioProperties minioProperties;

    public InputStream getFileInputStream(String assetId) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO Get Stream Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    public StatObjectResponse getFileMetadata(String assetId) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO Get Metadata Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    public List<String> listAllFiles() {
        try {
            createBucketIfNotExists();
            List<String> fileNames = new ArrayList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .recursive(true)
                    .build());

            for (Result<Item> result : results) {
                fileNames.add(result.get().objectName());
            }
            return fileNames;
        } catch (Exception e) {
            log.error("MinIO List Files Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    public void deleteFile(String assetId) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO Delete Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    public Map<String, String> getPresignedPostFormData(String assetId) {
        try {
            createBucketIfNotExists();
            PostPolicy policy = new PostPolicy(
                    minioProperties.getBucketName(), ZonedDateTime.now().plusDays(1));
            policy.addEqualsCondition("key", assetId);
            policy.addContentLengthRangeCondition(0, minioProperties.getMaxFileSize());

            Map<String, String> formData = minioClient.getPresignedPostFormData(policy);

            // Replace internal endpoint with external endpoint if configured
            String internalEndpoint = minioProperties.getEndpoint();
            String externalEndpoint = minioProperties.getExternalEndpoint();
            if (externalEndpoint != null && !externalEndpoint.isEmpty() && !externalEndpoint.equals(internalEndpoint)) {
                String url = formData.get("url");
                if (url != null) {
                    formData.put("url", url.replace(internalEndpoint, externalEndpoint));
                }
            }

            return formData;
        } catch (Exception e) {
            log.error("MinIO Presign POST Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    public String generateUniqueAssetName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    private void createBucketIfNotExists() {
        try {
            String bucketName = minioProperties.getBucketName();
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO Bucket Creation Error: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }
}
