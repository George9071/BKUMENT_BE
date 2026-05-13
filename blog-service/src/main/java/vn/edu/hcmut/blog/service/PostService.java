package vn.edu.hcmut.blog.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.blog.dto.request.BlogMetadataRequest;
import vn.edu.hcmut.blog.dto.response.BlogMetadataResponse;
import vn.edu.hcmut.blog.dto.response.ProfileResponse;
import vn.edu.hcmut.blog.dto.response.ReportInfo;
import vn.edu.hcmut.blog.dto.response.ReportedBlogResponse;
import vn.edu.hcmut.blog.dto.response.SocialReportResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.entity.PostAsset;
import vn.edu.hcmut.blog.exception.AppException;
import vn.edu.hcmut.blog.exception.ErrorCode;
import vn.edu.hcmut.blog.repository.PostAssetRepository;
import vn.edu.hcmut.blog.repository.PostRepository;
import vn.edu.hcmut.blog.repository.httpclient.ProfileClient;
import vn.edu.hcmut.blog.repository.httpclient.SocialClient;

/**
 * Manages blog post CRUD, search, trending feed, and view tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {

    /**
     * Maximum number of characters returned in the truncated content preview
     */
    static final int MAX_LENGTH = 500;

    /**
     * Time window (days) for the trending feed query. Older posts decay out anyway.
     */
    static final int TRENDING_WINDOW_DAYS = 30;

    PostRepository postRepository;
    PostAssetRepository postAssetRepository;
    ProfileClient profileClient;
    SocialClient socialClient;
    MinioClient minioClient;

    @NonFinal
    @Value("${minio.bucket-name:resource-bucket}")
    String bucketName;

    /**
     * Routing logic:
     * - blank keyword --> return everything paginated by createdAt.
     * - UUID input --> direct ID lookup; the result is shown with full content
     * because UUID lookup is
     * equivalent to a "view single post by ID".
     * - other input --> results are ordered by ts_rank_cd relevance score, not
     * insertion order.
     */
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
            posts = postRepository.searchByFullText(keyword, pageable);
        }

        if (!posts.isEmpty()) {
            postRepository.incrementViews(
                    posts.getContent().stream().map(Post::getId).toList());
        }

        return mapPageWithBatchProfileFetch(posts, isUuidQuery);
    }

    public Page<BlogMetadataResponse> getBlogsByOwnerId(String ownerId, Pageable pageable) {
        Page<Post> posts = postRepository.findByOwnerId(ownerId, pageable);
        if (!posts.isEmpty()) {
            postRepository.incrementViews(
                    posts.getContent().stream().map(Post::getId).toList());
        }
        return mapPageWithBatchProfileFetch(posts, false);
    }

    public Page<BlogMetadataResponse> getTopBlogs(Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusDays(TRENDING_WINDOW_DAYS);

        Page<Post> posts = postRepository.findRecentPostsByTrendingScore(since, pageable);

        if (posts.isEmpty()) {
            log.info("[TRENDING] No posts within {} day window", TRENDING_WINDOW_DAYS);
            posts = postRepository.findAllOrderByCreatedAtDesc(pageable);
        }

        if (!posts.isEmpty())
            postRepository.incrementViews(
                    posts.getContent().stream().map(Post::getId).toList());

        return mapPageWithBatchProfileFetch(posts, false);
    }

    /**
     * Creates a new blog post and its associated media-asset references.
     */
    @Transactional
    public Post createBlog(BlogMetadataRequest request, String ownerId) {

        Post post = Post.builder()
                .content(sanitizeHtml(request.getContent()))
                .ownerId(ownerId)
                .coverImage(request.getCoverImage())
                .title(request.getTitle())
                .type("POST")
                .views(0L)
                .visibility(request.getVisibility())
                .topicId(request.getTopicId() != null ? request.getTopicId() : "")
                .universityId(request.getUniversityId())
                .courseId(request.getCourseId())
                .build();

        postRepository.save(post);

        try {
            profileClient.updatePoints(ownerId, 10L);
        } catch (Exception e) {
            log.error("Failed to award points for blog post {}: {}", post.getId(), e.getMessage());
        }

        saveAssets(post.getId(), request.getAssetIds());
        return post;
    }

    /**
     * Updates an existing post
     */
    @Transactional
    public Post updateBlog(BlogMetadataRequest request, String blogId) {

        Post post = postRepository.findById(blogId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (request.getAssetIds() != null) {
            List<PostAsset> currentAssets = postAssetRepository.findByResourceId(blogId);

            Set<String> currentAssetIds =
                    currentAssets.stream().map(PostAsset::getId).collect(Collectors.toSet());

            Set<String> newAssetIds = new HashSet<>(request.getAssetIds());

            List<PostAsset> assetsToRemove = currentAssets.stream()
                    .filter(asset -> !newAssetIds.contains(asset.getId()))
                    .toList();

            if (!assetsToRemove.isEmpty()) {
                removeAssetFiles(assetsToRemove);

                List<String> idsToDelete =
                        assetsToRemove.stream().map(PostAsset::getId).toList();

                postAssetRepository.deleteByResourceIdAndIdIn(blogId, idsToDelete);
            }

            List<String> assetIdsToAdd = newAssetIds.stream()
                    .filter(id -> !currentAssetIds.contains(id))
                    .toList();

            if (!assetIdsToAdd.isEmpty()) saveAssets(blogId, assetIdsToAdd);
        }

        post.setTitle(request.getTitle());
        post.setVisibility(request.getVisibility());
        post.setContent(sanitizeHtml(request.getContent()));
        post.setCoverImage(request.getCoverImage());
        post.setTopicId(request.getTopicId() != null ? request.getTopicId() : "");
        post.setUniversityId(request.getUniversityId());
        post.setCourseId(request.getCourseId());

        return postRepository.save(post);
    }

    public String getOwnerId(String blogId) {
        return postRepository
                .findById(blogId)
                .map(Post::getOwnerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));
    }

    public boolean existsById(String blogId) {
        return postRepository.existsById(blogId);
    }

    /**
     * Deletes a single blog post:
     * - removes media files from MinIO
     * - then the post_asset rows, then the post itself.
     */
    @Transactional
    public void deleteBlog(String blogId) {
        Post post = postRepository.findById(blogId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        // Cascading cleanup of social data
        try {
            socialClient.deleteSocialByResourceId(blogId);
        } catch (Exception e) {
            log.error("Failed to clean up social data for blog {}: {}", blogId, e.getMessage());
        }

        List<PostAsset> assets = postAssetRepository.findByResourceId(blogId);
        removeAssetFiles(assets);
        postAssetRepository.deleteByResourceId(blogId);
        postRepository.delete(post);
    }

    /**
     * Bulk-deletes all blogs belonging to a single user.
     * * * *
     * Cascade order:
     * 1. Look up the user's posts and their asset references.
     * 2. Remove every asset's underlying MinIO object (best-effort).
     * 3. Remove every PostAsset row for the user's posts.
     * 4. Bulk-delete the posts themselves via deleteByOwnerId.
     */
    @Transactional
    public void deleteByOwnerId(String ownerId) {
        List<Post> posts = postRepository
                .findByOwnerId(ownerId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        if (posts.isEmpty()) {
            postRepository.deleteByOwnerId(ownerId);
            return;
        }

        // Collect every asset across every post
        List<PostAsset> assets = new java.util.ArrayList<>();
        for (Post post : posts) assets.addAll(postAssetRepository.findByResourceId(post.getId()));

        removeAssetFiles(assets);

        List<String> postIds = posts.stream().map(Post::getId).toList();
        postAssetRepository.deleteByResourceIdIn(postIds);

        postRepository.deleteByOwnerId(ownerId);

        log.info("Deleted {} blogs and {} assets for owner {}", posts.size(), assets.size(), ownerId);
    }

    /**
     * Removes the underlying MinIO objects for a list of post_assets.
     * * * *
     * If you switch to MinIO's batch removeObjects() API, replace this loop
     * with a single batched call — only one network round trip for all assets.
     */
    private void removeAssetFiles(List<PostAsset> assets) {
        if (assets == null || assets.isEmpty()) return;

        for (PostAsset asset : assets) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(asset.getId())
                        .build());
            } catch (Exception e) {
                // Non-fatal: log and continue. The DB cleanup below still proceeds.
                log.error(
                        "Failed to remove MinIO object {} for resource {}: {}",
                        asset.getId(),
                        asset.getResourceId(),
                        e.getMessage());
            }
        }
    }

    private void saveAssets(String postId, List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return;

        List<PostAsset> assets = assetIds.stream()
                .map(id -> {
                    PostAsset a = new PostAsset();
                    a.setId(id);
                    a.setResourceId(postId);
                    return a;
                })
                .toList();
        postAssetRepository.saveAll(assets);
    }

    private Page<BlogMetadataResponse> mapPageWithBatchProfileFetch(Page<Post> posts, boolean detailed) {
        if (posts.isEmpty()) return posts.map(p -> null);

        List<String> ownerIds =
                posts.getContent().stream().map(Post::getOwnerId).distinct().toList();

        Map<String, ProfileResponse> profiles = fetchProfileMap(ownerIds);

        return posts.map(post -> toBlogMetadataResponse(post, profiles.get(post.getOwnerId()), detailed));
    }

    private Map<String, ProfileResponse> fetchProfileMap(List<String> ownerIds) {
        if (ownerIds.isEmpty()) return Collections.emptyMap();

        return profileClient.getProfiles(ownerIds).stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));
    }

    private BlogMetadataResponse toBlogMetadataResponse(Post post, ProfileResponse profile, boolean detailed) {
        BlogMetadataResponse.Author authorDto = null;
        if (profile != null) {
            authorDto = BlogMetadataResponse.Author.builder()
                    .id(profile.getId())
                    .name(profile.getFullName())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        }

        // Detailed = full sanitised HTML (single-post view).
        // Not detailed = plain-text excerpt (list/feed view).
        String content = detailed ? post.getContent() : htmlToTextWithoutImages(post.getContent());

        return BlogMetadataResponse.builder()
                .id(post.getId())
                .name(post.getTitle())
                .author(authorDto)
                .content(content)
                .coverImage(post.getCoverImage())
                .createdAt(post.getCreatedAt())
                .views(post.getViews())
                .build();
    }

    private String sanitizeHtml(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, Safelist.relaxed());
    }

    public String htmlToTextWithoutImages(String html) {
        if (html == null || html.isBlank()) return "";

        Document document = Jsoup.parse(html);
        document.select("img").remove();

        String text = document.text().replace("\u00A0", " ").trim();
        if (text.isEmpty()) return "";
        if (text.length() <= MAX_LENGTH) return text;

        int cutoff = text.lastIndexOf(' ', MAX_LENGTH);
        String truncated = (cutoff > 0) ? text.substring(0, cutoff) : text.substring(0, MAX_LENGTH);
        return truncated + "...";
    }

    private boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Admin endpoint: returns posts that have been reported, enriched with their report list.
     * Posts are sorted by number of reports (descending).
     */
    public Page<ReportedBlogResponse> getReportedBlogs(Pageable pageable) {
        // 1. Fetch ALL blog reports from social-service (type=BLOG, not deleted)
        List<SocialReportResponse> allReports;
        try {
            // Fetch all blog-type post IDs that have reports
            // We get reports then find the corresponding posts
            List<String> allBlogIds =
                    postRepository.findAll().stream().map(Post::getId).toList();

            if (allBlogIds.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);

            allReports = socialClient.getReportsByTargetIds(allBlogIds);
        } catch (Exception e) {
            log.error("Failed to fetch reports from social-service: {}", e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if (allReports.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);

        // 2. Group reports by targetId (blog ID)
        Map<String, List<SocialReportResponse>> reportsByBlogId =
                allReports.stream().collect(Collectors.groupingBy(SocialReportResponse::getTargetId));

        // 3. Sort blog IDs by report count (desc)
        List<String> sortedBlogIds = reportsByBlogId.entrySet().stream()
                .sorted((a, b) ->
                        Integer.compare(b.getValue().size(), a.getValue().size()))
                .map(Map.Entry::getKey)
                .toList();

        // 4. Apply pagination
        int total = sortedBlogIds.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        if (start >= total) return new PageImpl<>(List.of(), pageable, total);

        List<String> pagedBlogIds = sortedBlogIds.subList(start, end);

        // 5. Fetch posts
        List<Post> posts = postRepository.findAllById(pagedBlogIds);
        Map<String, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, Function.identity()));

        // 6. Fetch profiles in batch
        List<String> ownerIds = posts.stream().map(Post::getOwnerId).distinct().toList();
        Map<String, ProfileResponse> profiles = fetchProfileMap(ownerIds);

        // 7. Build response maintaining sort order
        List<ReportedBlogResponse> result = pagedBlogIds.stream()
                .filter(postMap::containsKey)
                .map(blogId -> {
                    Post post = postMap.get(blogId);
                    ProfileResponse profile = profiles.get(post.getOwnerId());

                    BlogMetadataResponse.Author authorDto = null;
                    if (profile != null) {
                        authorDto = BlogMetadataResponse.Author.builder()
                                .id(profile.getId())
                                .name(profile.getFullName())
                                .avatarUrl(profile.getAvatarUrl())
                                .build();
                    }

                    List<ReportInfo> reportInfos = reportsByBlogId.getOrDefault(blogId, List.of()).stream()
                            .map(r -> ReportInfo.builder()
                                    .id(r.getId())
                                    .reporterId(r.getReporterId())
                                    .status(r.getStatus())
                                    .reason(r.getReason())
                                    .detail(r.getDetail())
                                    .createdAt(r.getCreatedAt())
                                    .build())
                            .toList();

                    return ReportedBlogResponse.builder()
                            .id(post.getId())
                            .name(post.getTitle())
                            .author(authorDto)
                            .content(htmlToTextWithoutImages(post.getContent()))
                            .coverImage(post.getCoverImage())
                            .createdAt(post.getCreatedAt())
                            .views(post.getViews())
                            .reportList(reportInfos)
                            .build();
                })
                .toList();

        return new PageImpl<>(result, pageable, total);
    }
}
