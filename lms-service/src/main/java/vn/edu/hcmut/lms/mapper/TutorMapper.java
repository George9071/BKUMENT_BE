package vn.edu.hcmut.lms.mapper;

import org.mapstruct.*;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.entity.Tutor;

@Mapper(componentModel = "spring")
public interface TutorMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isNew", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    Tutor toTutor(TutorRegistrationRequest request);

    TutorResponse toResponse(Tutor tutor);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    void updateTutor(@MappingTarget Tutor tutor, TutorUpdateRequest request);
}
