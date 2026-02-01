package vn.edu.hcmut.profile.mapper;

import org.mapstruct.*;

import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "points", constant = "0L")
    UserProfile toProfile(ProfileCreationRequest request);

    @Mapping(target = "fullName", expression = "java(entity.getFirstName() + \" \" + entity.getLastName())")
    ProfileResponse toProfileResponse(UserProfile entity);

    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProfile(@MappingTarget UserProfile entity, ProfileUpdateRequest request);
}
