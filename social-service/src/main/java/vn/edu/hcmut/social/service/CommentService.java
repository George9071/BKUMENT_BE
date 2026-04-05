package vn.edu.hcmut.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.CommentRequest;
import vn.edu.hcmut.social.dto.response.CommentResponse;
import vn.edu.hcmut.social.dto.response.ProfileResponse;
import vn.edu.hcmut.social.entity.Comment;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentService {
    CommentRepository commentRepository;
    ProfileClient profileClient;

    @Transactional
    public CommentResponse createComment(CommentRequest request, String profileId) {
        if (request.getReplyId() != null) {
            commentRepository
                    .findById(request.getReplyId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
        }

        Comment comment = Comment.builder()
                .replyId(request.getReplyId())
                .content(request.getContent())
                .resourceId(request.getResourceId())
                .profileId(profileId)
                .build();

        comment = commentRepository.save(comment);

        return toCommentResponse(comment);
    }

    @Transactional
    public void deleteComment(String commentId, String profileId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (!comment.getProfileId().equals(profileId)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        commentRepository.delete(comment);
    }

    public Page<CommentResponse> getParentCommentsByResource(String resourceId, Pageable pageable) {
        return commentRepository
                .findByResourceIdAndReplyIdIsNull(resourceId, pageable)
                .map(this::toCommentResponse);
    }

    public Page<CommentResponse> getChildComments(String replyId, Pageable pageable) {
        return commentRepository.findByReplyId(replyId, pageable).map(this::toCommentResponse);
    }

    private CommentResponse toCommentResponse(Comment comment) {
        ProfileResponse profile = profileClient.findUserProfileById(comment.getProfileId());
        CommentResponse.Author authorDto = CommentResponse.Author.builder()
                .id(profile.getId())
                .name(profile.getFullName())
                .avatarUrl(profile.getAvatarUrl())
                .build();

        int childCount = commentRepository.countByReplyId(comment.getId());

        return CommentResponse.builder()
                .id(comment.getId())
                .replyId(comment.getReplyId())
                .content(comment.getContent())
                .resourceId(comment.getResourceId())
                .author(authorDto)
                .numberOfChildComment(childCount)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
