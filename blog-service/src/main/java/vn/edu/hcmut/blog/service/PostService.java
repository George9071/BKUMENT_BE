package vn.edu.hcmut.blog.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.dto.response.APIResponse;
import vn.edu.hcmut.blog.dto.response.BlogMetadataResponse;
import vn.edu.hcmut.blog.dto.response.ProfileResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.entity.PostAsset;
import vn.edu.hcmut.blog.entity.Resource;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.repository.PostAssetRepository;
import vn.edu.hcmut.blog.repository.PostRepository;
import vn.edu.hcmut.blog.repository.ResourceRepository;
import vn.edu.hcmut.blog.repository.httpclient.ProfileClient;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    static int MAX_LENGTH = 500;
    PostRepository postRepository;
    ResourceRepository resourceRepository;
    PostAssetRepository postAssetRepository;
    ProfileClient profileClient;

    public Page<BlogMetadataResponse> search(String keyword, Pageable pageable) {
        Page<Post> posts;
        boolean isUuidQuery = false;

        if (keyword == null || keyword.isBlank()) {
            posts = postRepository.findAll(pageable);
        } else if (isUUID(keyword)) {
            Optional<Post> optionalPost = postRepository.findById(keyword);
            posts = optionalPost
                    .map(post -> new PageImpl<>(List.of(post), pageable, 1L))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0L));
            isUuidQuery = true;
        } else {
            posts = postRepository.findByTitleContainingIgnoreCase(keyword, pageable);
        }

        boolean finalIsUuidQuery = isUuidQuery;
        return posts.map(post -> toBlogMetadataResponse(post, finalIsUuidQuery));
    }

    public Page<BlogMetadataResponse> getBlogsByOwnerId(String ownerId, Pageable pageable) {
        Page<Post> posts = postRepository.findByOwnerId(ownerId, pageable);
        return posts.map(post -> toBlogMetadataResponse(post, false));
    }

    private BlogMetadataResponse toBlogMetadataResponse(Post post, boolean detailed) {
        APIResponse<ProfileResponse> apiResponse = profileClient.findUserProfileById(post.getOwnerId());
        ProfileResponse profile = apiResponse.getResult();

        BlogMetadataResponse.Author authorDto = null;
        if (profile != null) {
            authorDto = BlogMetadataResponse.Author.builder()
                    .id(profile.getId())
                    .name(profile.getFullName())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        }

        String processedContent = detailed ? post.getContent() : htmlToTextWithoutImages(post.getContent());

        return BlogMetadataResponse.builder()
                .id(post.getId())
                .name(post.getTitle())
                .author(authorDto)
                .content(processedContent)
                .coverImage(post.getCoverImage())
                .createdAt(post.getCreatedAt())
                .build();
    }

    public boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public Post createBlog(BlogMetadataRequest request, String ownerId) {
        Post post = new Post();
        post.setContent(request.getContent());
        post.setOwnerId(ownerId);
        post.setCoverImage(request.getCoverImage());
        post.setCreatedAt(LocalDateTime.now());
        post.setTitle(request.getTitle());
        post.setType("POST");
        post.setUpdatedAt(LocalDateTime.now());
        post.setVisibility(request.getVisibility());

        postRepository.save(post);

        // Points hook: +20 for blog post
        try {
            profileClient.updatePoints(ownerId, 20L);
        } catch (Exception e) {
            log.error("Failed to update points for blog post: {}", post.getId(), e);
        }

        for (String assetId : request.getAssetIds()) {
            PostAsset postAsset = new PostAsset();
            postAsset.setResourceId(post.getId());
            postAsset.setId(assetId);
            postAssetRepository.save(postAsset);
        }

        return post;
    }

    @Transactional
    public Post updateBlog(BlogMetadataRequest request, String blogId) {
        Resource resource = resourceRepository.findById(blogId).orElseThrow(() -> {
            throw new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        });
        Post post = postRepository.findById(blogId).orElseThrow(() -> {
            throw new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        });
        postAssetRepository.deleteByResourceId(blogId);

        post.setContent(request.getContent());
        post.setCoverImage(request.getCoverImage());
        resource.setTitle(request.getTitle());
        resource.setType("POST");
        resource.setUpdatedAt(LocalDateTime.now());
        resource.setVisibility(request.getVisibility());
        resourceRepository.save(resource);

        for (String assetId : request.getAssetIds()) {
            PostAsset postAsset = new PostAsset();
            postAsset.setResourceId(post.getId());
            postAsset.setId(assetId);
            postAssetRepository.save(postAsset);
        }

        return postRepository.save(post);
    }

    public String getOwnerId(String blogId) {
        Post post = postRepository.findById(blogId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
        return post.getOwnerId();
    }

    public String htmlToTextWithoutImages(String html) {
        if (html == null || html.isBlank()) return "";

        Document doc = Jsoup.parse(html);
        doc.select("img").remove();

        String text = doc.text().replace("\u00A0", " ").trim();

        if (text.isEmpty()) return "";

        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
