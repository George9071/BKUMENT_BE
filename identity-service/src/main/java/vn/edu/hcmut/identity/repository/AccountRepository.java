package vn.edu.hcmut.identity.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    Page<Account> findAllByRolesContaining(UserRole role, Pageable pageable);
}
