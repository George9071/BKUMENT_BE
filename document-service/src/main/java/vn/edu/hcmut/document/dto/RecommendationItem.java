package vn.edu.hcmut.document.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.dto.response.RecommendationReason;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationItem {

    /** The recommended document's ID. */
    String docId;

    /**
     * Raw ID of the trigger entity that caused this recommendation.
     */
    String triggerId;

    /**
     * The recommendation reason attached to this item.
     */
    RecommendationReason reason;
}
