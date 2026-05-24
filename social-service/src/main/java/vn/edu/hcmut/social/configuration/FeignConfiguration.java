package vn.edu.hcmut.social.configuration;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class FeignConfiguration {
    private final String internalApiSecret;

    public FeignConfiguration(@Value("${app.internal-api.secret}") String internalApiSecret) {
        this.internalApiSecret = internalApiSecret;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                String tokenValue = jwtAuthenticationToken.getToken().getTokenValue();
                requestTemplate.header("Authorization", "Bearer " + tokenValue);
            }

            if (isInternalRequest(requestTemplate.path()) && internalApiSecret != null && !internalApiSecret.isBlank()) {
                requestTemplate.header(InternalApiAuthFilter.HEADER, internalApiSecret);
            }
        };
    }

    private boolean isInternalRequest(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return InternalApiAuthFilter.isInternalPath(normalizedPath);
    }
}
