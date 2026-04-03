package vn.edu.hcmut.email.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.hcmut.email.configuration.BrevoFeignConfig;
import vn.edu.hcmut.email.dto.response.EmailResponse;
import vn.edu.hcmut.email.entity.EmailRequest;

@FeignClient(name = "email-client", url = "${notification.email.brevo-url}", configuration = BrevoFeignConfig.class)
public interface EmailClient {
    @PostMapping(value = "/v3/smtp/email",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    EmailResponse sendEmail(@RequestBody EmailRequest body);
}
