package vn.edu.hcmut.resource.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MinioProperties {
    String endpoint;
    String externalEndpoint;
    String accessKey;
    String secretKey;
    String bucketName;
    long maxFileSize;
    boolean secure;
}
