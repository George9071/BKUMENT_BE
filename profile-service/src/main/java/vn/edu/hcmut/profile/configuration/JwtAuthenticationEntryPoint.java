package vn.edu.hcmut.profile.configuration;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.exception.ErrorCode;

/**
 * Custom authentication entry point to handle unauthorized access attempts.
 * This component intercepts requests that lack valid authentication credentials (e.g., missing or invalid JWT)
 * and returns a standardized JSON response instead of the default Spring Security behavior.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Triggered automatically by Spring Security when an unauthenticated user attempts
     * to access a secured HTTP resource.
     *
     * @param request           The HTTP request that resulted in an AuthenticationException.
     * @param response          The HTTP response to be sent back to the client.
     * @param authException     The exception that caused the invocation.
     * @throws IOException      If an input or output exception occurs during response writing.
     */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;

        response.setStatus(errorCode.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Construct the standardized API response payload
        APIResponse<?> apiResponse = APIResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        // Serialize the Java object and write it directly to the response body
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }
}
