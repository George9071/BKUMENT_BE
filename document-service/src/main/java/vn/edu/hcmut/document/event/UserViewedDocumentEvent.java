package vn.edu.hcmut.document.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserViewedDocumentEvent {
    String profileId;
    String documentId;
    String timestamp;
}
