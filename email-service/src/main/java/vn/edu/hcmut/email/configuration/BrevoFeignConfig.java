package vn.edu.hcmut.email.configuration;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@Slf4j
public class BrevoFeignConfig {

    @Value("${notification.email.brevo-apikey}")
    private String apiKey;

    @Bean
    public RequestInterceptor brevoInterceptor() {
        return requestTemplate -> {
            if (apiKey != null && !apiKey.isBlank()) {
                requestTemplate.header("api-key", apiKey.trim());
            } else {
                log.error("CẢNH BÁO NGHIÊM TRỌNG: key đang bị TRỐNG!");
            }
        };
    }
}
