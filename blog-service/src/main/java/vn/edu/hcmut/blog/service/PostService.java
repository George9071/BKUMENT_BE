package vn.edu.hcmut.blog.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.dto.response.BlogMetadataResponse;
import vn.edu.hcmut.blog.dto.response.ProfileResponse;
import vn.edu.hcmut.blog.dto.response.ResourceEngagementStatsResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.entity.PostAsset;
import vn.edu.hcmut.blog.entity.Resource;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.repository.PostAssetRepository;
import vn.edu.hcmut.blog.repository.PostRepository;
import vn.edu.hcmut.blog.repository.ResourceRepository;
import vn.edu.hcmut.blog.repository.httpclient.ProfileClient;
import vn.edu.hcmut.blog.repository.httpclient.SocialClient;

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
    SocialClient socialClient;

    @NonFinal
    @Value("${app.trending.w1:100.0}")
    double w1;

    @NonFinal
    @Value("${app.trending.w2:1.0}")
    double w2;

    @NonFinal
    @Value("${app.trending.w3:5.0}")
    double w3;

    @NonFinal
    @Value("${app.trending.gravity:1.8}")
    double gravity;

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

        if (!posts.isEmpty()) {
            List<String> ids = posts.getContent().stream().map(Post::getId).toList();
            postRepository.incrementViews(ids);
        }

        boolean finalIsUuidQuery = isUuidQuery;
        return posts.map(post -> toBlogMetadataResponse(post, finalIsUuidQuery));
    }

    public Page<BlogMetadataResponse> getBlogsByOwnerId(String ownerId, Pageable pageable) {
        Page<Post> posts = postRepository.findByOwnerId(ownerId, pageable);
        if (!posts.isEmpty()) {
            List<String> ids = posts.getContent().stream().map(Post::getId).toList();
            postRepository.incrementViews(ids);
        }
        return posts.map(post -> toBlogMetadataResponse(post, false));
    }

    public Page<BlogMetadataResponse> getTopBlogs(Pageable pageable) {
        List<Post> allPosts = postRepository.findAll();

        if (allPosts.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<String, ResourceEngagementStatsResponse> engagementMap = new HashMap<>();
        try {
            List<ResourceEngagementStatsResponse> engagementStats = socialClient.getEngagementStats();
            if (engagementStats != null) {
                engagementMap = engagementStats.stream()
                        .collect(Collectors.toMap(ResourceEngagementStatsResponse::getResourceId, s -> s));
            }
        } catch (Exception e) {
            log.error("Failed to fetch engagement stats from social service", e);
        }

        final Map<String, ResourceEngagementStatsResponse> finalEngagementMap = engagementMap;
        LocalDateTime now = LocalDateTime.now();

        List<PostScore> scoredPosts = allPosts.stream()
                .map(post -> {
                    ResourceEngagementStatsResponse stats = finalEngagementMap.get(post.getId());
                    double avgRate =
                            (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;
                    long numRates = (stats != null && stats.getRatingCount() != null) ? stats.getRatingCount() : 0L;
                    long comments = (stats != null && stats.getCommentCount() != null) ? stats.getCommentCount() : 0L;
                    long views = post.getViews() != null ? post.getViews() : 0L;

                    long ageInHours = ChronoUnit.HOURS.between(post.getCreatedAt(), now);

                    // Trending_Score = (w1 * [avg_rate * log10(1 + num_rates)] + w2 * Views + w3 * Comments) /
                    // (age_in_hours + 2)^G
                    double numerator = (w1 * (avgRate * Math.log10(1 + numRates))) + (w2 * views) + (w3 * comments);
                    double denominator = Math.pow(ageInHours + 2, gravity);
                    double score = numerator / denominator;

                    return new PostScore(post, score);
                })
                .sorted(Comparator.comparingDouble((PostScore ps) -> ps.score).reversed())
                .collect(Collectors.toList());

        // Paginate
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), scoredPosts.size());

        if (start > scoredPosts.size()) {
            return new PageImpl<>(List.of(), pageable, scoredPosts.size());
        }

        List<Post> pagedPosts =
                scoredPosts.subList(start, end).stream().map(ps -> ps.post).collect(Collectors.toList());

        if (!pagedPosts.isEmpty()) {
            postRepository.incrementViews(pagedPosts.stream().map(Post::getId).toList());
        }

        return new PageImpl<>(pagedPosts, pageable, scoredPosts.size())
                .map(post -> toBlogMetadataResponse(post, false));
    }

    private static class PostScore {
        Post post;
        double score;

        public PostScore(Post post, double score) {
            this.post = post;
            this.score = score;
        }
    }

    private BlogMetadataResponse toBlogMetadataResponse(Post post, boolean detailed) {
        ProfileResponse profile = profileClient.findUserProfileById(post.getOwnerId());
        log.info("FULL RESPONSE from profile-service: {}", profile);

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
                .views(post.getViews())
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
        post.setViews(0L);
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

    public boolean existsById(String blogId) {
        return postRepository.existsById(blogId);
    }

    @Transactional
    public void deleteBlog(String blogId) {
        Post post = postRepository.findById(blogId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
        postAssetRepository.deleteByResourceId(blogId);
        postRepository.delete(post);
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
