package vn.edu.hcmut.identity.configuration;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@Component
public class AuthenticationRequestInterceptor implements RequestInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletAttributes)) {
            log.debug("No servlet request context found. Authorization header will not be forwarded.");
            return;
        }

        String authHeader = servletAttributes.getRequest().getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader)) {
            template.header(AUTHORIZATION_HEADER, authHeader);
        }
    }
}
