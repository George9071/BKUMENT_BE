package vn.edu.hcmut.document.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDownloadedDocumentEvent {
    private String profileId;
    private String documentId;
    private String topicId;
    private String timestamp;
}
