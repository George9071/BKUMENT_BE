package vn.edu.hcmut.communication.messaging.event;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdatedEvent {
    private String profileId;
    private String firstName;
    private String lastName;
    private String avatar;
    private LocalDate dob;
    private String bio;
    private String address;
    private String gender;
    private String phone;
}
