package vn.edu.hcmut.document.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.minio.StatObjectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.configuration.GatewayProperties;
import vn.edu.hcmut.document.dto.request.DocumentMetadataRequest;
import vn.edu.hcmut.document.dto.response.*;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.entity.Resource;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.ResourceRepository;
import vn.edu.hcmut.document.repository.httpclient.AiClient;
import vn.edu.hcmut.document.utils.StreamMultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentService {
    DocumentRepository documentRepository;
    ResourceRepository resourceRepository;
    GatewayProperties gatewayProperties;

    MinioService minioService;
    AiClient aiClient;

    public List<String> getDocumentKeywords(String docId) {
        log.info("[DOC][{}] Start processing document with AI Service", docId);

        Document document = documentRepository.findById(docId).orElseThrow(() -> {
            log.warn("[DOC][{}] Document not found", docId);
            return new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        });

        String assetId = document.getAssetId();
        String fileName = document.getTitle();
        String finalFileName = fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";

        StatObjectResponse stat = minioService.getFileMetadata(assetId);
        long fileSize = stat.size();

        try (InputStream inputStream = minioService.getFileInputStream(assetId)) {

            MultipartFile multipartFile =
                    new StreamMultipartFile("file", finalFileName, "application/pdf", fileSize, inputStream);

            log.info("[DOC][{}] Sending request to AI Service (Streaming mode)", docId);

            DocumentProcessResponse result = aiClient.processDocument(multipartFile);

            if (result == null) {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }

            updateDocumentWithAiResult(document, result);

            return result.getKeywords();

        } catch (Exception e) {
            log.error("[DOC][{}] Error processing document", docId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Transactional
    protected void updateDocumentWithAiResult(Document document, DocumentProcessResponse result) {
        document.setKeywords(result.getKeywords());
        document.setSummary(result.getSummary());
        document.setContent(result.getContent());
        document.setVector(result.getVector());

        documentRepository.save(document);
        log.info("[DOC][{}] Document updated in database", document.getId());
    }

    public Page<Document> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return documentRepository.findAll(pageable);
        }

        // Search by ID if it's a UUID
        if (isUUID(keyword)) {
            Optional<Document> optionalDoc = documentRepository.findById(keyword);
            if (optionalDoc.isPresent()) {
                return new PageImpl<>(List.of(optionalDoc.get()), pageable, 1);
            }
        }

        // Search by title (case-insensitive)
        return documentRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }

    @Transactional
    public Document createDocument(DocumentMetadataRequest request, String ownerId) {
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setVisibility(request.getVisibility());
        document.setType("DOCUMENT");
        document.setOwnerId(ownerId);
        document.setDescription(request.getDescription());
        document.setDocumentType(request.getDocumentType());
        document.setUniversity(request.getUniversity());
        document.setCourse(request.getCourse());
        document.setSummary(request.getSummary());
        document.setDownloadable(Boolean.TRUE.equals(request.getDownloadable()));
        document.setAssetId(request.getAssetId());

        return documentRepository.save(document);
    }

    @Transactional
    public Document updateDocument(DocumentMetadataRequest request, String docId) {
        Resource resource =
                resourceRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        // Update Resource base fields
        resource.setTitle(request.getTitle());
        resource.setVisibility(request.getVisibility());
        resourceRepository.save(resource);

        // Update Document specific fields
        document.setDescription(request.getDescription());
        document.setDocumentType(request.getDocumentType());
        document.setUniversity(request.getUniversity());
        document.setCourse(request.getCourse());
        document.setSummary(request.getSummary());
        document.setDownloadable(Boolean.TRUE.equals(request.getDownloadable()));

        if (request.getAssetId() != null && !request.getAssetId().isBlank()) {
            document.setAssetId(request.getAssetId());
        }

        return documentRepository.save(document);
    }

    public DocumentMetadataResponse getDocumentInfo(String docId) {
        Resource resource =
                resourceRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        String downloadUrl = gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/download/" + docId;

        return DocumentMetadataResponse.builder()
                .id(document.getId())
                .title(resource.getTitle())
                .authorId(document.getOwnerId())
                .documentType(document.getDocumentType())
                .university(document.getUniversity())
                .course(document.getCourse())
                .downloadCount(document.getDownloadCount())
                .downloadUrl(downloadUrl)
                .createdAt(resource.getCreatedAt())
                .description(document.getDescription())
                .summary(document.getSummary())
                .downloadable(document.isDownloadable())
                .build();
    }

    @Transactional
    public ResourceDownloadResponse downloadDocument(String docId) {
        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        // Increment download counter
        document.setDownloadCount(document.getDownloadCount() + 1);
        documentRepository.save(document);

        // Get file from MinIO
        String assetId = document.getAssetId();
        InputStream stream = minioService.getFileInputStream(assetId);
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return ResourceDownloadResponse.builder()
                .fileName(document.getTitle())
                .contentType(stat.contentType())
                .fileSize(stat.size())
                .inputStream(stream)
                .build();
    }

    public FileInfoResponse getFileInfo(String docId) {
        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        String assetId = document.getAssetId();
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return FileInfoResponse.builder()
                .fileName(document.getTitle())
                .size(stat.size())
                .contentType(stat.contentType())
                .lastModified(stat.lastModified())
                .build();
    }

    @Transactional
    public void deleteDocument(String docId) {
        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        String assetId = document.getAssetId();
        if (assetId != null && !assetId.isBlank()) {
            minioService.deleteFile(assetId);
        }

        documentRepository.delete(document);
    }

    public PresignResponse generatePresignedUrl(String fileName) {
        String assetId = minioService.generateUniqueAssetName(fileName);
        String url = minioService.getPresignedUrl(assetId, 10);
        return new PresignResponse(assetId, url);
    }

    private boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
