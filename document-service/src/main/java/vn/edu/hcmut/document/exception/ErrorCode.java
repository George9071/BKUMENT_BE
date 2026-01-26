package vn.edu.hcmut.document.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error"),
    RESOURCE_NOT_EXISTED(1001, "Resource not existed"),
    MINIO_ERROR(1002, "MinIO operation failed"),
    INVALID_RESOURCE_TYPE(1003, "Invalid resource type"),
    VALIDATION_FAILED(1001, "Invalid body");

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private final int code;
    private final String message;
}
