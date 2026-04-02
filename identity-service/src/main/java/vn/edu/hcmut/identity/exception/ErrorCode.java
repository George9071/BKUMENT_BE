package vn.edu.hcmut.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // ----------------------------------------------------------------------
    // 1xxx: GENERAL VALIDATION and REQUEST ERRORS
    // ----------------------------------------------------------------------
    INVALID_KEY(1001, "Invalid message key or request parameter", HttpStatus.BAD_REQUEST),
    INVALID_ROLE(1002, "Invalid role. Accepted (MODERATOR, ADMIN, USER, TUTOR)", HttpStatus.BAD_REQUEST),
    USERNAME_LENGTH_INVALID(1003, "username must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_LENGTH_INVALID(1004, "password must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(1005, "The email format is invalid", HttpStatus.BAD_REQUEST),
    INVALID_DOB(1006, "Date of birth must be a valid date in the past", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD(1007, "This field is required and cannot be empty", HttpStatus.BAD_REQUEST),
    BIO_LENGTH_INVALID(1008, "Bio length must be not exceed {max} characters", HttpStatus.BAD_REQUEST),

    // ----------------------------------------------------------------------
    // 2xxx: IDENTITY and ACCOUNT BUSINESS LOGIC (IDENTITY SERVICE DOMAIN)
    // ----------------------------------------------------------------------
    ACCOUNT_ALREADY_EXISTS(2001, "Account already exists", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND(2002, "Account not found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(2003, "Unauthenticated access. Please log in", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(2004, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),

    // ----------------------------------------------------------------------
    // 9yxx: MICROSERVICE INTEGRATION and SYSTEM ERRORS (y is the last digit of the port number in the target service)
    // ----------------------------------------------------------------------
    DELETE_LMS_FAILED(9201, "Failed to delete LMS data during account removal", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_PROFILE_FAILED(
            9101, "Failed to delete user profile during account removal", HttpStatus.INTERNAL_SERVER_ERROR),
    PROFILE_NOT_FOUND(9102, "User profile not found in Profile Service", HttpStatus.NOT_FOUND),
    INVALID_VERIFICATION_TOKEN(400, "Token xác minh không hợp lệ hoặc đã được sử dụng", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_EXPIRED(400, "Token xác minh đã hết hạn, vui lòng yêu cầu gửi lại", HttpStatus.BAD_REQUEST),
    SYNC_FAILED(400, "Lỗi đồng bộ", HttpStatus.INTERNAL_SERVER_ERROR),

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
