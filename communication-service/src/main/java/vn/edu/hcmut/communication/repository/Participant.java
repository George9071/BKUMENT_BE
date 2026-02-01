package vn.edu.hcmut.communication.repository;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "participants")
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    Conversation conversation;

    @Column(name = "profile_id")
    String profileId; // ID từ Profile Service
}
