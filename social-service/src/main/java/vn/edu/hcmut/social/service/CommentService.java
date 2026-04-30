package vn.edu.hcmut.social.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.CommentRequest;
import vn.edu.hcmut.social.dto.response.CommentResponse;
import vn.edu.hcmut.social.dto.response.ProfileResponse;
import vn.edu.hcmut.social.entity.Comment;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.httpclient.ProfileClient;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentService {
    CommentRepository commentRepository;
    ProfileClient profileClient;

    /**
     * Creates a new comment.
     * * *
     * Replying to a reply is rejected — the schema only supports 2 levels.
     * @param request   payload (content, resourceId, optional replyId)
     * @param profileId the authenticated user's profile ID (from JWT)
     */
    @Transactional
    public CommentResponse createComment(CommentRequest request, String profileId) {

        if (request.getReplyId() != null) {
            Comment parent = commentRepository
                    .findById(request.getReplyId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

            if (parent.getReplyId() != null) throw new AppException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        }

        Comment comment = Comment.builder()
                .replyId(request.getReplyId())
                .content(request.getContent())
                .resourceId(request.getResourceId())
                .profileId(profileId)
                .build();

        comment = commentRepository.save(comment);

         ProfileResponse profile = getProfile(profileId);

         return toCommentResponse(comment, profile, 0);
    }

    /**
     * Deletes a comment authored by the requesting user, cascading to any child replies.
     * @param commentId the comment to delete
     * @param profileId the authenticated user's profile ID (must own the comment)
     */
    @Transactional
    public void deleteComment(String commentId, String profileId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!comment.getProfileId().equals(profileId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        // Cascade delete: only top-level comments have children to clean up.
        if (comment.getReplyId() == null) commentRepository.deleteByReplyId(commentId);

        commentRepository.delete(comment);
    }

    /**
     * Returns top-level comments for a resource, fully enriched in one pass.
     */
    public Page<CommentResponse> getParentCommentsByResource(String resourceId, Pageable pageable) {
        Page<Comment> page = commentRepository.findByResourceIdAndReplyIdIsNull(resourceId, pageable);
        return enrichPage(page);
    }

    public Page<CommentResponse> getChildComments(String replyId, Pageable pageable) {
        Page<Comment> page = commentRepository.findByReplyId(replyId, pageable);
        return enrichPage(page);
    }

    private Page<CommentResponse> enrichPage(Page<Comment> page) {
        if (page.isEmpty()) return page.map(c -> null);

        List<Comment> comments = page.getContent();

        // Batch fetch profiles for all distinct authors on the page.
        List<String> profileIds = comments.stream()
                .map(Comment::getProfileId)
                .distinct()
                .toList();
        Map<String, ProfileResponse> profileMap = getProfileMap(profileIds);

        List<String> commentIds = comments.stream().map(Comment::getId).toList();
        Map<String, Integer> replyCountMap = fetchReplyCountMap(commentIds);

        return page.map(c -> toCommentResponse(
                c,
                profileMap.get(c.getProfileId()),
                replyCountMap.getOrDefault(c.getId(), 0)));
    }

    /**
     * Batch-fetches author profiles.
     */
    private Map<String, ProfileResponse> getProfileMap(List<String> profileIds) {
        if (profileIds.isEmpty()) return Collections.emptyMap();

        return profileClient.getProfiles(profileIds).stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));
    }

    private ProfileResponse getProfile(String profileId) {
        try {
            return profileClient.findUserProfileById(profileId);
        } catch (Exception e) {
            log.warn("Failed to fetch profile {}: {}", profileId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns commentId -> replyCount for the given comments.
     * Only parent IDs that have at least one reply appear in the result
     */
    private Map<String, Integer> fetchReplyCountMap(List<String> parentIds) {
        if (parentIds.isEmpty()) return Collections.emptyMap();

        List<Object[]> rows = commentRepository.countRepliesGroupedByParentId(parentIds);
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            String parentId = (String) row[0];
            Long count = (Long) row[1];
            map.put(parentId, count.intValue());
        }
        return map;
    }

    private CommentResponse toCommentResponse(Comment comment, ProfileResponse profile, int replyCount) {
        CommentResponse.Author authorDto = null;
        if (profile != null) {
            authorDto = CommentResponse.Author.builder()
                    .id(profile.getId())
                    .name(profile.getFullName())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .replyId(comment.getReplyId())
                .content(comment.getContent())
                .resourceId(comment.getResourceId())
                .author(authorDto)
                .numberOfChildComment(replyCount)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
