package vn.edu.hcmut.identity.mapper;

import org.mapstruct.Mapper;

import vn.edu.hcmut.identity.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    ProfileCreationRequest toProfileCreationRequest(UserCreationRequest request);
}
