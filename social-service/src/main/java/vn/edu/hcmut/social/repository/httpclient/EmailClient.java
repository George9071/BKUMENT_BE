package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import vn.edu.hcmut.social.dto.request.SendEmailRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;

@FeignClient(name = "email-service", url = "${app.services.email}")
public interface EmailClient {
    @PostMapping("/email/send")
    APIResponse<Object> sendEmail(@RequestBody SendEmailRequest request);
}
