package vn.edu.hcmut.lms.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.entity.Enrollment;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {
    @Mapping(target = "id", source = "enrollment.id")
    @Mapping(target = "status", source = "enrollment.status")
    @Mapping(target = "enrolledAt", source = "enrollment.enrolledAt")
    @Mapping(target = "studentId", source = "enrollment.studentProfileId")

    // Map thông tin từ ProfileResponse
    @Mapping(target = "studentName", expression = "java(profile != null ? profile.getLastName() + \" \" + profile.getFirstName() : null)")
    @Mapping(target = "studentEmail", source = "profile.email")
    @Mapping(target = "studentAvatar", source = "profile.avatarUrl")
    EnrollmentResponse toResponse(Enrollment enrollment, ProfileResponse profile);
}
