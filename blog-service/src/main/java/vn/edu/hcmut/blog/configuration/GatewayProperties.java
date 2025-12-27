package vn.edu.hcmut.blog.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Component
@ConfigurationProperties(prefix = "gateway")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GatewayProperties {
    String baseUrl;
    String apiPrefix;
}
