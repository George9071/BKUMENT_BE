package vn.edu.hcmut.profile.scheduler;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.event.ProfileUpdatedEvent;
import vn.edu.hcmut.profile.entity.jpa.OutboxEvent;
import vn.edu.hcmut.profile.repository.OutboxRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private static final String PROFILE_SYNC_TOPIC = "profile-synchronize-event";
    private static final String PROFILE_UPDATE_TOPIC = "profile-update-events";
    private static final String PROFILE_UPDATED_FOR_COMMUNICATION = "PROFILE_UPDATED_FOR_COMMUNICATION";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 120000) // 2 minutes
    @Transactional
    public void publishEvents() {
        List<OutboxEvent> events = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                /* ProducerRecord (Topic, Key, Value)
                   Key: aggregateId (profileId) to ensure that events from the same user
                   always go to the same partition
                 */
                String topic = resolveTopic(event);
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        topic,
                        event.getAggregateId(),
                        resolvePayload(event)
                );

                record.headers().add(new RecordHeader(
                        "X-Event-Type",
                        event.getEventType().getBytes(StandardCharsets.UTF_8)));
                // record.headers().add(new RecordHeader("X-Aggregate-Type", event.getAggregateType().getBytes()));

                kafkaTemplate.send(record).get();
                event.setProcessed(true);
                outboxRepository.save(event);

                log.info("Successfully published event [{}] to topic [{}] for aggregateId: {}",
                        event.getEventType(),
                        topic,
                        event.getAggregateId());

            } catch (Exception e) {
                log.error("Failed to publish event to Kafka, will retry later", e);
            }
        }
    }

    private String resolveTopic(OutboxEvent event) {
        if (PROFILE_UPDATED_FOR_COMMUNICATION.equals(event.getEventType())) {
            return PROFILE_UPDATE_TOPIC;
        }

        return PROFILE_SYNC_TOPIC;
    }

    private Object resolvePayload(OutboxEvent event) throws Exception {
        if (PROFILE_UPDATED_FOR_COMMUNICATION.equals(event.getEventType())) {
            return objectMapper.readValue(event.getPayload(), ProfileUpdatedEvent.class);
        }

        return event.getPayload();
    }
}
