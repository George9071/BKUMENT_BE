package vn.edu.hcmut.identity.exception;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.hcmut.identity.dto.response.APIResponse;

/**
 * Global centralized exception handler for the application.
 * Intercepts exceptions thrown by controllers and translates them into a standardized APIResponse format.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Catches all unhandled/unexpected exceptions
     * @param exception The caught general exception.
     * @return Standardized APIResponse with an INTERNAL_SERVER_ERROR (500) status.
     */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<APIResponse<?>> handlingRuntimeException(RuntimeException exception) {
        log.error("Exception: ", exception);
        APIResponse<?> apiResponse = new APIResponse<>();

        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
    }

    /**
     * Catches custom application-specific exceptions.
     * @param exception The custom AppException containing a specific ErrorCode.
     * @return Standardized APIResponse mapping to the ErrorCode's defined HTTP status.
     */
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<APIResponse<?>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        APIResponse<?> apiResponse = new APIResponse<>();

        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    /**
     * Catches Spring Security access denial exceptions (e.g., user lacks required roles).
     * @param exception The Spring Security AccessDeniedException.
     * @return Standardized APIResponse with a FORBIDDEN (403) status.
     */
    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<APIResponse<?>> handlingAccessDeniedException(AccessDeniedException exception) {
        log.warn("Access denied error intercepted: {}", exception.getMessage());

        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(APIResponse.builder().code(errorCode.getCode()).message(errorCode.getMessage()).build());
    }

    /**
     * Catches validation errors from request bodies (e.g., @Valid DTOs).
     * Dynamically formats the error message if constraint attributes (like {min}) are present.
     * @param exception The validation exception containing field errors.
     * @return Standardized APIResponse with a BAD_REQUEST (400) status.
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<?>> handlingValidation(MethodArgumentNotValidException exception) {
        // Retrieve the validation message key (which should map to an ErrorCode enum name)
        String enumKey = Optional.ofNullable(exception.getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.UNCATEGORIZED_EXCEPTION.name()); // Global Error

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;

        try {
            errorCode = ErrorCode.valueOf(enumKey);

            if (!exception.getBindingResult().getAllErrors().isEmpty()) {
                // Extract constraint violation details to get dynamic attributes (e.g., @Size(min = 5))
                ConstraintViolation<?> constraintViolation = exception.getBindingResult()
                        .getAllErrors().get(0)
                        .unwrap(ConstraintViolation.class);

                attributes = constraintViolation.getConstraintDescriptor().getAttributes();
                log.info("Validation attributes extracted: {}", attributes);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Validation error key '{}' does not match any ErrorCode enum", enumKey);
        }

        APIResponse<?> body = new APIResponse<>();
        body.setCode(errorCode.getCode());
        body.setMessage(
                Objects.nonNull(attributes)
                        ? mapAttribute(errorCode.getMessage(), attributes)
                        : errorCode.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseEntity<APIResponse<?>> handlingConstraintViolation(ConstraintViolationException ex) {
        var violation = ex.getConstraintViolations().iterator().next();
        String enumKey = violation.getMessage();

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
        } catch (IllegalArgumentException e) {
            log.warn("Validation key {} not found", enumKey);
        }

        APIResponse<?> response = new APIResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.builder().code(ErrorCode.INVALID_ROLE.getCode()).message(ex.getMessage()).build());
        }
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse<?>> handleJsonParseError(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.builder().code(ErrorCode.INVALID_ROLE.getCode()).message(ex.getMessage()).build());
        }
        return ResponseEntity.badRequest().build();
    }

    /**
     * Helper method to inject constraint attributes into the error message template.
     * @param message    The raw error message template.
     * @param attributes The map of constraint attributes.
     * @return The formatted error message.
     */
    private String mapAttribute(String message, Map<String, Object> attributes) {
        String finalMessage = message;

        // Loop through all attributes provided by the constraint (e.g., "min" -> 6, "max" -> 500)
        for (var entry : attributes.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}"; // Example: "{max}, {min}"

            // If the message template contains this placeholder, replace it with the actual value
            if (finalMessage.contains(placeholder)) {
                finalMessage = finalMessage.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        return finalMessage;
    }
}
