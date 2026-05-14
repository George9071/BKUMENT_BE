package vn.edu.hcmut.social.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(    9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED(          1000, "Invalid body", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(         1001, "Resource not existed", HttpStatus.NOT_FOUND),
    MINIO_ERROR(                1002, "MinIO operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED(               1003, "You are not authorized to perform this action", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(            1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS(       1007, "Invalid token", HttpStatus.UNAUTHORIZED),
    INVALID_RESOURCE_TYPE(      1008, "Invalid resource type", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_FOUND(         1009, "Document not found", HttpStatus.NOT_FOUND),

    ALREADY_RATED(              1100, "You have already rated this resource", HttpStatus.CONFLICT),
    REPLY_DEPTH_EXCEEDED(       1101, "Cannot reply to a reply — only one level of nesting is supported", HttpStatus.BAD_REQUEST),
    TUTOR_REVIEW_NOT_FOUND(     1102, "Tutor review not found", HttpStatus.NOT_FOUND),

    CANNOT_RATE_OWN_RESOURCE(   1201, "You cannot rate your own resource", HttpStatus.BAD_REQUEST),

    CANNOT_REPORT_OWN_RESOURCE( 1301, "You cannot report your own content",                      HttpStatus.BAD_REQUEST),
    REPORT_ALREADY_SUBMITTED(   1302, "You already have a pending report for this resource",    HttpStatus.CONFLICT),
    REPORT_ALREADY_PROCESSED(   1303, "This report has already been processed",                  HttpStatus.CONFLICT),
    INVALID_REPORT_STATUS(      1304, "Invalid report status; must be PENDING, APPROVED, or REJECTED",HttpStatus.BAD_REQUEST),
    CANNOT_DEL_PROCESSED_REPORT(1305, "Cannot delete a report that has already been processed", HttpStatus.BAD_REQUEST),

    CANNOT_REVIEW_SELF(         1401, "You cannot review yourself",                                          HttpStatus.BAD_REQUEST),
    INVALID_REPORT_TYPE(        1402, "Invalid report type filter", HttpStatus.BAD_REQUEST);

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
