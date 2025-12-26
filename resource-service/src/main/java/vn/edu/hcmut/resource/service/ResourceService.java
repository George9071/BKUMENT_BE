package vn.edu.hcmut.resource.service;

import java.io.InputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.minio.StatObjectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.resource.configuration.GatewayProperties;
import vn.edu.hcmut.resource.dto.request.ResourceMetadataRequest;
import vn.edu.hcmut.resource.dto.response.DocumentResponse;
import vn.edu.hcmut.resource.dto.response.FileInfoResponse;
import vn.edu.hcmut.resource.dto.response.PresignResponse;
import vn.edu.hcmut.resource.dto.response.ResourceDownloadResponse;
import vn.edu.hcmut.resource.entity.Document;
import vn.edu.hcmut.resource.entity.Post;
import vn.edu.hcmut.resource.entity.Resource;
import vn.edu.hcmut.resource.exception.AppException;
import vn.edu.hcmut.resource.exception.ErrorCode;
import vn.edu.hcmut.resource.repository.DocumentRepository;
import vn.edu.hcmut.resource.repository.PostRepository;
import vn.edu.hcmut.resource.repository.ResourceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceService {
    ResourceRepository resourceRepository;
    PostRepository postRepository;
    DocumentRepository documentRepository;
    GatewayProperties gatewayProperties;
    MinioService minioService;

    @Transactional
    public Resource createResource(ResourceMetadataRequest request, String ownerId) {
        if ("POST".equalsIgnoreCase(request.getResourceType())) {
            Post post = new Post();
            mapCommonFields(post, request);
            post.setContent(request.getContent());
            post.setAssetId(request.getAssetId());
            post.setOwnerId(ownerId);
            return postRepository.save(post);
        }

        if ("DOCUMENT".equalsIgnoreCase(request.getResourceType())) {
            Document document = new Document();
            mapCommonFields(document, request);
            document.setOwnerId(ownerId);
            document.setDescription(request.getDescription());
            document.setTitle(request.getTitle());
            document.setDocumentType(request.getDocumentType());
            document.setUniversity(request.getUniversity());
            document.setCourse(request.getCourse());
            document.setSummary(request.getSummary());
            document.setDownloadable(Boolean.TRUE.equals(request.getDownloadable()));
            document.setAssetId(request.getAssetId());
            return documentRepository.save(document);
        }

        throw new AppException(ErrorCode.INVALID_RESOURCE_TYPE);
    }

    @Transactional
    public Resource updateResource(String resourceId, ResourceMetadataRequest request) {
        Resource existingResource = findResourceById(resourceId);
        // Additional update logic can be added here
        return resourceRepository.save(existingResource);
    }

    public ResourceDownloadResponse downloadResource(String resourceId) {
        Resource resource = findResourceById(resourceId);
        String assetId = getAssetIdFromResource(resource);

        if (resource instanceof Document) {
            Document doc = (Document) resource;
            doc.setDownloadCount(doc.getDownloadCount() + 1);
            documentRepository.save(doc);
        }

        InputStream stream = minioService.getFileInputStream(assetId);
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return ResourceDownloadResponse.builder()
                .fileName(resource.getTitle())
                .contentType(stat.contentType())
                .fileSize(stat.size())
                .inputStream(stream)
                .build();
    }

    public FileInfoResponse getResourceFileInfo(String resourceId) {
        Resource resource = findResourceById(resourceId);
        String assetId = getAssetIdFromResource(resource);
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return FileInfoResponse.builder()
                .fileName(resource.getTitle())
                .size(stat.size())
                .contentType(stat.contentType())
                .lastModified(stat.lastModified())
                .build();
    }

    public DocumentResponse getDocumentInfo(String resourceId) {
        Resource resource = findResourceById(resourceId);
        Document document = documentRepository
                .findById(resourceId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        DocumentResponse response = new DocumentResponse();
        response.setCourse(document.getCourse());
        response.setTitle(resource.getTitle());
        response.setDescription(document.getDescription());
        response.setDocumentType(document.getDocumentType());
        response.setDownloadCount(document.getDownloadCount());
        response.setUniversity(document.getUniversity());
        response.setDownloadable(document.isDownloadable());
        response.setDownloadUrl(
                gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/resource/download/" + resourceId);
        response.setCreatedAt(resource.getCreatedAt());

        return response;
    }

    @Transactional
    public void deleteResource(String resourceId) {
        Resource resource = findResourceById(resourceId);
        String assetId = getAssetIdFromResource(resource);
        minioService.deleteFile(assetId);
        resourceRepository.delete(resource);
    }

    public PresignResponse generatePresignedUrl(String fileNameOrResourceId) {
        String assetId;
        try {
            Resource resource = findResourceById(fileNameOrResourceId);
            assetId = getAssetIdFromResource(resource);
        } catch (AppException e) {
            assetId = minioService.generateUniqueAssetName(fileNameOrResourceId);
        }

        String url = minioService.getPresignedUrl(assetId, 10);
        return new PresignResponse(assetId, url);
    }

    private Resource findResourceById(String id) {
        return resourceRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
    }

    private String getAssetIdFromResource(Resource resource) {
        if (resource instanceof Document) {
            return ((Document) resource).getAssetId();
        }
        if (resource instanceof Post) {
            return ((Post) resource).getAssetId();
        }
        return null;
    }

    private void mapCommonFields(Resource resource, ResourceMetadataRequest request) {
        resource.setTitle(request.getTitle());
        resource.setVisibility(request.getVisibility());
        resource.setType(request.getResourceType().toUpperCase());
    }
}
