package vn.edu.hcmut.lms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {

    // 10xx: General, Validation & Authentication
    INVALID_KEY                 (1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT              (1002, "Invalid input format", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD              (1003, "This field is required", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED             (1004, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS        (1005, "Invalid token", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED               (1006, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    SYNC_FAILED                 (1007, "An error occurred during the synchronization process", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED                (1008, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),

    // 20xx: Not Found
    PROFILE_NOT_FOUND           (2001, "Profile not found", HttpStatus.NOT_FOUND),
    TUTOR_NOT_FOUND             (2002, "Tutor not found", HttpStatus.NOT_FOUND),
    CLASS_NOT_FOUND             (2003, "Class not found", HttpStatus.NOT_FOUND),
    SUBJECT_NOT_FOUND           (2004, "Subject not found", HttpStatus.NOT_FOUND),
    TOPIC_NOT_FOUND             (2005, "Topic not found", HttpStatus.NOT_FOUND),
    ENROLLMENT_NOT_FOUND        (2006, "Enrollment not found", HttpStatus.NOT_FOUND),
    APPLICATION_NOT_FOUND       (2007, "Application not found", HttpStatus.NOT_FOUND),

    // 30xx: Business Logic & Scheduling
    TUTOR_ALREADY_REGISTERED    (3001, "User is already a tutor", HttpStatus.BAD_REQUEST),
    CLASS_NOT_AVAILABLE         (3002, "Class is not available for enrollment", HttpStatus.BAD_REQUEST),
    ALREADY_ENROLLED            (3003, "You have already enrolled in this class", HttpStatus.BAD_REQUEST),
    CANNOT_ENROLL_OWN_CLASS     (3004, "You cannot enroll in your own class", HttpStatus.BAD_REQUEST),
    ENROLLMENT_PENDING          (3005, "Your enrollment request is still pending approval", HttpStatus.BAD_REQUEST),
    SCHEDULE_CONFLICT           (3006, "Schedule conflict detected", HttpStatus.BAD_REQUEST),
    ENROLLMENT_COOLDOWN         (3007, "You were previously denied entry to this class. Please try again in 1 day", HttpStatus.BAD_REQUEST),
    NOT_ENOUGH_POINTS           (3008, "You do not have enough points to register as a tutor", HttpStatus.BAD_REQUEST),
    REGISTRATION_PENDING        (3009, "Your registration is still being processed", HttpStatus.BAD_REQUEST),
    REGISTRATION_COOLDOWN       (3010, "You can resubmit your registration 3 days after a rejection", HttpStatus.BAD_REQUEST),
    INVALID_STATUS              (3011, "Invalid status", HttpStatus.BAD_REQUEST),
    ALREADY_COMPLETED           (3012, "You have already completed this course", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION   (3013, "Invalid class status transition", HttpStatus.BAD_REQUEST),

    // 41xx: Form Validation (Suggestions & Subjects)
    SUGGESTION_TYPE_REQUIRED    (4101, "Suggestion type is required", HttpStatus.BAD_REQUEST),
    PROPOSED_NAME_REQUIRED      (4102, "Proposed name is required", HttpStatus.BAD_REQUEST),
    PROPOSED_NAME_LENGTH_INVALID(4103, "Proposed name must be between 2 and 255 characters", HttpStatus.BAD_REQUEST),
    REASON_TOO_LONG             (4104, "Reason must not exceed 2000 characters", HttpStatus.BAD_REQUEST),
    PARENT_SUBJECT_REQUIRED     (4105, "Parent subject is required when proposing a topic", HttpStatus.BAD_REQUEST),
    FINAL_NAME_LENGTH_INVALID   (4106, "Final name must be between 2 and 255 characters", HttpStatus.BAD_REQUEST),
    REJECTION_REASON_REQUIRED   (4107, "Rejection reason is required", HttpStatus.BAD_REQUEST),
    REJECTION_REASON_TOO_LONG   (4108, "Rejection reason must not exceed 2000 characters", HttpStatus.BAD_REQUEST),
    NOTE_TOO_LONG               (4109, "Note must not exceed 2000 characters", HttpStatus.BAD_REQUEST),
    SUBJECT_NAME_REQUIRED       (4110, "Subject name is required", HttpStatus.BAD_REQUEST),
    SUBJECT_NAME_LENGTH_INVALID (4111, "Subject name must be between 2 and 255 characters", HttpStatus.BAD_REQUEST),
    SUBJECT_ID_REQUIRED         (4112, "Subject ID is required", HttpStatus.BAD_REQUEST),
    TOPIC_NAME_REQUIRED         (4113, "Topic name is required", HttpStatus.BAD_REQUEST),
    TOPIC_NAME_LENGTH_INVALID   (4114, "Topic name must be between 2 and 255 characters", HttpStatus.BAD_REQUEST),
    FINAL_ID_REQUIRED          (4124, "An identifier must be entered when reviewing proposals.", HttpStatus.BAD_REQUEST),
    FINAL_ID_LENGTH_INVALID    (4125, "The code must be between 2 and 64 characters long.", HttpStatus.BAD_REQUEST),

    // 42xx: Data Conflicts
    SUBJECT_ALREADY_EXISTS      (4201, "Subject already exists", HttpStatus.CONFLICT),
    TOPIC_ALREADY_EXISTS        (4202, "Topic already exists in this subject", HttpStatus.CONFLICT),
    SUBJECT_ID_ALREADY_EXISTS  (4203, "This subject code has already been used.", HttpStatus.CONFLICT),
    TOPIC_ID_ALREADY_EXISTS    (4204, "This topic code has already been used.", HttpStatus.CONFLICT),

    // 43xx: Suggestion Workflows
    SUGGESTION_NOT_FOUND        (4301, "Suggestion not found", HttpStatus.NOT_FOUND),
    SUGGESTION_ALREADY_SUBMITTED(4302, "A similar suggestion is already pending approval", HttpStatus.CONFLICT),
    SUGGESTION_ALREADY_PROCESSED(4303, "This suggestion has already been processed", HttpStatus.CONFLICT),

    // 9999: Catch-all Error
    UNCATEGORIZED_EXCEPTION     (9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR)
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
