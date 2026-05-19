package vn.edu.hcmut.identity.entity;

import java.time.Instant;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Entity
@Table(name = "verification_token",
        indexes = {
                @Index(name = "idx_vt_token_type_used", columnList = "token,type,used"),
                @Index(name = "idx_vt_account_type", columnList = "accountId,type")
        })
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false)
    String token;

    @Column(nullable = false)
    String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TokenType type;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    boolean used;

    public enum TokenType { EMAIL_VERIFICATION, PASSWORD_RESET }
}
