package vn.edu.hcmut.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    TUTOR_ALREADY_REGISTERED(1001, "User is already a tutor", HttpStatus.BAD_REQUEST),
    PROFILE_NOT_FOUND(1002, "Profile not found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1003, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS(9001, "Invalid token", HttpStatus.UNAUTHORIZED),
    TUTOR_NOT_FOUND(9002, "Tutor not found", HttpStatus.NOT_FOUND),
    CLASS_NOT_FOUND(9003, "Class not found", HttpStatus.NOT_FOUND),
    TOPIC_NOT_FOUND(9004, "Topic not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ACTION(9005, "unauthorized action", HttpStatus.UNAUTHORIZED),
    CLASS_NOT_AVAILABLE(9006, "class not available", HttpStatus.BAD_REQUEST),
    ALREADY_ENROLLED(9007, "you already enrolled this class", HttpStatus.BAD_REQUEST),
    CANNOT_ENROLL_OWN_CLASS(9008, "you cannot enroll your own class", HttpStatus.BAD_REQUEST),
    ENROLLMENT_NOT_FOUND(9009, "enrollment not found", HttpStatus.NOT_FOUND),
    SYNC_FAILED(9998, "An error occurred during the synchronization process", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULE_CONFLICT(9010, "Schedule conflict", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACCESS(9011, "you are not the owner of this class", HttpStatus.FORBIDDEN),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
