package vn.edu.hcmut.profile.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.service.ProfileNeo4jService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileSynchronizeConsumer {
    private final ProfileNeo4jService profileNeo4jService;
    private final UniversityRepository universityRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "profile-synchronize-event", groupId = "profile-neo4j-sync")
    public void consumeProfileEvents(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String profileId = record.key();
        String payload = record.value();

        try {
            var eventTypeHeader = record.headers().lastHeader("X-Event-Type");
            if (eventTypeHeader == null || eventTypeHeader.value() == null) {
                log.warn("Missing X-Event-Type header for profileId: {}", profileId);
                ack.acknowledge();
                return;
            }

            String eventType = new String(eventTypeHeader.value(), StandardCharsets.UTF_8).trim().toUpperCase();
            log.info("Consuming event [{}] from Kafka for profileId: {}", eventType, profileId);

            switch (eventType) {
                case "PROFILE_CREATED":
                    ProfileCreationRequest request = objectMapper.readValue(
                            payload,
                            ProfileCreationRequest.class);

                    University university = universityRepository
                            .findById(request.getUniversityId())
                            .orElse(null);

                    profileNeo4jService.createUserNode(profileId, request, university);
                    break;

                case "PROFILE_UPDATED":
                    ProfileUpdateRequest updateRequest = objectMapper.readValue(payload, ProfileUpdateRequest.class);

                    University updatedUniversity = null;
                    if (updateRequest.getUniversityId() != null) {
                        updatedUniversity = universityRepository
                                .findById(updateRequest.getUniversityId())
                                .orElse(null);
                    }

                    profileNeo4jService.updateUserNode(profileId, updateRequest, updatedUniversity);
                    break;

                case "PROFILE_DELETED":
                    profileNeo4jService.deleteUserNode(profileId);
                    break;

                case "USER_FOLLOWED":
                    Map<String, String> followData = objectMapper.readValue(payload, new TypeReference<>() {});

                    String followerId = followData.get("followerId");
                    String followeeId = followData.get("followeeId");

                    profileNeo4jService.createFollowRelationship(followerId, followeeId);

                    // TODO
                    // send a Kafka message to the 'follow-events' topic to have the
                    // Notification Service send a notification that "{follower name} has started following you".
                    // kafkaTemplate.send("follow-events", event);
                    break;

                case "USER_UNFOLLOWED":
                    Map<String, String> unfollowData = objectMapper.readValue(payload, new TypeReference<>() {});
                    profileNeo4jService.removeFollowRelationship(
                            unfollowData.get("followerId"),
                            unfollowData.get("followeeId")
                    );
                    break;

                default:
                    log.warn("Received unsupported event type [{}] for profileId: {}", eventType, profileId);
                    break;
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error occurred while processing Kafka message for profileId: {}. " +
                    "Message will not be acknowledged.", profileId, e);
        }
    }
}
