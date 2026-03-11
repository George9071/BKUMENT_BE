package vn.edu.hcmut.blog.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    RESOURCE_NOT_EXISTED(1001, "Resource not existed", HttpStatus.NOT_FOUND),
    MINIO_ERROR(1002, "MinIO operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1003, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS(9001, "Invalid token", HttpStatus.UNAUTHORIZED),
    INVALID_RESOURCE_TYPE(1004, "Invalid resource type", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(1005, "Invalid body", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
