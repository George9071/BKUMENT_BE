package vn.edu.hcmut.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.profile.entity.jpa.OutboxEvent;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    /**
     * Get a list of unprocessed events (processed = false)
     * Events that occurred first are sent first.
     */
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc();
}
