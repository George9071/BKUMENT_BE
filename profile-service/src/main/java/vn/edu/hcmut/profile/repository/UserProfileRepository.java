package vn.edu.hcmut.profile.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.profile.entity.jpa.UserProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByAccountId(String accountId);

    @Query("SELECT p FROM UserProfile p WHERE "
            + "LOWER(function('f_unaccent', CONCAT(p.lastName, ' ', p.firstName))) LIKE LOWER(function('f_unaccent', CONCAT('%', :keyword, '%'))) OR "
            + "LOWER(function('f_unaccent', CONCAT(p.firstName, ' ', p.lastName))) LIKE LOWER(function('f_unaccent', CONCAT('%', :keyword, '%'))) OR "
            + "LOWER(p.email) LIKE LOWER(CONCAT(:keyword, '%')) OR "
            + "p.phone LIKE CONCAT('%', :keyword, '%')")
    Page<UserProfile> search(@Param("keyword") String keyword, Pageable pageable);
}
