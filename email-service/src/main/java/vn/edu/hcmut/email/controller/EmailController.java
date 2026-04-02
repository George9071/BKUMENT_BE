package vn.edu.hcmut.email.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.email.dto.response.APIResponse;
import vn.edu.hcmut.email.dto.response.EmailResponse;
import vn.edu.hcmut.email.entity.SendEmailRequest;
import vn.edu.hcmut.email.service.EmailService;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailController {
    EmailService emailService;

    @PostMapping("/email/send")
    APIResponse<EmailResponse> sendEmail(@RequestBody SendEmailRequest request) {
        return APIResponse.<EmailResponse>builder()
                .result(emailService.sendEmail(request))
                .build();
    }
}
