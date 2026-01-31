package vn.edu.hcmut.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import vn.edu.hcmut.identity.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    @Mapping(target = "accountId", ignore = true)
    ProfileCreationRequest toProfileCreationRequest(UserCreationRequest request);
}
