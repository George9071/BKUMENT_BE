package vn.edu.hcmut.identity.configuration;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@Component
public class AuthenticationRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (!Objects.isNull(attributes)) {
            var request = attributes.getRequest();
            var authHeader = request.getHeader("Authorization");

            log.info("authHeader:{}", authHeader);
            if (StringUtils.hasText(authHeader)) template.header("Authorization", authHeader);
        } else {
            log.warn("RequestContextHolder returned null. No active HTTP request context found.");
        }
    }
}
