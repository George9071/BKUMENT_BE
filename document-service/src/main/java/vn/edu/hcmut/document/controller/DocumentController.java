package vn.edu.hcmut.document.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.configuration.GatewayProperties;
import vn.edu.hcmut.document.dto.request.DocumentMetadataRequest;
import vn.edu.hcmut.document.dto.response.*;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;
import vn.edu.hcmut.document.repository.httpclient.ProfileClient;
import vn.edu.hcmut.document.service.DocumentService;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentController {
    DocumentService documentService;
    GatewayProperties gatewayProperties;
    ProfileClient profileClient;

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            String profileId = jwt.getClaimAsString("profile_id");
            if (profileId == null || profileId.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            }

            return profileId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "Document Service is running";
    }

    @GetMapping("/search")
    public APIResponse<Page<DocumentMetadataResponse>> searchDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.search(q, pageable);

        Page<DocumentMetadataResponse> result = documents.map(doc -> DocumentMetadataResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .authorId(doc.getOwnerId())
                .documentType(doc.getDocumentType())
                .university(doc.getUniversity())
                .course(doc.getCourse())
                .description(doc.getDescription())
                .downloadable(doc.isDownloadable())
                .downloadUrl(gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/document/download/"
                        + doc.getId())
                .viewUrl(gatewayProperties.getBaseUrl() + gatewayProperties.getApiPrefix() + "/resource/download/asset/"
                        + doc.getAssetId())
                .downloadCount(doc.getDownloadCount())
                .previewImageUrl(doc.getPreviewImageUrl())
                .createdAt(doc.getCreatedAt())
                .summary(doc.getSummary())
                .build());

        return APIResponse.<Page<DocumentMetadataResponse>>builder()
                .result(result)
                .message("Search documents successfully")
                .build();
    }

    @PostMapping("updateMetadata")
    public APIResponse<DocumentMetadataResponse> createDocument(@RequestBody @Valid DocumentMetadataRequest request) {
        String authorId = getProfileIdFromToken();
        Document document = documentService.createOrUpdateDocument(request, authorId);

        return APIResponse.<DocumentMetadataResponse>builder()
                .result(DocumentMetadataResponse.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .documentType(document.getDocumentType())
                        .build())
                .message("Document created successfully")
                .build();
    }

    @PutMapping("/{docId}")
    public APIResponse<DocumentMetadataResponse> updateDocument(
            @PathVariable String docId, @RequestBody @Valid DocumentMetadataRequest request) {
        Document document = documentService.updateDocument(request, docId);

        return APIResponse.<DocumentMetadataResponse>builder()
                .result(DocumentMetadataResponse.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .documentType(document.getDocumentType())
                        .build())
                .message("Document updated successfully")
                .build();
    }

    @GetMapping("/{docId}")
    public APIResponse<DocumentMetadataResponse> getDocumentInfo(@PathVariable String docId) {
        return APIResponse.<DocumentMetadataResponse>builder()
                .result(documentService.getDocumentInfo(docId))
                .message("Get document info successfully")
                .build();
    }

    @GetMapping("analyze/{assetId}")
    public APIResponse<DocAnalyzeResponse> analyseDocument(
            @PathVariable String assetId, @RequestParam(required = false) String fileName) {
        String finalName = (fileName == null || fileName.trim().isEmpty()) ? assetId : fileName;
        String authorId = getProfileIdFromToken();

        return APIResponse.<DocAnalyzeResponse>builder()
                .result(documentService.processAndCreateDocument(assetId, finalName, authorId))
                .message("Phân tích và tạo tài liệu thành công")
                .build();
    }

    @GetMapping("/download/{docId}")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable String docId) {
        String userId = getProfileIdFromToken();
        ResourceDownloadResponse data = documentService.downloadDocument(docId, userId);

        String fileName = data.getFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "document";
        }

        String encodedFileName =
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(data.getContentType()))
                .contentLength(data.getFileSize())
                .body(new InputStreamResource(data.getInputStream()));
    }

    @DeleteMapping("/{docId}")
    public APIResponse<String> deleteDocument(@PathVariable String docId) {
        documentService.deleteDocument(docId);
        return APIResponse.<String>builder()
                .message("Document deleted successfully")
                .build();
    }

    @GetMapping("/presign")
    public APIResponse<PresignResponse> getPresignUrl(@RequestParam(required = false) String fileName) {
        return APIResponse.<PresignResponse>builder()
                .result(documentService.generatePresignedUrl(fileName))
                .message("Presign URL generated")
                .build();
    }

    @GetMapping("/related/{docId}")
    public APIResponse<Page<RelatedDocumentsResponse>> getRelatedDocuments(
            @PathVariable String docId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<RelatedDocumentsResponse>>builder()
                .result(documentService.getRelatedDocuments(docId, pageable))
                .message("Get related documents successfully")
                .build();
    }

    @GetMapping("/search-universities")
    public APIResponse<List<UniversityResponse>> getAllUniversitiesByQuery(@RequestParam(required = false) String q) {
        List<UniversityResponse> universities = profileClient.searchUniversities(q);
        return APIResponse.<List<UniversityResponse>>builder()
                .result(universities)
                .message("Get universities successfully")
                .build();
    }

    @GetMapping("/recommendations")
    public APIResponse<Page<RelatedDocumentsResponse>> getRecommendationDocuments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

        String userId = getProfileIdFromToken();

        Pageable pageable = PageRequest.of(page, size);

        return APIResponse.<Page<RelatedDocumentsResponse>>builder()
                .result(documentService.getRecommendedDocuments(userId, pageable))
                .message("Get related documents successfully")
                .build();
    }
}
