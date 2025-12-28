package vn.edu.hcmut.blog.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @GetMapping("/search")
    public APIResponse<Page<BlogMetadataResponse>> searchBlogs(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Post> posts = postService.search(q, pageable);
        posts.forEach(p -> System.out.println("content = " + p.getContent()));
        posts.forEach(p -> {
            String text = postService.htmlToTextWithoutImages(p.getContent());
            System.out.println("TEXT = [" + text + "]");
        });

        if (postService.isUUID(q)) {
            Page<BlogMetadataResponse> result = posts.map(post -> BlogMetadataResponse.builder()
                    .id(post.getId())
                    .name(post.getTitle())
                    .authorId(post.getOwnerId())
                    .createdAt(post.getCreatedAt())
                    .content(post.getContent())
                    .coverImage(post.getCoverImage())
                    .build());

            return APIResponse.<Page<BlogMetadataResponse>>builder()
                    .result(result)
                    .message("Search blogs successfully")
                    .build();
        } else {
            Page<BlogMetadataResponse> result = posts.map(post -> BlogMetadataResponse.builder()
                    .id(post.getId())
                    .name(post.getTitle())
                    .authorId(post.getOwnerId())
                    .content(postService.htmlToTextWithoutImages(post.getContent()))
                    .coverImage(post.getCoverImage())
                    .build());

            return APIResponse.<Page<BlogMetadataResponse>>builder()
                    .result(result)
                    .message("Search blogs successfully")
                    .build();
        }
    }

    @PostMapping("")
    public APIResponse<BlogMetadataResponse> createResource(@RequestBody @Valid BlogMetadataRequest request) {
        Post post = postService.createBlog(request, "5c240a33-aa0c-4d98-bd88-68c52dc86486");

        return APIResponse.<BlogMetadataResponse>builder()
                .result(BlogMetadataResponse.builder()
                        .id(post.getId())
                        .name(post.getTitle())
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
