package vn.edu.hcmut.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 1xxx: GENERAL VALIDATION and REQUEST ERRORS
    INVALID_KEY                 (1001, "Invalid message key or request parameter", HttpStatus.BAD_REQUEST),
    INVALID_ROLE                (1002, "Invalid role. Accepted roles: MODERATOR, ADMIN, USER, TUTOR", HttpStatus.BAD_REQUEST),
    USERNAME_LENGTH_INVALID     (1003, "Username must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_LENGTH_INVALID     (1004, "Password must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL               (1005, "The email format is invalid", HttpStatus.BAD_REQUEST),
    INVALID_DOB                 (1006, "Date of birth must be a valid date in the past", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD              (1007, "This field is required and cannot be empty", HttpStatus.BAD_REQUEST),
    BIO_LENGTH_INVALID          (1008, "Bio length must not exceed {max} characters", HttpStatus.BAD_REQUEST),

    // 2xxx: IDENTITY and ACCOUNT BUSINESS LOGIC (IDENTITY SERVICE DOMAIN)
    ACCOUNT_ALREADY_EXISTS      (2001, "Account already exists", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND           (2002, "Account not found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED             (2003, "Unauthenticated access. Please log in", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED               (2004, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    TOO_MANY_OTP_REQUESTS       (2005, "Too many OTP requests. Please wait before requesting another.", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_VERIFICATION_TOKEN  (2006, "Invalid or already used verification token", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_EXPIRED  (2007, "Verification token has expired. Please request a new one", HttpStatus.BAD_REQUEST),
    USERNAME_ALREADY_EXISTS      (2008, "This username already exists, please choose a new one", HttpStatus.BAD_REQUEST),

    // 9yxx: MICROSERVICE INTEGRATION and SYSTEM ERRORS (y is the last digit of the port number in the target service)
    SYNC_FAILED                 (9001, "Synchronization failed", HttpStatus.INTERNAL_SERVER_ERROR),
    PROFILE_NOT_FOUND           (9102, "User profile not found in Profile Service", HttpStatus.NOT_FOUND),

    UNCATEGORIZED_EXCEPTION     (9999, "Uncategorized system error", HttpStatus.INTERNAL_SERVER_ERROR)
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
