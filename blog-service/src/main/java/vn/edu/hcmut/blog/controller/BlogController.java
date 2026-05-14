package vn.edu.hcmut.blog.controller;

import jakarta.validation.Valid;

import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.dto.response.APIResponse;
import vn.edu.hcmut.blog.dto.response.BlogMetadataResponse;
import vn.edu.hcmut.blog.dto.response.ReportedBlogResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.service.PostService;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
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

    @GetMapping("/user/{userId}")
    public APIResponse<Page<BlogMetadataResponse>> getBlogsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<BlogMetadataResponse> result = postService.getBlogsByOwnerId(userId, pageable);

        return APIResponse.<Page<BlogMetadataResponse>>builder()
                .result(result)
                .message("Get blogs by user id successfully")
                .build();
    }

    @GetMapping("/top-blog")
    public APIResponse<Page<BlogMetadataResponse>> getTopBlogs(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<BlogMetadataResponse> result = postService.getTopBlogs(pageable);

        return APIResponse.<Page<BlogMetadataResponse>>builder()
                .result(result)
                .message("Get top blogs successfully")
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
    @PreAuthorize("isAuthenticated()")
    public APIResponse<BlogMetadataResponse> updateBlog(
            @PathVariable @NotBlank String blogId,
            @RequestBody @Valid BlogMetadataRequest request) {

        /*
        Jwt jwt = getCurrentJwt();
        String callerProfileId = jwt.getSubject();
        boolean isAdmin = hasRole(jwt, ADMIN_ROLE);

        String ownerId = postService.getOwnerId(blogId);

        if (!isAdmin && !callerProfileId.equals(ownerId)) throw new AppException(ErrorCode.UNAUTHORIZED);
         */
        Post updated = postService.updateBlog(request, blogId);

        BlogMetadataResponse response = postService.getBlogById(updated.getId());

        return APIResponse.<BlogMetadataResponse>builder()
                .result(response)
                .message("Blog updated successfully")
                .build();
    }

    @DeleteMapping("{blogId}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<Void> deleteBlog(@PathVariable String blogId) {

        String userId = getProfileIdFromToken();
        String ownerId = postService.getOwnerId(blogId);

        if (!userId.equals(ownerId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // TODO: admin role can delete single blog too.
        // boolean isAdmin = hasRole(jwt, ADMIN_ROLE);
        // if (!isAdmin && !userId.equals(ownerId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        postService.deleteBlog(blogId);

        return APIResponse.<Void>builder().message("Blog deleted successfully").build();
    }

    @GetMapping("/admin/reported")
    public APIResponse<Page<ReportedBlogResponse>> getReportedBlogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        checkAdminRole();

        Pageable pageable = PageRequest.of(page, size);
        Page<ReportedBlogResponse> result = postService.getReportedBlogs(pageable);

        return APIResponse.<Page<ReportedBlogResponse>>builder()
                .result(result)
                .message("Get reported blogs successfully")
                .build();
    }

    @DeleteMapping("/by-owner/{ownerId}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') or hasRole('ADMIN')")
    public APIResponse<Void> deleteBlogsByOwner(@PathVariable @NotBlank String ownerId) {
        postService.deleteByOwnerId(ownerId);
        log.info("Bulk-deleted all blogs for owner {}", ownerId);
        return APIResponse.<Void>builder()
                .code(1000)
                .message("All blogs for owner deleted")
                .build();
    }

    private void checkAdminRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Jwt getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jwt;
    }

    private boolean hasRole(Jwt jwt, String role) {
        var roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }
}
