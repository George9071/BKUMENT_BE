package vn.edu.hcmut.lms.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.entity.Enrollment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {
    @Mapping(target = "studentId", source = "enrollment.studentProfileId")
    @Mapping(target = "studentName", expression = "java(profile != null ? profile.getLastName() + \" \" + profile.getFirstName() : null)")
    @Mapping(target = "studentEmail", source = "profile.email")
    @Mapping(target = "studentAvatar", source = "profile.avatarUrl")
    @Mapping(target = "id", source = "enrollment.id")
    @Mapping(target = "status", source = "enrollment.status")
    @Mapping(target = "enrolledAt", source = "enrollment.enrolledAt")
    EnrollmentResponse toResponse(Enrollment enrollment, ProfileResponse profile);

    default List<EnrollmentResponse> toResponseList(
            List<Enrollment> enrollments,
            Map<String, ProfileResponse> profileMap) {
        if (enrollments == null) return new ArrayList<>();
        return enrollments.stream()
                .map(enrollment -> {
                    ProfileResponse profile = profileMap.get(enrollment.getStudentProfileId());
                    return toResponse(enrollment, profile);
                })
                .collect(Collectors.toList());
    }
}
