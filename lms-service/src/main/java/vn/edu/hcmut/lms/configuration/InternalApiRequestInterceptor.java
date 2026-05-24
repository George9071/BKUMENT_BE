package vn.edu.hcmut.lms.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalApiRequestInterceptor implements RequestInterceptor {
    private final String secret;

    public InternalApiRequestInterceptor(@Value("${app.internal-api.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (isInternalPath(template.path())) {
            template.header(InternalApiAuthFilter.HEADER, secret);
        }
    }

    private static boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedPath.equals("/internal")
                || normalizedPath.startsWith("/internal/")
                || normalizedPath.contains("/internal/");
    }
}
