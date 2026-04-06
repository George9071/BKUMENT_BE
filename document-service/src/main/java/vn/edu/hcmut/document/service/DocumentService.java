package vn.edu.hcmut.document.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
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
import org.springframework.beans.factory.annotation.Value;
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
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.configuration.GatewayProperties;
import vn.edu.hcmut.document.dto.request.DocumentMetadataRequest;
import vn.edu.hcmut.document.dto.response.*;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.entity.DocumentDownload;
import vn.edu.hcmut.document.entity.Resource;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;
import vn.edu.hcmut.document.repository.DocumentDownloadRepository;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.ResourceRepository;
import vn.edu.hcmut.document.repository.httpclient.AiClient;
import vn.edu.hcmut.document.repository.httpclient.ProfileClient;
import vn.edu.hcmut.document.repository.httpclient.SocialClient;
import vn.edu.hcmut.document.utils.StreamMultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentService {
    DocumentRepository documentRepository;
    DocumentDownloadRepository documentDownloadRepository;
    ResourceRepository resourceRepository;
    GatewayProperties gatewayProperties;

    MinioService minioService;
    GraphSyncService graphSyncService;
    ProfileClient profileClient;
    SocialClient socialClient;
    AiClient aiClient;
    DocumentAsyncService documentAsyncService;

    @NonFinal
    @Value("${app.ranking.weight-rating:0.7}")
    double weightRating;

    @NonFinal
    @Value("${app.ranking.weight-download:0.3}")
    double weightDownload;

    public DocAnalyzeResponse processAndCreateDocument(String assetId, String originalFileName, String ownerId) {
        log.info("[ASSET][{}] Bắt đầu xử lý với AI Service", assetId);

        StatObjectResponse stat = minioService.getFileMetadata(assetId);
        long fileSize = stat.size();
        String finalFileName =
                originalFileName.toLowerCase().endsWith(".pdf") ? originalFileName : originalFileName + ".pdf";
        ProcessResult processResult = null;

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
        document.setViews(0L);
        document.setDocumentType("application/pdf");

        document = documentRepository.save(document);
        documentAsyncService.runBackgroundAiProcess(assetId, finalFileName, fileSize, document.getId());

        // Points hook: +10 for upload
        try {
            profileClient.updatePoints(ownerId, 10L);
        } catch (Exception e) {
            log.error("Failed to update points for document upload: {}", document.getId(), e);
        }

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

    public Page<Document> getDocumentsByCourseId(String courseId, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());
        Page<Document> result = documentRepository.findByCourseId(courseId, sortedByCreatedAt);
        if (!result.isEmpty()) {
            documentRepository.incrementViews(
                    result.getContent().stream().map(Document::getId).toList());
        }
        return result;
    }

    public Page<Document> getDocumentsByOwnerId(String ownerId, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());
        Page<Document> result = documentRepository.findByOwnerId(ownerId, sortedByCreatedAt);
        if (!result.isEmpty()) {
            documentRepository.incrementViews(
                    result.getContent().stream().map(Document::getId).toList());
        }
        return result;
    }

    public Page<Document> getTopRankedDocuments(Pageable pageable) {
        // Step 1: Fetch a batch of candidate documents (e.g., top 1000 by download or overall)
        // For simplicity and to ensure accuracy, we'll fetch a larger set than just one page
        // But in a real-world scenario, we might want a specialized query or caching
        List<Document> documents = documentRepository.findAll();

        if (documents.isEmpty()) {
            return Page.empty(pageable);
        }

        RankingStatsResponse rankingStats = null;
        try {
            rankingStats = socialClient.getRankingStats();
        } catch (Exception e) {
            log.error("Failed to fetch ranking stats from social service", e);
        }

        final Map<String, ResourceRatingStatsResponse> statsMap =
                (rankingStats != null && rankingStats.getStats() != null)
                        ? rankingStats.getStats().stream()
                                .collect(Collectors.toMap(ResourceRatingStatsResponse::getResourceId, s -> s))
                        : new HashMap<>();

        // Step 3: Calculate score for each document
        List<DocumentScore> scoredDocs = documents.stream()
                .map(doc -> {
                    ResourceRatingStatsResponse stats = statsMap.get(doc.getId());
                    double R = (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;

                    // Final Score = w1 * R + w2 * log10(1 + Downloads)
                    double downloads = doc.getDownloadCount() != null ? doc.getDownloadCount() : 0.0;
                    double finalScore = (weightRating * R) + (weightDownload * Math.log10(1 + downloads));

                    return new DocumentScore(doc, finalScore);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());

        // Step 4: Paginate
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), scoredDocs.size());

        if (start > scoredDocs.size()) {
            return new PageImpl<>(List.of(), pageable, scoredDocs.size());
        }

        List<Document> pagedDocs =
                scoredDocs.subList(start, end).stream().map(ds -> ds.document).collect(Collectors.toList());

        if (!pagedDocs.isEmpty()) {
            documentRepository.incrementViews(
                    pagedDocs.stream().map(Document::getId).toList());
        }

        return new PageImpl<>(pagedDocs, pageable, scoredDocs.size());
    }

    private static class DocumentScore {
        Document document;
        double score;

        public DocumentScore(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }

    // TODO: move to search service
    public Page<Document> search(String keyword, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());

        Page<Document> result;
        if (keyword == null || keyword.isBlank()) {
            result = documentRepository.findAll(sortedByCreatedAt);
        } else if (isUUID(keyword)) {
            Optional<Document> optionalDoc = documentRepository.findById(keyword);
            result = optionalDoc
                    .map(doc -> new PageImpl<>(List.of(doc), sortedByCreatedAt, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), sortedByCreatedAt, 0));
        } else {
            result = documentRepository.findByTitleContainingIgnoreCase(keyword, sortedByCreatedAt);
        }

        if (!result.isEmpty()) {
            documentRepository.incrementViews(
                    result.getContent().stream().map(Document::getId).toList());
        }
        return result;
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

        document.setUniversityId(request.getUniversityId());
        document.setCourseId(request.getCourseId());
        if (request.getTopicId() != null) {
            document.setTopicId(request.getTopicId());
        }

        boolean isNew = (document.getId() == null);
        document = documentRepository.save(document);

        // Points hook: +10 for new document
        if (isNew) {
            try {
                profileClient.updatePoints(ownerId, 10L);
            } catch (Exception e) {
                log.error("Failed to update points for new document creation: {}", document.getId(), e);
            }
        }

        return document;
    }

    @Transactional
    public Document updateDocument(DocumentMetadataRequest request, String docId) {
        Resource resource =
                resourceRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        resource.setTitle(request.getTitle());
        resource.setVisibility(request.getVisibility());
        resourceRepository.save(resource);

        document.setDescription(request.getDescription());
        document.setDocumentType(request.getDocumentType());
        document.setUniversity(request.getUniversity());
        document.setCourse(request.getCourse());
        document.setSummary(request.getSummary());
        document.setDownloadable(Boolean.TRUE.equals(request.getDownloadable()));

        document.setUniversityId(request.getUniversityId());
        document.setCourseId(request.getCourseId());
        if (request.getTopicId() != null) {
            document.setTopicId(request.getTopicId());
        }

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

        documentRepository.incrementViews(List.of(docId));

        String downloadUrl =
                gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/document/asset/" + docId;

        String viewUrl =
                gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/resource/download/asset/" + docId;

        ProfileResponse profile = profileClient.findUserProfileById(document.getOwnerId());
        DocumentMetadataResponse.Author authorDto = DocumentMetadataResponse.Author.builder()
                .id(profile.getId())
                .name(profile.getFullName())
                .avatarUrl(profile.getAvatarUrl())
                .build();

        return DocumentMetadataResponse.builder()
                .id(document.getId())
                .title(resource.getTitle())
                .author(authorDto)
                .documentType(document.getDocumentType())
                .university(document.getUniversity())
                .course(document.getCourse())
                .universityId(document.getUniversityId())
                .courseId(document.getCourseId())
                .viewUrl(viewUrl)
                .topicId(document.getTopicId())
                .downloadCount(document.getDownloadCount())
                .downloadUrl(downloadUrl)
                .createdAt(resource.getCreatedAt())
                .description(document.getDescription())
                .summary(document.getSummary())
                .downloadable(document.isDownloadable())
                .previewImageUrl(document.getPreviewImageUrl())
                .views(document.getViews())
                .build();
    }

    @Transactional
    public ResourceDownloadResponse downloadDocument(String docId, String userId) {
        Document document =
                documentRepository.findById(docId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        // Increment download counter
        document.setDownloadCount(document.getDownloadCount() + 1);
        documentRepository.save(document);

        // Points hook: +2 for unique download
        boolean isFirstDownload = !documentDownloadRepository.existsByDocumentIdAndProfileId(docId, userId);
        if (isFirstDownload && !userId.equals(document.getOwnerId())) {
            try {
                profileClient.updatePoints(document.getOwnerId(), 2L);
            } catch (Exception e) {
                log.error("Failed to update points for unique download: {}", docId, e);
            }
        }

        // Save download history
        DocumentDownload documentDownload = new DocumentDownload();
        documentDownload.setDocumentId(docId);
        documentDownload.setProfileId(userId);
        documentDownloadRepository.save(documentDownload);

        // Notify graph sync service
        if (document.getTopicId() != null) {
            graphSyncService.handleDownloadEvent(userId, docId, document.getTopicId());
        }

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

    public String getOwnerId(String docId) {
        return documentRepository
                .findById(docId)
                .map(Document::getOwnerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
    }

    public boolean existsById(String docId) {
        return documentRepository.existsById(docId);
    }

    public PresignResponse generatePresignedUrl(String fileName) {
        String assetId = minioService.generateUniqueAssetName(fileName);
        String url = minioService.getPresignedUrl(assetId, 10);
        return new PresignResponse(assetId, url);
    }

    public Page<RelatedDocumentsResponse> getRecommendedDocuments(String userId, Pageable pageable) {
        List<String> allDocIds = graphSyncService.getCollaborativeRecommendations(userId);

        if (allDocIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Document> realDocuments = documentRepository.findAllById(allDocIds);
        Map<String, Document> docMap = realDocuments.stream().collect(Collectors.toMap(Document::getId, d -> d));

        List<String> validIds = allDocIds.stream().filter(docMap::containsKey).toList();

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int start = Math.min(page * size, validIds.size());
        int end = Math.min((page + 1) * size, validIds.size());

        List<String> pagedIds = validIds.subList(start, end);

        if (pagedIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, validIds.size());
        }

        List<String> ownerIds = pagedIds.stream()
                .map(id -> docMap.get(id).getOwnerId())
                .distinct()
                .toList();

        Map<String, ProfileResponse> profileMap = new HashMap<>();
        for (String ownerId : ownerIds) {
            ProfileResponse res = profileClient.findUserProfileById(ownerId);
            if (res != null) {
                profileMap.put(ownerId, res);
            }
        }

        List<RelatedDocumentsResponse> dtos = pagedIds.stream()
                .map(id -> {
                    Document doc = docMap.get(id);
                    ProfileResponse profile = profileMap.get(doc.getOwnerId());
                    return toRelatedDocument(doc, profile);
                })
                .toList();

        if (!dtos.isEmpty()) {
            documentRepository.incrementViews(
                    dtos.stream().map(RelatedDocumentsResponse::getId).toList());
        }

        return new PageImpl<>(dtos, pageable, validIds.size());
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

        List<String> ownerIds = docIds.stream()
                .map(docMap::get)
                .filter(doc -> doc != null)
                .map(Document::getOwnerId)
                .distinct()
                .toList();

        Map<String, ProfileResponse> profileMap = new HashMap<>();
        for (String ownerId : ownerIds) {
            ProfileResponse res = profileClient.findUserProfileById(ownerId);
            if (res != null) {
                profileMap.put(ownerId, res);
            }
        }

        List<RelatedDocumentsResponse> dtos = docIds.stream()
                .map(docMap::get)
                .filter(doc -> doc != null)
                .map(doc -> {
                    ProfileResponse profile = profileMap.get(doc.getOwnerId());
                    return toRelatedDocument(doc, profile);
                })
                .toList();

        if (!dtos.isEmpty()) {
            documentRepository.incrementViews(
                    dtos.stream().map(RelatedDocumentsResponse::getId).toList());
        }

        return new PageImpl<>(dtos, pageable, docIdsPage.getTotalElements());
    }

    private RelatedDocumentsResponse toRelatedDocument(Document document, ProfileResponse profile) {
        String downloadUrl = gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix()
                + "/resource/download/asset/" + document.getId();

        RelatedDocumentsResponse.Author authorDto = null;
        if (profile != null) {
            authorDto = RelatedDocumentsResponse.Author.builder()
                    .id(profile.getId())
                    .name(profile.getFullName())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        }

        return RelatedDocumentsResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .author(authorDto)
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
                .views(document.getViews())
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
