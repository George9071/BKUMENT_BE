package vn.edu.hcmut.identity.entity;

import java.time.Instant;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "verification_token")
public class VerificationToken {
    @Id
    String token;

    @Column(nullable = false)
    String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TokenType type; // EMAIL_VERIFICATION | PASSWORD_RESET

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    boolean used;

    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
}
