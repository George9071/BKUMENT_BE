package vn.edu.hcmut.notification.service;

import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.notification.dto.response.EmailResponse;
import vn.edu.hcmut.notification.entity.EmailRequest;
import vn.edu.hcmut.notification.entity.SendEmailRequest;
import vn.edu.hcmut.notification.entity.Sender;
import vn.edu.hcmut.notification.exception.AppException;
import vn.edu.hcmut.notification.exception.ErrorCode;
import vn.edu.hcmut.notification.repository.httpclient.EmailClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailService {
    EmailClient emailClient;

    String APIKey = "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";

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
            return emailClient.sendEmail(APIKey, email);
        } catch (FeignException e){
            throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
        }
    }
}
