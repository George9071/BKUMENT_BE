package vn.edu.hcmut.blog.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.dto.response.APIResponse;
import vn.edu.hcmut.blog.dto.response.BlogMetadataResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.service.PostService;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BlogController {
    PostService postService;

    @GetMapping("/health")
    public String getMethodName() {
        return "Blog Service is running";
    }

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            String profileId = jwt.getClaimAsString("profile_id");
            if (profileId == null || profileId.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            }

            return profileId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    @GetMapping("/search")
    public APIResponse<Page<BlogMetadataResponse>> searchBlogs(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<BlogMetadataResponse> result = postService.search(q, pageable);

        return APIResponse.<Page<BlogMetadataResponse>>builder()
                .result(result)
                .message("Search blogs successfully")
                .build();
    }

    @GetMapping("/my-blogs")
    public APIResponse<Page<BlogMetadataResponse>> getMyBlogs(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

        String userId = getProfileIdFromToken();
        Pageable pageable = PageRequest.of(page, size);

        Page<BlogMetadataResponse> result = postService.getBlogsByOwnerId(userId, pageable);

        return APIResponse.<Page<BlogMetadataResponse>>builder()
                .result(result)
                .message("Get my blogs successfully")
                .build();
    }

    @PostMapping("")
    public APIResponse<BlogMetadataResponse> createResource(@RequestBody @Valid BlogMetadataRequest request) {
        String authorId = getProfileIdFromToken();
        Post post = postService.createBlog(request, authorId);

        return APIResponse.<BlogMetadataResponse>builder()
                .result(BlogMetadataResponse.builder()
                        .id(post.getId())
                        .name(post.getTitle())
                        .content(post.getContent())
                        .coverImage(post.getCoverImage())
                        .build())
                .message("Blog created successfully")
                .build();
    }

    @PutMapping("{blogId}")
    public APIResponse<BlogMetadataResponse> updateBlog(
            @PathVariable String blogId, @RequestBody @Valid BlogMetadataRequest request) {
        Post post = postService.updateBlog(request, blogId);

        return APIResponse.<BlogMetadataResponse>builder()
                .result(BlogMetadataResponse.builder()
                        .id(post.getId())
                        .name(post.getTitle())
                        .build())
                .message("Blog created successfully")
                .build();
    }
}
