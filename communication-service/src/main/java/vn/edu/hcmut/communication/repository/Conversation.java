package vn.edu.hcmut.communication.repository;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String name;

    boolean isGroup;

    @OneToMany(mappedBy = "conversation")
    List<Participant> participants;
}
