package vn.edu.hcmut.email.service;

import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.email.dto.response.EmailResponse;
import vn.edu.hcmut.email.entity.EmailRequest;
import vn.edu.hcmut.email.entity.SendEmailRequest;
import vn.edu.hcmut.email.entity.Sender;
import vn.edu.hcmut.email.exception.AppException;
import vn.edu.hcmut.email.exception.ErrorCode;
import vn.edu.hcmut.email.repository.httpclient.EmailClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmailService {
    EmailClient emailClient;

    public EmailResponse sendEmail(SendEmailRequest request) {
        EmailRequest email = EmailRequest.builder()
                .sender(Sender.builder()
                        .name("BKUMENT")
                        .email("phucthanh0917@gmail.com")
                        .build())
                .to(List.of(request.getTo()))
                .subject(request.getSubject())
                .htmlContent(request.getHtmlContent())
                .build();
        try {
            log.info("Email body: {}", email);
            return emailClient.sendEmail(email);
        } catch (FeignException e){
            log.error("Feign error status: {}", e.status());
            log.error("Feign error body: {}", e.getMessage());
            throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
        }
    }
}
