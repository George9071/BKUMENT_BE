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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAsyncService {

    private final DocumentRepository documentRepository;
    private final MinioService minioService;
    private final AiClient aiClient;

    @Async
    public void runBackgroundAiProcess(String assetId, String fileName, long fileSize, String docId) {
        log.info("[ASYNC-THREAD] Bắt đầu phân tích AI sâu cho Document ID: {}", docId);

        try (InputStream inputStream = minioService.getFileInputStream(assetId)) {
            MultipartFile multipartFile =
                    new StreamMultipartFile("file", fileName, "application/pdf", fileSize, inputStream);

            DocumentProcessResponse result = aiClient.processDocument(multipartFile);

            if (result != null) {
                updateDocumentWithAiResult(docId, result);
                log.info("[ASYNC-THREAD] Hoàn tất cập nhật AI cho Document: {}", docId);
            }
        } catch (Exception e) {
            log.error("[ASYNC-THREAD] Lỗi khi xử lý ngầm tài liệu {}", assetId, e);
        }
    }

    @Transactional
    public void updateDocumentWithAiResult(String docId, DocumentProcessResponse result) {
        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isPresent()) {
            Document document = docOpt.get();
            document.setSummary(result.getSummary());
            document.setContent(result.getContent());

            if (result.getVector() != null && !result.getVector().isEmpty()) {
                float[] floatVector = new float[result.getVector().size()];
                for (int i = 0; i < result.getVector().size(); i++) {
                    floatVector[i] = result.getVector().get(i).floatValue();
                }
                document.setEmbedding(floatVector);
            }
            documentRepository.save(document);
        }
    }
}
