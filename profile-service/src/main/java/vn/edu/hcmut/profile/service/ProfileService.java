package vn.edu.hcmut.profile.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.Profile;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.ProfileRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileService {
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;

    public ProfileResponse createProfile(ProfileCreationRequest request) {
        Profile profile = profileMapper.toProfile(request);
        profile = profileRepository.save(profile);

        return profileMapper.toProfileResponse(profile);
    }

    public ProfileResponse getProfile(String id) {
        Profile profile = profileRepository.findById(id).orElseThrow(() -> new RuntimeException("Profile not found"));

        return profileMapper.toProfileResponse(profile);
    }

    public ProfileResponse getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String accountId = authentication.getName();
        log.info("accountId: {}", accountId);

        Profile profile = profileRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Profile not found for account: " + accountId));

        return profileMapper.toProfileResponse(profile);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<ProfileResponse> getAllProfiles() {
        var profiles = profileRepository.findAll();

        return profiles.stream().map(profileMapper::toProfileResponse).toList();
    }
}
