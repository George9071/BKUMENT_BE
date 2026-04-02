package vn.edu.hcmut.email.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.email.entity.Recipient;
import vn.edu.hcmut.email.entity.SendEmailRequest;
import vn.edu.hcmut.email.service.EmailService;
import vn.edu.hcmut.event.EmailSendEvent;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailDeliveryListener {
    EmailService emailService;

    @KafkaListener(topics = "email-delivery", groupId = "email-group")
    public void listenEmailDelivery(EmailSendEvent message){
        try {
            log.info("Message received for email delivery: {}", message);

            emailService.sendEmail(SendEmailRequest.builder()
                    .to(Recipient.builder().email(message.getRecipient()).build())
                    .subject(message.getSubject())
                    .htmlContent(message.getBody())
                    .build());

        } catch (Exception e) {
            log.error("Gửi mail thất bại cho {}: {}", message.getRecipient(), e.getMessage());
        }
    }

}
