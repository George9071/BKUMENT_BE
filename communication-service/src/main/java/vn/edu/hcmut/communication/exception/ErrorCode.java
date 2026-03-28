package vn.edu.hcmut.communication.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1003, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN_CLAIMS(9001, "Invalid token", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED_ACTION(9005, "unauthorized action", HttpStatus.UNAUTHORIZED),
    SYNC_FAILED(9998, "An error occurred during the synchronization process", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),

    USER_NOT_FOUND(5001, "User not found", HttpStatus.NOT_FOUND),
    CONVERSATION_NOT_FOUND(5002, "Conversation not found", HttpStatus.NOT_FOUND),
    INVALID_PARTICIPANT(5003, "Invalid participant", HttpStatus.BAD_REQUEST),
    INVALID_DIRECT_CHAT_MEMBERS(5004, "Invalid number participant for direct chat", HttpStatus.BAD_REQUEST),
    INVALID_MESSAGE_PAYLOAD(5005, "invalid message payload", HttpStatus.BAD_REQUEST),
    NOTIFICATION_NOT_FOUND(5006, "Notification not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED(5007, "access denied", HttpStatus.UNAUTHORIZED),
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
