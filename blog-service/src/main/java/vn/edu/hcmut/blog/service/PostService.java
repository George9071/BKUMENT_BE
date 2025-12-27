package vn.edu.hcmut.blog.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.entity.PostAsset;
import vn.edu.hcmut.blog.entity.Resource;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.repository.PostAssetRepository;
import vn.edu.hcmut.blog.repository.PostRepository;
import vn.edu.hcmut.blog.repository.ResourceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    ResourceRepository resourceRepository;
    PostAssetRepository postAssetRepository;

    public Page<Post> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return postRepository.findAll(pageable);
        }
        if (isUUID(keyword)) {
            Optional<Post> optionalPost = postRepository.findById(keyword);
            return new PageImpl<>(List.of(optionalPost.get()), pageable, 1);
        }

        return postRepository.findByTitleContainingIgnoreCase(keyword, pageable);
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
}
