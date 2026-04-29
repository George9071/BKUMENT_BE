package vn.edu.hcmut.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.document.entity.DocumentDownload;
import vn.edu.hcmut.document.exception.AppException;
import vn.edu.hcmut.document.exception.ErrorCode;
import vn.edu.hcmut.document.repository.DocumentDownloadRepository;
import vn.edu.hcmut.document.repository.DocumentRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDownloadService {
    private final DocumentDownloadRepository documentDownloadRepository;
    private final DocumentRepository documentRepository;

    /**
     * Attempts to insert a record marking the user's first download of a document.
     *
     * @param docId  the document being downloaded
     * @param userId the user who is downloading it
     * @return {@code true} if this is the user's first download of the document;
     *         {@code false} if they have downloaded it before
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRecordDownload(String docId, String userId) {
        try {
            DocumentDownload download = DocumentDownload.builder()
                    .profileId(userId)
                    .documentId(docId)
                    .downloadedAt(LocalDateTime.now())
                    .build();
            download.setDocumentId(docId);
            download.setProfileId(userId);
            documentDownloadRepository.save(download);
            documentDownloadRepository.flush();
            log.debug("First download recorded — user={}, doc={}", userId, docId);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation: this user has downloaded this document before.
            // Return false so the caller knows not to award points.
            log.debug("Repeat download detected — user={}, doc={} — no points awarded", userId, docId);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementDownloadCount(String docId) {
        if (!documentRepository.existsById(docId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        }
        documentRepository.incrementDownloadCount(docId);
    }
}
