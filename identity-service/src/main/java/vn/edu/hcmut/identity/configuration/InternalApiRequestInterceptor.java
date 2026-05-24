package vn.edu.hcmut.identity.configuration;

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
        if (InternalApiAuthFilter.isInternalPath(normalizePath(template.path()))) {
            template.header(InternalApiAuthFilter.HEADER, secret);
        }
    }

    private static String normalizePath(String path) {
        return path == null ? "" : (path.startsWith("/") ? path : "/" + path);
    }
}
