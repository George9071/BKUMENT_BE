package vn.edu.hcmut.social.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error"),
    RESOURCE_NOT_EXISTED(1001, "Resource not existed"),
    MINIO_ERROR(1002, "MinIO operation failed"),
    INVALID_RESOURCE_TYPE(1003, "Invalid resource type"),
    VALIDATION_FAILED(1001, "Invalid body"),
    UNAUTHENTICATED(1006, "Unauthenticated"),
    DOCUMENT_NOT_FOUND(404, "Document not found"),
    INVALID_TOKEN_CLAIMS(401, "Invalid token"),
    ALREADY_RATED(1100, "You have already rated this resource"),
    TUTOR_REVIEW_NOT_FOUND(1101, "Tutor review not found"),
    UNAUTHORIZED(1007, "You do not have permission to perform this action");

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private final int code;
    private final String message;
}
