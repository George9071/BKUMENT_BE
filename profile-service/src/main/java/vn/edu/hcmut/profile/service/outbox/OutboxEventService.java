package vn.edu.hcmut.profile.service.outbox;

import java.time.LocalDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.entity.jpa.OutboxEvent;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.repository.OutboxRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OutboxEventService {
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    public void save(String aggregateType, String aggregateId, String eventType, Object payloadObject) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payloadObject))
                    .processed(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Outbox serialization failed for event: {}", eventType, e);
            throw new AppException(ErrorCode.JSON_PROCESSING_EXCEPTION);
        }
    }
}
