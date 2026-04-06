package vn.edu.hcmut.lms.mapper;

import java.util.ArrayList;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.response.ApplicationResponse;
import vn.edu.hcmut.lms.entity.TutorApplication;

@Mapper(componentModel = "spring")
public interface TutorApplicationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profileId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    TutorApplication toEntity(TutorRegistrationRequest request);

    ApplicationResponse toResponse(TutorApplication application);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profileId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(TutorRegistrationRequest request,
                                 @MappingTarget TutorApplication application);

    @AfterMapping
    default void handleSubjectIds(TutorRegistrationRequest request,
                                  @MappingTarget TutorApplication application) {

        if (request.getSubjectIds() != null) {
            application.setSubjectIds(new ArrayList<>(request.getSubjectIds()));
        }
    }
}