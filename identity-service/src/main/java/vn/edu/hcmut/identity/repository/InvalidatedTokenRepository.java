package vn.edu.hcmut.identity.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.identity.entity.InvalidatedToken;

import java.util.Date;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {
    /**
     * Deletes all invalidated-token rows whose expiry time is before the given cutoff.
     * Returns the number of rows deleted
     * .
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM InvalidatedToken t WHERE t.expiryTime < :cutoff")
    int deleteByExpiryTimeBefore(@Param("cutoff") Date cutoff);
}
