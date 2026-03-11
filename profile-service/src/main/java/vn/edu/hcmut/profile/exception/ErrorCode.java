package vn.edu.hcmut.profile.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // ----------------------------------------------------------------------
    // 1xxx: VALIDATION ERRORS
    // ----------------------------------------------------------------------
    INVALID_KEY(1001, "Invalid request parameter", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD(1002, "This field is required and cannot be empty", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(1003, "The email format is invalid", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(1004, "Phone number must be between 10 and 11 digits", HttpStatus.BAD_REQUEST),
    INVALID_DOB(1005, "Date of birth must be a valid date in the past", HttpStatus.BAD_REQUEST),
    BIO_LENGTH_INVALID(1006, "Bio length must not exceed {max} characters", HttpStatus.BAD_REQUEST),

    // ----------------------------------------------------------------------
    // 2xxx: AUTHENTICATION and AUTHORIZATION
    // ----------------------------------------------------------------------
    UNAUTHENTICATED(2001, "Unauthenticated access. Please log in", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(2002, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),

    // ----------------------------------------------------------------------
    // 3xxx: PROFILE BUSINESS LOGIC
    // ----------------------------------------------------------------------
    ACCOUNT_ALREADY_EXISTS(3001, "Account already exists", HttpStatus.BAD_REQUEST), // Fixed grammar
    ACCOUNT_NOT_FOUND(3002, "Account not found", HttpStatus.NOT_FOUND), // Fixed grammar
    UNIVERSITY_NOT_FOUND(3003, "University not found", HttpStatus.NOT_FOUND),
    PROFILE_NOT_FOUND(3004, "Profile not found", HttpStatus.NOT_FOUND),
    CANNOT_FOLLOW_YOURSELF(3005, "Profile not found", HttpStatus.BAD_REQUEST),

    // ----------------------------------------------------------------------
    // 9xxx: SYSTEM ERRORS
    // ----------------------------------------------------------------------
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized system error", HttpStatus.INTERNAL_SERVER_ERROR);

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
