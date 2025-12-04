package vn.edu.hcmut.profile.mapper;

import org.mapstruct.Mapper;

import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.Profile;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    Profile toProfile(ProfileCreationRequest request);

    ProfileResponse toProfileResponse(Profile entity);
}
