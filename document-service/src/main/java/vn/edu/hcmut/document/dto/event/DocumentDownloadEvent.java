package vn.edu.hcmut.document.dto.event;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentDownloadEvent {
    private String accountId;
    private String documentId;
    private String topicId;
    private LocalDateTime timestamp;
}
