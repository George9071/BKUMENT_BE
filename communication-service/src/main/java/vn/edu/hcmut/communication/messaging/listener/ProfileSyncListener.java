package vn.edu.hcmut.communication.messaging.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.communication.messaging.entity.Conversation;
import vn.edu.hcmut.event.dto.ProfileUpdatedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileSyncListener {
    private final MongoTemplate mongoTemplate;

    @KafkaListener(topics = "profile-events", groupId = "communication-group")
    public void onProfileUpdated(ProfileUpdatedEvent event) {
        log.info("Received profile update event for user: {}", event.getProfileId());
        log.info("Received payload: last_name: {}, first_name: {}, avatar: {}",
                event.getFirstName(), event.getLastName(), event.getAvatar());
        Query query = new Query(Criteria.where("participants.userId").is(event.getProfileId()));

        Update update = new Update()
                .set("participants.$[elem].firstName", event.getFirstName())
                .set("participants.$[elem].lastName", event.getLastName())
                .set("participants.$[elem].avatar", event.getAvatar());

        update.filterArray(Criteria.where("elem.userId").is(event.getProfileId()));

        var result = mongoTemplate.updateMulti(query, update, Conversation.class);

        log.info("Successfully updated participant info in {} conversations.", result.getModifiedCount());
    }

}
