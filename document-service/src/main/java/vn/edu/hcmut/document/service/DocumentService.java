package vn.edu.hcmut.document.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.minio.StatObjectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.configuration.GatewayProperties;
import vn.edu.hcmut.document.dto.RecommendationItem;
import vn.edu.hcmut.document.dto.request.DocumentMetadataRequest;
import vn.edu.hcmut.document.dto.response.*;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.event.UserDownloadedDocumentEvent;
import vn.edu.hcmut.document.event.UserViewedDocumentEvent;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;
import vn.edu.hcmut.document.repository.DocumentDownloadRepository;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.httpclient.AiClient;
import vn.edu.hcmut.document.repository.httpclient.LmsClient;
import vn.edu.hcmut.document.repository.httpclient.ProfileClient;
import vn.edu.hcmut.document.utils.StreamMultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentService {
    DocumentRepository documentRepository;
    DocumentDownloadRepository documentDownloadRepository;

    DocumentAsyncService documentAsyncService;
    DocumentDownloadService documentDownloadService;
    DocumentRecommendationService documentRecommendationService;

    ProfileClient profileClient;
    LmsClient lmsClient;
    AiClient aiClient;

    GraphSyncService graphSyncService;
    MinioService minioService;
    GatewayProperties gatewayProperties;
    KafkaTemplate<String, Object> kafkaTemplate;

    public Map<String, ResourceContentSnapshot> getMetadataBatch(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Collections.emptyMap();

        List<Document> docs = documentRepository.findAllById(documentIds);
        if (docs.isEmpty()) return Collections.emptyMap();

        Map<String, ResourceContentSnapshot> result = new LinkedHashMap<>(docs.size());
        for (Document d : docs) {
            result.put(d.getId(), ResourceContentSnapshot.builder()
                    .id(d.getId())
                    .title(d.getTitle())
                    .content(d.getSummary())
                    .coverImage(d.getPreviewImageUrl())
                    .ownerId(d.getOwnerId())
                    .createdAt(d.getCreatedAt())
                    .views(d.getViews())
                    .build());
        }
        return result;
    }

    /**
     * Processes a newly uploaded PDF document:
     * 1. Renders the first page as a preview PNG and stores it in MinIO.
     * 2. Calls the fast AI endpoint for keywords + summary extraction.
     * 3. Persists the document entity immediately.
     * 4. Kicks off a background deep-AI analysis (embedding + full content) asynchronously.
     * 5. Give points to the uploader (10 points).
     *
     * @param assetId          the MinIO object key of the uploaded PDF
     * @param originalFileName the original file name provided by the client
     * @param ownerId          the ID of the user who uploaded the document
     * @return a lightweight response containing docId, keywords, and summary
     */
    public DocAnalyzeResponse processAndCreateDocument(String assetId, String originalFileName, String ownerId) {
        log.info("[ASSET][{}] Starting AI processing pipeline", assetId);

        StatObjectResponse stat = minioService.getFileMetadata(assetId);
        long fileSize = stat.size();

        // Ensure the stored file name always ends with .pdf for downstream consumers.
        String finalFileName =
                originalFileName.toLowerCase().endsWith(".pdf") ? originalFileName : originalFileName + ".pdf";
        String previewUrl = null;

        try (InputStream inputStream = minioService.getFileInputStream(assetId)) {
            byte[] fileBytes = inputStream.readAllBytes();

            try (PDDocument pdDocument = PDDocument.load(fileBytes)) {
                // Render only the first page (index 0) at 300 DPI as the document preview thumbnail.
                PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", os);
                InputStream is = new ByteArrayInputStream(os.toByteArray());

                // Store preview image with a unique random asset name.
                String previewAssetId = UUID.randomUUID() + ".png";
                minioService.uploadFile(previewAssetId, is, os.size(), "image/png");

                // Build the publicly accessible preview URL through the API gateway.
                previewUrl = gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix()
                        + "/resource/download/asset/" + previewAssetId;
            }
        } catch (Exception e) {
            log.error("Error reading file or creating preview for asset {}", assetId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Persist the document.
        // Full content + vector embedding will be filled by the background async job.
        Document document = Document.builder()
                .assetId(assetId)
                .title(originalFileName)
                .ownerId(ownerId)
                .type("DOCUMENT")
                .documentType("application/pdf")
                .visibility("PRIVATE")
                .downloadable(false)
                .previewImageUrl(previewUrl)
                .keywords(Collections.emptyList())
                .summary("")
                .downloadCount(0)
                .views(0L)
                .deepAiStatus(vn.edu.hcmut.document.constant.AiAnalyzeStatus.PENDING)
                .build();

        document = documentRepository.save(document);

        // Trigger deep processing (full content extraction) in a background thread.
        documentAsyncService.runBackgroundAiProcess(assetId, finalFileName, fileSize, document.getId());

        // Award gamification points for uploading (+10)
        try {
            profileClient.updatePoints(ownerId, 10L);
        } catch (Exception e) {
            log.error("Failed to award upload points for document {}", document.getId(), e);
        }

        return DocAnalyzeResponse.builder()
                .docId(document.getId())
                .keywords(document.getKeywords())
                .summary(document.getSummary())
                .deepAiStatus(document.getDeepAiStatus())
                .build();
    }

    public DocAnalyzeResponse fastAnalyzeDocument(String docId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        // If Deep AI has already populated the summary, return it immediately to save API costs
        if (document.getSummary() != null && !document.getSummary().isBlank()) {
            log.info("[FAST-AI] Bypass API call, returning existing data for docId: {}", docId);
            return DocAnalyzeResponse.builder()
                    .docId(docId)
                    .keywords(document.getKeywords())
                    .summary(document.getSummary())
                    .deepAiStatus(document.getDeepAiStatus())
                    .build();
        }

        log.info("[FAST-AI] Calling Gemini for docId: {}", docId);
        
        try (InputStream inputStream = minioService.getFileInputStream(document.getAssetId())) {
            StatObjectResponse stat = minioService.getFileMetadata(document.getAssetId());
            long fileSize = stat.size();
            String finalFileName = document.getTitle().toLowerCase().endsWith(".pdf") ? 
                    document.getTitle() : document.getTitle() + ".pdf";

            MultipartFile multipartFile = new StreamMultipartFile(
                    "file", finalFileName, "application/pdf", fileSize, inputStream);

            FastDocumentProcessResponse fastResult = aiClient.processDocumentFast(multipartFile);

            if (fastResult != null) {
                // Use the custom JPQL query to update only if not overwritten by deep AI
                int updatedRows = documentRepository.updateFastAiResult(
                        docId, 
                        fastResult.getKeywords(), 
                        fastResult.getSummary()
                );

                if (updatedRows > 0) {
                    log.info("[FAST-AI] Successfully updated Fast AI results for docId: {}", docId);
                    return DocAnalyzeResponse.builder()
                            .docId(docId)
                            .keywords(fastResult.getKeywords())
                            .summary(fastResult.getSummary())
                            .deepAiStatus(document.getDeepAiStatus())
                            .build();
                } else {
                    log.info("[FAST-AI] Fast AI results discarded due to Deep AI already completed for docId: {}", docId);
                    // Fetch the latest document to return the deep AI results
                    Document latestDoc = documentRepository.findById(docId).orElse(document);
                    return DocAnalyzeResponse.builder()
                            .docId(docId)
                            .keywords(latestDoc.getKeywords())
                            .summary(latestDoc.getSummary())
                            .deepAiStatus(latestDoc.getDeepAiStatus())
                            .build();
                }
            } else {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
        } catch (Exception e) {
            log.error("[FAST-AI] Error calling fast AI for docId {}", docId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public Page<Document> getDocumentsByCourseId(String courseId, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());
        Page<Document> result = documentRepository.findByCourseId(courseId, sortedByCreatedAt);
//        if (!result.isEmpty()) {
//            documentRepository.incrementViews(result.getContent().stream().map(Document::getId).toList());
//        }
        return result;
    }

    /**
     * Returns a paginated list of documents owned by a given user, ordered by newest first.
     * Also increments the view counter for all returned documents in a single bulk update.
     */
    public Page<Document> getDocumentsByOwnerId(String ownerId, Pageable pageable) {
        Pageable sortedByCreatedAt = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending());
        Page<Document> result = documentRepository.findByOwnerId(ownerId, sortedByCreatedAt);
        if (!result.isEmpty()) {
            documentRepository.incrementViews(result.getContent().stream().map(Document::getId).toList());
        }
        return result;
    }

    public Page<Document> getTopRankedDocuments(Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        Page<Document> result = documentRepository.findRecentDocumentsByRankingScore(since, pageable);

        if (result.isEmpty()) {
            // Fallback: time window has no results — widen to all documents.
            log.info("[RANKING] No documents in {} day window; falling back to all documents", 90);
            result = documentRepository.findAllOrderByCreatedAtDesc(pageable);
        }

        if (!result.isEmpty()) {
            documentRepository.incrementViews(
                    result.getContent().stream().map(Document::getId).toList());
        }

        return result;
    }

    /**
     * TODO: move to search service
     * Basic keyword search over document titles.
     * - Empty/null keyword -> returns all documents (newest first).
     * - UUID-shaped keyword -> direct lookup by ID (exact match, O(1)).
     * - Otherwise → case-insensitive LIKE search on the title column.
     * OPTIMIZE: replace LIKE with full-text search for better relevance and performance.
     * (PostgreSQL tsvector / Elasticsearch)
     */
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

    /**
     * Upsert operation: creates a new document or updates an existing one based on whether the request contains an ID.
     * NOTE: When creating, the embedding vector is initialized to all-zeros (768 dims).
     * The real embedding is written later by the async AI process. The zero vector ensures
     * the pgvector column is never NULL.
     * TODO: Remove the hard-coded dimension constant (768) — read from config or constants class.
     *
     * @param request metadata from the client
     * @param ownerId  JWT-extracted user ID of the requester
     */
    @Transactional
    public Document createOrUpdateDocument(DocumentMetadataRequest request, String ownerId) {
        Document document;

        if (request.getId() != null) {
            document = documentRepository
                    .findById(request.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        } else {
            document = Document.builder()
                    .type("DOCUMENT")
                    .ownerId(ownerId)
                    .embedding(new float[768]) // TODO: remove hard code
                    .build();
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

        // Check before save so can award points only on creation, not on updates.
        boolean isNew = (document.getId() == null);
        document = documentRepository.save(document);

        if (isNew) {
            try {
                profileClient.updatePoints(ownerId, 10L);
            } catch (Exception e) {
                log.error("Failed to update points for new document creation: {}", document.getId(), e);
            }
        }

        return document;
    }

    /**
     * Updates an existing document's metadata.
     */
    @Transactional
    public Document updateDocument(DocumentMetadataRequest request, String docId) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        document.setTitle(request.getTitle());
        document.setVisibility(request.getVisibility());

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

    /**
     * Returns full metadata for a single document, including the author's profile info
     * Also constructs both a direct download URL and a view URL (served by the resource-service)
     */
    public DocumentMetadataResponse getDocumentInfo(String docId, String userId) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        documentRepository.incrementViews(List.of(docId));

        if (userId != null) {
            UserViewedDocumentEvent event = UserViewedDocumentEvent.builder()
                    .profileId(userId)
                    .documentId(docId)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            kafkaTemplate.send("document-view-events", event);
        }

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
                .title(document.getTitle())
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
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .summary(document.getSummary())
                .downloadable(document.getDownloadable())
                .previewImageUrl(document.getPreviewImageUrl())
                .views(document.getViews())
                .deepAiStatus(document.getDeepAiStatus())
                .build();
    }

    /**
     * Handles a document download request:
     * 1. Increments the download counter on the document.
     * 2. Awards +2 points to the document owner if this is the first time this user downloads it
     *    (unique download), but NOT if the user is downloading their own document.
     * 3. Persists a DocumentDownload record for analytics / history.
     * 4. Fires an async Neo4j graph event (DOWNLOADED relationship + HAS_TOPIC edge).
     * 5. Streams the file bytes from MinIO.
     */
    public ResourceDownloadResponse downloadDocument(String docId, String userId) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        documentDownloadService.incrementDownloadCount(docId);
        boolean isFirstDownload = documentDownloadService.tryRecordDownload(docId, userId);

        // Award +2 points to the owner only on the user's first download of this document.
        // Self-downloads are excluded to prevent farming.
        if (isFirstDownload && !userId.equals(document.getOwnerId())) {
            try {
                profileClient.updatePoints(document.getOwnerId(), 2L);
            } catch (Exception e) {
                log.error("Failed to award points for unique download on document {}", docId, e);
            }
        }

        if (document.getTopicId() != null && userId != null) {
            UserDownloadedDocumentEvent event = UserDownloadedDocumentEvent.builder()
                    .profileId(userId)
                    .documentId(docId)
                    .topicId(document.getTopicId())
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            kafkaTemplate.send("document-download-events", event);
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

    /**
     * Returns file metadata (name, size, content type, last modified) without downloading the file.
     * Useful for the frontend to display file info or to determine whether to display a download button.
     */
    public FileInfoResponse getFileInfo(String docId) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        String assetId = document.getAssetId();
        StatObjectResponse stat = minioService.getFileMetadata(assetId);

        return FileInfoResponse.builder()
                .fileName(document.getTitle())
                .size(stat.size())
                .contentType(stat.contentType())
                .lastModified(stat.lastModified())
                .build();
    }

    /**
     * Deletes a document and its associated MinIO asset.
     * IMPROVEMENT NOTE: Consider making this two-phase or idempotent:
     * soft-delete the DB row first, then schedule an async MinIO cleanup. This way a failed
     * MinIO call won't roll back the DB transaction and leave the document visible to users.
     */
    @Transactional
    public void deleteDocument(String docId) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        String assetId = document.getAssetId();
        if (assetId != null && !assetId.isBlank()) {
            minioService.deleteFile(assetId);
        }

        documentRepository.delete(document);
    }

    @Transactional
    public void deleteByOwnerId(String ownerId) {
        List<Document> documents = documentRepository.findByOwnerId(ownerId);
        for (Document doc : documents) {
            if (doc.getAssetId() != null && !doc.getAssetId().isBlank()) {
                try {
                    minioService.deleteFile(doc.getAssetId());
                } catch (Exception e) {
                    log.error("Failed to delete asset {} for document {}: {}",
                            doc.getAssetId(), doc.getId(), e.getMessage());
                }
            }
        }
        documentRepository.deleteByOwnerId(ownerId);
        log.info("Deleted all documents and assets for owner {}", ownerId);
    }

    /** Returns the owner ID of a document without loading the entire entity. */
    public String getOwnerId(String docId) {
        return documentRepository
                .findById(docId)
                .map(Document::getOwnerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
    }

    /** Checks whether a document with the given ID exists in the database. */
    public boolean existsById(String docId) {
        return documentRepository.existsById(docId);
    }

    /**
     * Generates a pre-signed MinIO URL so the client can upload a file directly to object storage
     * The URL expires after 10 minutes.
     *
     * @param fileName the original file name (used to preserve the extension in the asset ID)
     * @return the generated assetId and the pre-signed upload URL
     */
    public PresignResponse generatePresignedUrl(String fileName) {
        String assetId = minioService.generateUniqueAssetName(fileName);
        String url = minioService.getPresignedUrl(assetId, 10);
        return new PresignResponse(assetId, url);
    }

    /**
     * Returns a paginated list of collaboratively recommended documents for a user, ranked by the Neo4j graph engine
     * (shared classrooms, mutual downloads, social graph).
     * * * *
     * Step 1: Candidates retrieval
     * Step 2: Consistency check
     * Step 3: In-memory pagination
     * Step 4: Multi-source enrichment
     * Step 5: Mapping DTO
     * Returns object carrying three fields per result:
     *   "id"               — document ID
     *   "reasonType"       — ENROLLED_CLASS | DOWNLOADED
     *   "reasonTriggerId"  — ID of the class or trigger-document that caused the match
     *
     * @param userId   the authenticated user to generate recommendations for
     * @param pageable page number and size
     * @return a Page of RelatedDocumentsResponse each carrying a RecommendationReason
     */
    public Page<RelatedDocumentsResponse> getRecommendedDocuments(String userId, Pageable pageable) {
        List<Map<String, Object>> recommendations = graphSyncService.getCollaborativeRecommendations(userId);

        if (recommendations.isEmpty()) return Page.empty(pageable);

        // Resolve document IDs against PostgreSQL to filter out stale Neo4j nodes
        List<String> docIds = recommendations.stream()
                .map(r -> (String) r.get("id"))
                .filter(Objects::nonNull)
                .toList();

        Map<String, Document> documentMap = documentRepository
                .findAllById(docIds)
                .stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        // Keep only results whose document still exists in the relational DB,
        // while preserving Neo4j's original ranking order.
        List<Map<String, Object>> results = recommendations.stream()
                .filter(r -> documentMap.containsKey((String) r.get("id")))
                .toList();

        // Trigger title and profile fetches are scoped to the current page only
        int page  = pageable.getPageNumber();
        int size  = pageable.getPageSize();
        int start = Math.min(page * size, results.size());
        int end   = Math.min((page + 1) * size, results.size());

        if (start >= results.size()) return new PageImpl<>(List.of(), pageable, results.size());

        List<Map<String, Object>> currentPageItems = results.subList(start, end);

        List<String> triggerDocIds = currentPageItems.stream()
                .filter(r -> "DOWNLOADED".equals(r.get("reasonType")))
                .map(r -> (String) r.get("reasonTriggerId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<String> triggerClassIds = currentPageItems.stream()
                .filter(r -> "ENROLLED_CLASS".equals(r.get("reasonType")))
                .map(r -> (String) r.get("reasonTriggerId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // triggerId -> human-readable label
        Map<String, String> triggerTitleMap = new HashMap<>();

        if (!triggerDocIds.isEmpty()) {
            documentRepository
                    .findAllById(triggerDocIds)
                    .forEach(doc -> triggerTitleMap.put(doc.getId(), doc.getTitle()));
        }

        if (!triggerClassIds.isEmpty()) {
            try {
                APIResponse<Map<String, String>> classNames = lmsClient.getClassNamesBatch(triggerClassIds);
                if (classNames != null && classNames.getResult() != null) {
                    triggerTitleMap.putAll(classNames.getResult());
                }
            } catch (Exception e) {
                log.warn("[RECO] Failed to fetch class names for trigger labels: {}", e.getMessage());
            }
        }

        List<String> ownerIds = currentPageItems.stream()
                .map(r -> documentMap.get((String) r.get("id")).getOwnerId())
                .distinct()
                .toList();

        Map<String, ProfileResponse> profileMap = fetchProfileMap(ownerIds);

        List<RelatedDocumentsResponse> pageContent = currentPageItems.stream()
                .map(r -> {
                    String id  = (String) r.get("id");
                    Document doc = documentMap.get(id);
                    ProfileResponse profile = profileMap.get(doc.getOwnerId());

                    String triggerId = (String) r.get("reasonTriggerId");
                    RecommendationReason reason = RecommendationReason.builder()
                            .type((String) r.get("reasonType"))
                            // Fall back to the raw triggerId if the name could not be resolved
                            // (e.g. LMS was unreachable or trigger doc was deleted).
                            .title(triggerTitleMap.getOrDefault(triggerId, triggerId))
                            .build();

                    return toRelatedDocument(doc, profile, reason);
                })
                .toList();

        if (!pageContent.isEmpty()) {
            documentRepository.incrementViews(pageContent.stream().map(RelatedDocumentsResponse::getId).toList());
        }

        return new PageImpl<>(pageContent, pageable, results.size());
    }

    /**
     * Returns documents that are related to a given document using a hybrid Reciprocal Rank Fusion (RRF)
     * of pgvector semantic search and Neo4j item-based collaborative filtering.
     *   - Semantic similarity (cosine distance on 768-dim embeddings, weight = context-adaptive)
     *   - Item-based CF (co-download graph in Neo4j, weight = context-adaptive)
     * @param docId    the source document whose neighbours to find
     * @param pageable page number and size
     * @return a Page of RelatedDocumentsResponse ordered by hybrid RRF score descending
     */
    public Page<RelatedDocumentsResponse> getRelatedDocuments(String docId, Pageable pageable) {
        Document document = documentRepository
                .findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        // Return empty rather than throwing — the embedding is populated asynchronously after upload
        // The document is usable but not yet ready for vector search.
        if (document.getEmbedding() == null) {
            log.debug("[RELATED] Document {} has no embedding yet; returning empty page", docId);
            return Page.empty(pageable);
        }

        // Convert float[] to the "[f1, f2, ...]" string format expected by the pgvector cast.
        String vectorStr = Arrays.toString(document.getEmbedding());

        // Build the context string from title + keywords for the semantic search component.
        StringBuilder queryBuilder = new StringBuilder();
        if (document.getTitle() != null)    queryBuilder.append(document.getTitle()).append(" ");
        if (document.getKeywords() != null) queryBuilder.append(String.join(" ", document.getKeywords()));

        String searchContext = queryBuilder.toString().trim();
        if (searchContext.isEmpty()) searchContext = " ";

        Page<RecommendationItem> itemsPage =
                documentRecommendationService.getHybridRelatedDocumentIds(searchContext, docId, vectorStr, pageable);

        if (itemsPage.isEmpty()) return Page.empty(pageable);

        List<RecommendationItem> items = itemsPage.getContent();
        List<String> relatedDocIds = items.stream().map(RecommendationItem::getDocId).toList();

        Map<String, Document> documentMap = documentRepository.findAllById(relatedDocIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<String> ownerIds = relatedDocIds.stream()
                .map(documentMap::get)
                .filter(Objects::nonNull)
                .map(Document::getOwnerId)
                .distinct()
                .toList();
        Map<String, ProfileResponse> profileMap = fetchProfileMap(ownerIds);

        List<RelatedDocumentsResponse> dtos = items.stream()
                .map(item -> {
                    Document doc = documentMap.get(item.getDocId());
                    if (doc == null) return null;
                    return toRelatedDocument(doc, profileMap.get(doc.getOwnerId()), item.getReason());
                })
                .filter(Objects::nonNull)
                .toList();

        if (!dtos.isEmpty()) {
            documentRepository.incrementViews(dtos.stream().map(RelatedDocumentsResponse::getId).toList());
        }

        return new PageImpl<>(dtos, pageable, itemsPage.getTotalElements());
    }

    public Page<DocumentMetadataResponse> getForYouFeed(String userId, Pageable pageable) {
        Page<RecommendationItem> recommendations = documentRecommendationService.getForYouFeed(userId, pageable);

        if (recommendations.isEmpty()) return Page.empty(pageable);

        List<RecommendationItem> items = recommendations.getContent();
        List<String> docIds = items.stream().map(RecommendationItem::getDocId).toList();

        // Batch-load all document entities on the current page in one query.
        Map<String, Document> documentMap = documentRepository
                .findAllById(docIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        // Batch profile fetch for the current page only.
        List<String> ownerIds = documentMap.values().stream()
                .map(Document::getOwnerId)
                .distinct()
                .toList();
        Map<String, ProfileResponse> profileMap = fetchProfileMap(ownerIds);

        // ── Title resolution (current page only) ───────────────────────
        List<String> triggerDocIds = items.stream()
                .filter(item -> item.getReason() != null
                                && "DOWNLOADED".equals(item.getReason().getType())
                                && item.getTriggerId() != null)
                .map(RecommendationItem::getTriggerId)
                .distinct()
                .toList();

        List<String> triggerClassIds = items.stream()
                .filter(item -> item.getReason() != null
                                && "ACTIVE_CLASS".equals(item.getReason().getType())
                                && item.getTriggerId() != null)
                .map(RecommendationItem::getTriggerId)
                .distinct()
                .toList();

        List<String> triggerTopicIds = items.stream()
                .filter(item -> item.getReason() != null
                                && "FAVORITE_TOPIC".equals(item.getReason().getType())
                                && item.getTriggerId() != null)
                .map(RecommendationItem::getTriggerId)
                .distinct()
                .toList();

        Map<String, String> triggerTitleMap = new HashMap<>();
        if (!triggerDocIds.isEmpty()) {
            documentRepository.findAllById(triggerDocIds).forEach(doc ->
                    triggerTitleMap.put(doc.getId(), doc.getTitle()));
        }

        if (!triggerClassIds.isEmpty()) {
            try {
                APIResponse<Map<String, String>> classRes = lmsClient.getClassNamesBatch(triggerClassIds);
                if (classRes != null && classRes.getResult() != null) {
                    triggerTitleMap.putAll(classRes.getResult());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve class names from LMS: {}", e.getMessage());
            }
        }

        if (!triggerTopicIds.isEmpty()) {
            try {
                APIResponse<Map<String, String>> topicRes = lmsClient.getTopicNamesBatch(triggerTopicIds);
                if (topicRes != null && topicRes.getResult() != null) {
                    triggerTitleMap.putAll(topicRes.getResult());
                }
            } catch (Exception e) {
                log.error("Failed to resolve topic names from LMS: {}", e.getMessage());
            }
        }

        return recommendations.map(item -> {
            Document document = documentMap.get(item.getDocId());
            if (document == null) return null;
            ProfileResponse profile = profileMap.get(document.getOwnerId());

            // Resolve triggerId -> human-readable label and write into reason.title
            // Falls back to the raw triggerId if resolution failed
            RecommendationReason reason = item.getReason();
            if (reason != null && item.getTriggerId() != null) {
                reason.setTitle(triggerTitleMap.getOrDefault(item.getTriggerId(), item.getTriggerId()));
            }

            return toMetadataResponse(document, profile, reason);
        });
    }

    /**
     * @param doc     the document entity
     * @param profile the author's profile (may be null — handled gracefully)
     * @param reason  the recommendation reason (may be null for anonymous / trending)
     */
    private DocumentMetadataResponse toMetadataResponse(
            Document doc, ProfileResponse profile, RecommendationReason reason) {
        DocumentMetadataResponse.Author authorDto = null;
        if (profile != null) {
            authorDto = DocumentMetadataResponse.Author.builder()
                    .id(profile.getId())
                    .name(profile.getFullName())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        }
        return DocumentMetadataResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .author(authorDto)
                .documentType(doc.getDocumentType())
                .university(doc.getUniversity())
                .course(doc.getCourse())
                .description(doc.getDescription())
                .downloadable(doc.getDownloadable())
                .downloadUrl(gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix()
                             + "/document/download/" + doc.getId())
                .viewUrl(gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix()
                         + "/resource/download/asset/" + doc.getAssetId())
                .downloadCount(doc.getDownloadCount())
                .previewImageUrl(doc.getPreviewImageUrl())
                .views(doc.getViews())
                .createdAt(doc.getCreatedAt())
                .summary(doc.getSummary())
                .keywords(doc.getKeywords())
                .universityId(doc.getUniversityId())
                .courseId(doc.getCourseId())
                .topicId(doc.getTopicId())
                .recommendationReason(reason)
                .deepAiStatus(doc.getDeepAiStatus())
                .build();
    }

    /**
     * Maps a Document entity + its owner's ProfileResponse into a RelatedDocumentsResponse DTO.
     * The author field is set to null if the profile could not be fetched (graceful degradation).
     * The download URL is the resource-service streaming endpoint (used for both viewing and downloading).
     */
    private RelatedDocumentsResponse toRelatedDocument(
            Document document,
            ProfileResponse profile,
            RecommendationReason reason) {
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
                .downloadable(document.getDownloadable())
                .keywords(document.getKeywords())
                .previewImageUrl(document.getPreviewImageUrl())
                .views(document.getViews())
                .recommendationReason(reason)
                .build();
    }

    private Map<String, ProfileResponse> fetchProfileMap(List<String> ownerIds) {
        if (ownerIds.isEmpty()) return Collections.emptyMap();

        return profileClient.getProfiles(ownerIds)
                .stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));
    }

    /**
     * Checks whether a string is a valid UUID.
     * Used in search() to detect when a user is searching by document ID rather than title.
     */
    private boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
