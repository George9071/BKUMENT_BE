package vn.edu.hcmut.communication.session.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.communication.session.entity.WebSocketSession;

import java.util.List;

@Repository
public interface WebSocketSessionRepository
        extends MongoRepository<WebSocketSession, String> {

    void deleteBySocketSessionId(String socketId);

    List<WebSocketSession> findAllByUserIdIn(List<String> userIds);
}
