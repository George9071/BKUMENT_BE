package vn.edu.hcmut.profile.service;

import java.util.*;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.entity.neo4j.UniversityNode;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileNodeRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileService {
    ProfileMapper profileMapper;
    UserProfileRepository jpaRepository;
    UserProfileNodeRepository neo4jRepository;
    UniversityRepository universityRepository;
    Neo4jClient neo4jClient;

    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse createProfile(ProfileCreationRequest request) {
        String profileId = UUID.randomUUID().toString();

        var university = universityRepository
                .findById(request.getUniversityId())
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

        UniversityNode uniNode = UniversityNode.builder()
                .id(university.getId())
                .name(university.getName())
                .abbreviation(university.getAbbreviation())
                .build();

        UserProfile user = profileMapper.toProfile(request);
        user.setId(profileId);
        user.setUniversityId(university.getId());
        jpaRepository.save(user);

        UserProfileNode userNode = UserProfileNode.builder()
                .id(profileId)
                .fullName(request.getFirstName() + " " + request.getLastName())
                .roles(List.of("STUDENT")) // Default role
                .university(uniNode)
                .build();
        try {
            neo4jRepository.save(userNode);
        } catch (Exception e) {
            log.error("Error saving to Neo4j for profile: {}", profileId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        var response = profileMapper.toProfileResponse(user);
        response.setUniversity(university.getName());
        return response;
    }

    public ProfileResponse getProfile(String id) {
        log.info("profile_id: {}", id);
        UserProfile user = jpaRepository.findById(id).orElseThrow(() -> new RuntimeException("Profile not found"));
        return profileMapper.toProfileResponse(user);
    }

    public ProfileResponse getProfileByAccountId(String accountId) {
        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        var response = profileMapper.toProfileResponse(user);

        if (user.getUniversityId() != null) {
            universityRepository
                    .findById(user.getUniversityId())
                    .ifPresent(uni -> response.setUniversity(uni.getName()));
        }

        return response;
    }

    public ProfileResponse getMyProfile() {
        var context = SecurityContextHolder.getContext();
        String accountId = context.getAuthentication().getName();

        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new RuntimeException("Profile not found for account: " + accountId));

        return profileMapper.toProfileResponse(user);
    }

    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        var context = SecurityContextHolder.getContext();
        String accountId = context.getAuthentication().getName();

        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        profileMapper.updateProfile(user, request);

        University uni = null;
        if (request.getUniversityId() != null) {
            uni = universityRepository
                    .findById(request.getUniversityId())
                    .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

            user.setUniversityId(uni.getId());
            final University lambda = uni;

            neo4jRepository.findById(user.getId()).ifPresent(node -> {
                UniversityNode uniNode = UniversityNode.builder()
                        .id(lambda.getId())
                        .name(lambda.getName())
                        .abbreviation(lambda.getAbbreviation())
                        .build();

                node.setUniversity(uniNode);
                node.setNotNew();
            });
        }

        jpaRepository.save(user);
        var response = profileMapper.toProfileResponse(user);

        if (uni != null) {
            response.setUniversity(uni.getName());
        } else if (user.getUniversityId() != null) {
            universityRepository
                    .findById(user.getUniversityId())
                    .ifPresent(university -> response.setUniversity(university.getName()));
        }

        return response;
    }

    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void deleteProfile(String profileId){
        UserProfile user = jpaRepository
                .findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        jpaRepository.delete(user);

        String query = """
                MATCH (u:UserProfile {id: $profileId})
                DETACH DELETE u
                """;

        neo4jClient.query(query)
                .bindAll(Map.of("profileId", profileId))
                .run();

        log.info("Deleted UserProfile {} and all its relationships in Neo4j", profileId);
    }

    public void addRole(String profileId, String role) {
        neo4jRepository.findById(profileId).ifPresent(node -> {
            List<String> currentRoles = node.getRoles();

            if (currentRoles == null) currentRoles = new ArrayList<>();

            if (!currentRoles.contains(role)) {
                currentRoles.add(role);
                node.setRoles(currentRoles);
                node.setNotNew();
                neo4jRepository.save(node);
            }
        });
    }

    // BATCH GET
    public List<ProfileResponse> getProfilesByIds(List<String> profileIds) {
        return jpaRepository.findAllById(profileIds).stream()
                .map(profileMapper::toProfileResponse)
                .toList();
    }

    @Transactional
    public void updateTutorSubjects(String profileId, Set<String> subjectIds) {
        String query =
                """
				MATCH (u:UserProfile {id: $profileId})

				OPTIONAL MATCH (u)-[r:TEACHES]->()
				DELETE r

				WITH u
				UNWIND $subjectIds AS subId
				MATCH (s:Subject {id: subId})
				MERGE (u)-[:TEACHES]->(s)
				""";

        neo4jClient
                .query(query)
                .bindAll(Map.of(
                        "profileId", profileId,
                        "subjectIds", subjectIds))
                .run();
    }
}
