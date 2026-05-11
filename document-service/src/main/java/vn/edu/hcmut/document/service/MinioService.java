package vn.edu.hcmut.document.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.configuration.MinioProperties;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;

/**
 * File storage operations.
 *
 * The bucket is lazily created on any operation that writes to or lists from MinIO.
 * Read operations assume the bucket already exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioService {
    MinioClient minioClient;
    MinioProperties minioProperties;

    /**
     * Opens a streaming InputStream for the given object.
     * @param assetId the object key in the configured bucket
     * @return an open InputStream over the object's bytes
     * @throws AppException if the object does not exist or MinIO is unreachable
     */
    public InputStream getFileInputStream(String assetId) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO get object failed for asset {}: {}", assetId, e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Returns metadata (size, content type, last modified, etc.) for an object without downloading its content.
     *
     *
     * @param assetId the object key in the configured bucket
     * @return a StatObjectResponse containing the object's metadata
     * @throws AppException if the object does not exist or MinIO is unreachable
     */
    public StatObjectResponse getFileMetadata(String assetId) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO statObject failed for asset {}: {}", assetId, e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Lists all object keys in the configured bucket recursively.
     * Creates the bucket if it does not yet exist.
     * @return a list of all object keys (file names) in the bucket
     * @throws AppException on any MinIO or network error
     */
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
            log.error("MinIO listObjects failed: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Permanently removes an object from the bucket.
     * @param assetId the object key to delete
     * @throws AppException on any MinIO or network error
     */
    public void deleteFile(String assetId) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO remove object failed for asset {}: {}", assetId, e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Generates a time-limited pre-signed URL that allows the client to upload
     * a file directly to MinIO without routing the binary data through this service.
     *
     * Creates the bucket if it does not yet exist.
     *
     * @param assetId        the object key the client should upload to
     * @param expiryMinutes  how many minutes the URL is valid for
     * @return a pre-signed PUT URL
     * @throws AppException on any MinIO or network error
     */
    public String getPresignedUrl(String assetId, int expiryMinutes) {
        try {
            createBucketIfNotExists();
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(minioProperties.getBucketName())
                    .object(assetId)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());

            // Replace internal endpoint with external endpoint if configured
            String internalEndpoint = minioProperties.getEndpoint();
            String externalEndpoint = minioProperties.getExternalEndpoint();
            if (externalEndpoint != null && !externalEndpoint.isEmpty() && !externalEndpoint.equals(internalEndpoint)) {
                url = url.replace(internalEndpoint, externalEndpoint);
            }
            return url;
        } catch (Exception e) {
            log.error("MinIO presigned URL generation failed for asset {}: {}", assetId, e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Derives a unique object key from an original file name by prepending a UUID.
     *
     * Example: "report.pdf" → "550e8400-e29b-41d4-a716-446655440000.pdf"
     *
     * @param originalFileName the user-provided file name (may be null)
     * @return a globally unique asset key safe to use as a MinIO object name
     */
    public String generateUniqueAssetName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Uploads a file to MinIO. Creates the bucket if it does not yet exist.
     * @param assetId      the object key to store the file under
     * @param inputStream  the raw bytes to upload (caller retains ownership; stream is NOT closed here)
     * @param size         the exact byte count of the stream (required by the MinIO SDK)
     * @param contentType  MIME type to associate with the stored object (e.g., "application/pdf")
     * @throws AppException on any MinIO or network error
     */
    public void uploadFile(String assetId, InputStream inputStream, long size, String contentType) {
        try {
            createBucketIfNotExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(assetId)
                            .stream(inputStream, size, -1)  // -1 = no multipart threshold (single-part upload)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            log.error("MinIO put object failed for asset {}: {}", assetId, e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }

    /**
     * Ensures the configured bucket exists, creating it if necessary.
     * @throws AppException if the bucket existence check or creation fails
     */
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
            log.error("MinIO bucket creation/check failed: {}", e.getMessage());
            throw new AppException(ErrorCode.MINIO_ERROR);
        }
    }
}
