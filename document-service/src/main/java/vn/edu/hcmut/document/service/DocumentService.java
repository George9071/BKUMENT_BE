package vn.edu.hcmut.document.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    DocumentAsyncService documentAsyncService;

    public DocAnalyzeResponse processAndCreateDocument(String assetId, String originalFileName, String ownerId) {
        log.info("[ASSET][{}] Bắt đầu xử lý với AI Service", assetId);

        StatObjectResponse stat = minioService.getFileMetadata(assetId);
        long fileSize = stat.size();
        String finalFileName =
                originalFileName.toLowerCase().endsWith(".pdf") ? originalFileName : originalFileName + ".pdf";

        try (InputStream inputStream = minioService.getFileInputStream(assetId)) {
            byte[] fileBytes = inputStream.readAllBytes();

            try (PDDocument pdDocument = PDDocument.load(fileBytes)) {
                PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", os);
                InputStream is = new ByteArrayInputStream(os.toByteArray());
                String previewAssetId = UUID.randomUUID().toString() + ".png";
                minioService.uploadFile(previewAssetId, is, os.size(), "image/png");

                String previewUrl = gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix()
                        + "/resource/download/asset/" + previewAssetId;

                ByteArrayInputStream multipartInputStream = new ByteArrayInputStream(fileBytes);
                MultipartFile multipartFile = new StreamMultipartFile(
                        "file", finalFileName, "application/pdf", fileSize, multipartInputStream);
                FastDocumentProcessResponse fastResult = aiClient.processDocumentFast(multipartFile);

                processResult = new ProcessResult(fastResult, previewUrl);
            }
        } catch (Exception e) {
            log.error("Lỗi khi đọc file hoặc gọi AI nhanh", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        if (processResult == null || processResult.fastResult == null)
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);

        Document document = new Document();
        document.setAssetId(assetId);
        document.setTitle(originalFileName);
        document.setOwnerId(ownerId);
        document.setType("DOCUMENT");
        document.setVisibility("PRIVATE");
        document.setDownloadable(false);
        document.setPreviewImageUrl(processResult.previewUrl);
        document.setKeywords(processResult.fastResult.getKeywords());
        document.setSummary(processResult.fastResult.getSummary());
        document.setDownloadCount(0);
        document.setDocumentType("application/pdf");

        document = documentRepository.save(document);
        documentAsyncService.runBackgroundAiProcess(assetId, finalFileName, fileSize, document.getId());

        return DocAnalyzeResponse.builder()
                .docId(document.getId())
                .keywords(processResult.fastResult.getKeywords())
                .summary(processResult.fastResult.getSummary())
                .build();
    }

    private static class ProcessResult {
        FastDocumentProcessResponse fastResult;
        String previewUrl;

        public ProcessResult(FastDocumentProcessResponse fastResult, String previewUrl) {
            this.fastResult = fastResult;
            this.previewUrl = previewUrl;
        }
    }

    // TODO: move to search service
    public Page<Document> search(String keyword, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());

        if (keyword == null || keyword.isBlank()) {
            return documentRepository.findAll(sortedByCreatedAt);
        }

        if (isUUID(keyword)) {
            Optional<Document> optionalDoc = documentRepository.findById(keyword);
            if (optionalDoc.isPresent()) {
                return new PageImpl<>(List.of(optionalDoc.get()), sortedByCreatedAt, 1);
            }
        }

        return documentRepository.findByTitleContainingIgnoreCase(keyword, sortedByCreatedAt);
    }

    @Transactional
    public Document createOrUpdateDocument(DocumentMetadataRequest request, String ownerId) {
        Document document;

        if (request.getId() != null) {
            document = documentRepository
                    .findById(request.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        } else {
            document = new Document();
            document.setType("DOCUMENT");
            document.setOwnerId(ownerId);
            document.setEmbedding(new float[768]); // TODO: remove hard code
        }

        document.setTitle(request.getTitle());
        document.setVisibility(request.getVisibility());
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
                .previewImageUrl(document.getPreviewImageUrl())
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

    public Page<RelatedDocumentsResponse> getRelatedDocuments(String docId, Pageable pageable) {
        Document sourceDoc =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (sourceDoc.getEmbedding() == null) {
            return Page.empty(pageable);
        }

        String vectorStr = Arrays.toString(sourceDoc.getEmbedding());

        StringBuilder queryBuilder = new StringBuilder();
        if (sourceDoc.getTitle() != null) {
            queryBuilder.append(sourceDoc.getTitle()).append(" ");
        }
        if (sourceDoc.getKeywords() != null) {
            queryBuilder.append(String.join(" ", sourceDoc.getKeywords()));
        }
        String queryString = queryBuilder.toString().trim();
        if (queryString.isEmpty()) queryString = " ";

        Page<String> docIdsPage = documentRepository.findRelatedDocumentIds(vectorStr, queryString, docId, pageable);

        if (docIdsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<String> docIds = docIdsPage.getContent();
        Map<String, Document> docMap = documentRepository.findAllById(docIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<RelatedDocumentsResponse> dtos = docIds.stream()
                .map(docMap::get)
                .filter(doc -> doc != null)
                .map(this::toRelatedDocument)
                .toList();

        return new PageImpl<>(dtos, pageable, docIdsPage.getTotalElements());
    }

    private RelatedDocumentsResponse toRelatedDocument(Document document) {
        String downloadUrl =
                gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/download/" + document.getId();
        return RelatedDocumentsResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .authorId(document.getOwnerId())
                .documentType(document.getDocumentType())
                .university(document.getUniversity())
                .course(document.getCourse())
                .downloadCount(document.getDownloadCount())
                .downloadUrl(downloadUrl)
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .summary(document.getSummary())
                .downloadable(document.isDownloadable())
                .keywords(document.getKeywords())
                .previewImageUrl(document.getPreviewImageUrl())
                .build();
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
