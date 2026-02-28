package vn.edu.hcmut.profile.mapper;

import org.mapstruct.Mapper;

import vn.edu.hcmut.profile.dto.response.UniversityResponse;
import vn.edu.hcmut.profile.entity.jpa.University;

@Mapper(componentModel = "spring")
public interface UniversityMapper {
    UniversityResponse toResponse(University university);
}
