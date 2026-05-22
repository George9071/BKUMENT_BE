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
        if (template.path().startsWith("/internal/")) {
            template.header(InternalApiAuthFilter.HEADER, secret);
        }
    }
}
