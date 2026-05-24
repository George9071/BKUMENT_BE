package vn.edu.hcmut.document.service;

import java.io.InputStream;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.dto.response.DocumentProcessResponse;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.httpclient.AiClient;
import vn.edu.hcmut.document.utils.StreamMultipartFile;

/**
 * Handles long-running AI document processing asynchronously so the main request thread
 * can return a fast response after the initial lightweight AI pass.
 *
 * Processing stages:
 *   1. Fast pass (synchronous, in DocumentService): keywords + short summary.
 *   2. Deep pass (this service, async): full content extraction + 768-dim embedding vector.
 *
 * The deep pass result is written back to the already-persisted Document row once complete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAsyncService {

    private final DocumentRepository documentRepository;
    private final MinioService minioService;
    private final AiClient aiClient;

    /**
     * Runs a deep AI analysis on the uploaded PDF in a background thread.
     *
     * Steps:
     *   1. Re-downloads the PDF bytes from MinIO using the asset ID.
     *   2. Wraps the stream as a MultipartFile and sends it to the AI service's full-processing endpoint.
     *   3. Writes the returned content, summary, and embedding vector back to the document row.
     *
     * This method is intentionally decoupled from the HTTP request lifecycle
     * errors here are logged and swallowed, so they cannot affect the already-committed document record.
     *
     * @param assetId   MinIO object key of the PDF to analyse
     * @param fileName  original file name (used as the multipart "filename" in the AI request)
     * @param fileSize  byte size of the file (required for multipart content-length header)
     * @param docId     ID of the already-persisted Document row to update when done
     */
    @Async("aiDeepProcessExecutor")
    public void runBackgroundAiProcess(String assetId, String fileName, long fileSize, String docId) {
        log.info("[ASYNC] Starting deep AI analysis for Document ID: {}", docId);

        try (InputStream inputStream = minioService.getFileInputStream(assetId)) {
            MultipartFile multipartFile =
                    new StreamMultipartFile("file", fileName, "application/pdf", fileSize, inputStream);

            // Call the AI service's full-processing endpoint
            DocumentProcessResponse result = aiClient.processDocument(multipartFile);

            if (result != null) {
                updateDocumentWithAiResult(docId, result);
                log.info("[ASYNC] Deep AI update complete for Document: {}", docId);
            } else {
                log.warn("[ASYNC] AI service returned null result for Document: {}; skipping update", docId);
            }
        } catch (Exception e) {
            log.error("[ASYNC] Error during background AI processing for asset {}", assetId, e);
        }
    }

    /**
     * Persists the deep-AI results (summary, full content, embedding vector) into the Document row.
     *
     * EDGE CASE: If the document was deleted between the async job starting and finishing,
     * docOpt.isEmpty() will be true and the update is silently skipped.
     *
     * @param docId  the document to update
     * @param result the AI service response containing summary, full content, and the embedding vector
     */
    @Transactional
    public void updateDocumentWithAiResult(String docId, DocumentProcessResponse result) {
        Optional<Document> docOpt = documentRepository.findById(docId);

        if (docOpt.isEmpty()) {
            log.warn("[ASYNC] Document {} not found when writing AI results — may have been deleted", docId);
            return;
        }

        Document document = docOpt.get();

        // Overwrite the fast-pass summary with the deep-pass summary.
        document.setSummary(result.getSummary());

        // Overwrite the fast-pass keywords with the deep-pass keywords.
        if (result.getKeywords() != null && !result.getKeywords().isEmpty()) {
            document.setKeywords(result.getKeywords());
        }

        // Store the full extracted text content of the document.
        document.setContent(result.getContent());

        // Convert List<Double> -> float[] for the pgvector column.
        // Guard against empty vector list to avoid storing a zero-length array.
        if (result.getVector() != null && !result.getVector().isEmpty()) {
            float[] floatVector = new float[result.getVector().size()];
            for (int i = 0; i < result.getVector().size(); i++) {
                floatVector[i] = result.getVector().get(i).floatValue();
            }
            document.setEmbedding(floatVector);
        }

        document.setKeywords(result.getKeywords());
        document.setDeepAiStatus(vn.edu.hcmut.document.constant.AiAnalyzeStatus.COMPLETED);

        documentRepository.save(document);
    }
}
