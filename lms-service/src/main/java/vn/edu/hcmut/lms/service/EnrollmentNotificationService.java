package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.event.EnrollmentNotificationEvent;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentNotificationService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEnrollmentRequested(String classId, String className,
                                        String studentId, String studentName,
                                        String tutorId) {
        send(EnrollmentNotificationEvent.builder()
                .action("REQUESTED")
                .classId(classId).className(className)
                .studentId(studentId).studentName(studentName)
                .tutorId(tutorId).timestamp(Instant.now())
                .build(), tutorId);
    }

    public void sendEnrollmentDecision(String classId, String className,
                                       String studentId, String tutorId,
                                       boolean isApproved) {
        send(EnrollmentNotificationEvent.builder()
                .action(isApproved ? "APPROVED" : "REJECTED")
                .classId(classId).className(className)
                .studentId(studentId).tutorId(tutorId)
                .timestamp(Instant.now())
                .build(), studentId);
    }

    private void send(EnrollmentNotificationEvent event, String partitionKey) {
        try {
            kafkaTemplate.send("enrollment-events", partitionKey, event);
        } catch (Exception e) {
            log.error("Failed to send notification event [{}] for key {}",
                    event.getAction(), partitionKey, e);
        }
    }
}
