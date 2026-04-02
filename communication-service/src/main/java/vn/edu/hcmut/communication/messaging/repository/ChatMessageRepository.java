package vn.edu.hcmut.communication.messaging.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.communication.messaging.entity.ChatMessage;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    Page<ChatMessage> findAllByConversationIdOrderByCreatedDateDesc(String conversationId, Pageable pageable);
}
