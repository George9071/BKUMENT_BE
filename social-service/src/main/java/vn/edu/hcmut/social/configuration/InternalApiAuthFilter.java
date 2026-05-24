package vn.edu.hcmut.social.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiAuthFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Internal-Api-Key";

    private final String secret;

    public InternalApiAuthFilter(@Value("${app.internal-api.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !isInternalPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(provided) || !constantTimeEquals(provided, secret)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"Internal API key invalid\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    static boolean isInternalPath(String path) {
        return StringUtils.hasText(path)
                && (path.equals("/internal") || path.startsWith("/internal/") || path.contains("/internal/"));
    }

    private static boolean constantTimeEquals(String provided, String expected) {
        if (!StringUtils.hasText(expected) || provided.length() != expected.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < provided.length(); i++) {
            result |= provided.charAt(i) ^ expected.charAt(i);
        }
        return result == 0;
    }
}
