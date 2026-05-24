package vn.edu.hcmut.social.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.CommentRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.CommentResponse;
import vn.edu.hcmut.social.service.CommentService;
import vn.edu.hcmut.social.utils.SecurityUtils;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Comment", description = "Comment APIs for resources")
public class CommentController {
    CommentService commentService;
    SecurityUtils securityUtils;

    @PostMapping
    public APIResponse<CommentResponse> createComment(@RequestBody @Valid CommentRequest request) {
        String profileId = securityUtils.getProfileId();
        return APIResponse.<CommentResponse>builder()
                .result(commentService.createComment(request, profileId))
                .message("Comment created successfully")
                .build();
    }

    @DeleteMapping("/{commentId}")
    public APIResponse<String> deleteComment(@PathVariable String commentId) {
        String profileId = securityUtils.getProfileId();
        commentService.deleteComment(commentId, profileId);
        return APIResponse.<String>builder()
                .message("Comment deleted successfully")
                .build();
    }

    @GetMapping("/resource/{resourceId}")
    public APIResponse<Page<CommentResponse>> getParentComments(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<CommentResponse>>builder()
                .result(commentService.getParentCommentsByResource(resourceId, pageable))
                .message("Get parent comments successfully")
                .build();
    }

    @GetMapping("/reply/{replyId}")
    public APIResponse<Page<CommentResponse>> getChildComments(
            @PathVariable String replyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<CommentResponse>>builder()
                .result(commentService.getChildComments(replyId, pageable))
                .message("Get child comments successfully")
                .build();
    }
}
