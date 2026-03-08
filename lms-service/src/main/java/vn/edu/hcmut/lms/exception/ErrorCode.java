package vn.edu.hcmut.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    // ==========================================
    // SYSTEM & AUTHENTICATION (1000 - 1999)
    // ==========================================
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT(1002, "Invalid input format", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD(1003, "This field is required", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1004, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS(1005, "Invalid token", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(1006, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    SYNC_FAILED(1007, "An error occurred during the synchronization process", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==========================================
    // ENTITY NOT FOUND (2000 - 2999)
    // ==========================================
    PROFILE_NOT_FOUND(2001, "Profile not found", HttpStatus.NOT_FOUND),
    TUTOR_NOT_FOUND(2002, "Tutor not found", HttpStatus.NOT_FOUND),
    CLASS_NOT_FOUND(2003, "Class not found", HttpStatus.NOT_FOUND),
    TOPIC_NOT_FOUND(2004, "Topic not found", HttpStatus.NOT_FOUND),
    ENROLLMENT_NOT_FOUND(2005, "Enrollment not found", HttpStatus.NOT_FOUND),

    // ==========================================
    // BUSINESS LOGIC & CONFLICTS (3000 - 3999)
    // ==========================================
    TUTOR_ALREADY_REGISTERED(3001, "User is already a tutor", HttpStatus.BAD_REQUEST),
    CLASS_NOT_AVAILABLE(3002, "Class is not available for enrollment", HttpStatus.BAD_REQUEST),
    ALREADY_ENROLLED(3003, "You have already enrolled in this class", HttpStatus.BAD_REQUEST),
    CANNOT_ENROLL_OWN_CLASS(3004, "You cannot enroll in your own class", HttpStatus.BAD_REQUEST),
    ENROLLMENT_PENDING(3005, "Your enrollment request is still pending approval", HttpStatus.BAD_REQUEST),
    SCHEDULE_CONFLICT(3006, "Schedule conflict detected", HttpStatus.BAD_REQUEST),
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
