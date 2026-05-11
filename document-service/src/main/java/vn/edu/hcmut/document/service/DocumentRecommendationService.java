package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.dto.RecommendationItem;
import vn.edu.hcmut.document.dto.response.RecommendationReason;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.neo4j.DocumentNeo4jRepository;

/**
 * Orchestrates the hybrid recommendation pipeline that powers two endpoints:
 * * * *
 *   1. "Related Documents" sidebar:
 *      Combines semantic vector similarity (pgvector) with item-based collaborative
 *      filtering (Neo4j) using Reciprocal Rank Fusion (RRF).
 *   2. Personalised "For You" feed:
 *      Three-layer cascade: user-based CF -> topic/class cold-start -> trending fallback.
 *      Each layer fills the pool up to {@code poolSize} before the next is consulted.
 * * * *
 * ── Dependency responsibilities ───────────────────────────────────────────────
 *   DocumentRepository     : pgvector semantic search + trending score query
 *   DocumentNeo4jRepository: item-based CF + user-based CF + cold-start queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentRecommendationService {

    final DocumentRepository documentRepository;
    final DocumentNeo4jRepository neo4jRepository;

    /**
     * Word count threshold that determines whether the context string is treated as
     * "long" (rich semantic signal) or "short" (sparse, trust collaborative filtering more).
     * Default: 10 words.
     */
    @Value("${app.hybrid.threshold-words:10}")
    int thresholdWords;

    /** Semantic weight when the context has ≤ {@link #thresholdWords} words. Default: 0.2 */
    @Value("${app.hybrid.weight-semantic-short:0.2}")
    double weightSemanticShort;

    /** CF weight when the context has ≤ {@link #thresholdWords} words. Default: 0.8 */
    @Value("${app.hybrid.weight-cf-short:0.8}")
    double weightCfShort;

    /** Semantic weight when the context has > {@link #thresholdWords} words. Default: 0.7 */
    @Value("${app.hybrid.weight-semantic-long:0.7}")
    double weightSemanticLong;

    /** CF weight when the context has > {@link #thresholdWords} words. Default: 0.3 */
    @Value("${app.hybrid.weight-cf-long:0.3}")
    double weightCfLong;

    // Common constants in Reciprocal Rank Fusion (RRF)
    static final int RRF_K = 60;

    /**
     * Returns a paginated hybrid-ranked list of documents related to given document.
     * * * *
     * ── Algorithm ─────────────────────────────────────────────────────────────
     * 1. Fetch top-50 candidates from pgvector semantic search (cosine + keyword RRF).
     * 2. Fetch top-50 candidates from Neo4j item-based CF (co-download graph).
     * 3. If CF returns no results, fall back to semantic-only and return immediately.
     * 4. Otherwise, merge both lists using Reciprocal Rank Fusion:
     *      rrf_score(doc, rank) = 1 / (RRF_K + rank + 1)
     *      hybrid_score = weightSemantic * rrfSemantic + weightCF * rrfCF
     * 5. Sort descending by hybrid_score, paginate in-memory.
     * * * *
     * ── Contextual weight adaptation ──────────────────────────────────────────
     * The weight split between semantic and CF is dynamic based on the word count of the context string
     * (title + keywords of the source document):
     *   - Short context (≤ threshold words): collaborative filtering is trusted more
     *     because the embedding has limited signal from a sparse title/keyword set.
     *   - Long context (> threshold words): semantic is trusted more
     *     because the rich text embedding is a reliable similarity signal.
     * * * *
     * @param context   concatenated title + keywords of the source document (may be blank)
     * @param docId     the source document to find related content for (excluded from results)
     * @param vectorStr the source document's embedding serialised as "[f1, f2, ...]"
     * @param pageable  page number and size
     * @return a Page of RecommendationItem ordered by hybrid RRF score descending
     */
    public Page<RecommendationItem> getHybridRelatedDocumentIds(
            String context, String docId, String vectorStr, Pageable pageable) {

        // ── Step 1: Semantic candidates (pgvector) ────────────────────────────
        Page<String> semanticPage = documentRepository
                .findRelatedDocumentIds(vectorStr, context, docId, PageRequest.of(0, 50));
        List<String> semanticDocs = semanticPage.getContent();

        // ── Step 2: Item-CF candidates (Neo4j co-download graph) ──────────────
        List<String> cfDocs;
        try {
            cfDocs = neo4jRepository.findItemBasedCFRecommendations(docId, 50).stream()
                    .map(map -> (String) map.get("recommendedDocId"))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("[HYBRID] Item-CF query failed for doc {}; falling back to semantic only: {}",
                    docId, e.getMessage());
            cfDocs = List.of();
        }

        // ── Step 3: CF empty -> semantic-only fallback ────────────────────────
        if (cfDocs.isEmpty()) {
            log.debug("[HYBRID] Collaborative filtering empty for doc {}; using semantic-only results", docId);
            Page<String> ids = documentRepository.findRelatedDocumentIds(vectorStr, context, docId, pageable);
            return ids.map(id -> RecommendationItem.builder()
                    .docId(id)
                    .reason(RecommendationReason.builder().type("SIMILAR").build())
                    .build());
        }

        // ── Step 4: Contextual weight selection ───────────────────────────────
        int wordCount = (context == null || context.isBlank())
                ? 0
                : context.trim().split("\\s+").length;

        double weightSemantic = (wordCount > thresholdWords) ? weightSemanticLong : weightSemanticShort;
        double weightCf       = (wordCount > thresholdWords) ? weightCfLong       : weightCfShort;

        // ── Step 5: Reciprocal Rank Fusion ────────────────────────────────────
        Map<String, Double> finalScores = new HashMap<>();

        for (int i = 0; i < semanticDocs.size(); i++) {
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(semanticDocs.get(i), weightSemantic * rrf, Double::sum);
        }
        for (int i = 0; i < cfDocs.size(); i++) {
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(cfDocs.get(i), weightCf * rrf, Double::sum);
        }

        // ── Step 6: Sort + paginate in-memory ─────────────────────────────────
        List<String> hybridRankedIds = finalScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), hybridRankedIds.size());

        if (start >= hybridRankedIds.size()) {
            return new PageImpl<>(List.of(), pageable, hybridRankedIds.size());
        }

        List<RecommendationItem> items = hybridRankedIds.subList(start, end).stream()
                .map(id -> RecommendationItem.builder()
                        .docId(id)
                        .reason(RecommendationReason.builder().type("SIMILAR").build())
                        .build())
                .toList();

        return new PageImpl<>(items, pageable, hybridRankedIds.size());
    }

    /**
     * Builds a personalised feed for the given user using a three-layer cascade.
     * * * *
     * ── Layer ordering  ──────────────────────────────────────────
     * * * *
     *   Layer 1 — user-based collaborative filtering (personalised, requires download history):
     *     Queries the user-user similarity graph.
     *     Returns documents that similar users have downloaded and the requesting user has not.
     *     Best quality but requires sufficient interaction history.
     * * * *
     *   Layer 2 — topic / class cold-start (semi-personalised):
     *     Documents tagged with topics from the user's enrolled classrooms or stated topic interests.
     *     Useful for new users with little download history.
     * * * *
     *   Layer 3 — trending fallback (non-personalised):
     *     Uses pre-computed ranking_score (maintained by DocumentRankingScheduler).
     *     Ensures the feed is never empty, even for brand-new users with no graph data at all.
     * * * *
     * @param userId   the authenticated user's profile ID; null is allowed
     *                 (anonymous -> Layers 1 & 2 are skipped, pool is filled entirely by Layer 3)
     * @param pageable page number and size for the final response
     * @return a Page of RecommendationItem ordered by layer priority descending
     */
    public Page<RecommendationItem> getForYouFeed(String userId, Pageable pageable) {
        int poolSize = 200;

        Map<String, RecommendationItem> pool = new LinkedHashMap<>();

        // ── Layer 1: user-based collaborative filtering
        if (userId != null) {
            try {
                List<Map<String, Object>> recommendations =
                        neo4jRepository.findUserBasedCFRecommendations(userId, poolSize);

                for (Map<String, Object> recommendation : recommendations) {
                    if (pool.size() >= poolSize) break;

                    String docId = (String) recommendation.get("recommendedDocId");
                    if (docId == null || pool.containsKey(docId)) continue;

                    // Store the raw trigger entity ID in triggerId
                    pool.put(docId, RecommendationItem.builder()
                            .docId(docId)
                            .triggerId((String) recommendation.get("reasonTriggerId"))
                            .reason(RecommendationReason.builder()
                                    .type((String) recommendation.get("reasonType"))
                                    .build())
                            .build());
                }
            } catch (Exception e) {
                // Non-fatal: Layer 2 and 3 will compensate for the missing CF items.
                log.warn("[3-layer cascade] Layer 1 (user-collaborative filtering) failed for user {}: {}",
                        userId, e.getMessage());
            }
        }

        // ── Layer 2: topic / class cold-start
        if (pool.size() < poolSize && userId != null) {
            try {
                List<Map<String, Object>> recommendations =
                        neo4jRepository.findColdStartRecommendationsByTopics(userId, poolSize);

                for (Map<String, Object> recommendation : recommendations) {
                    if (pool.size() >= poolSize) break;

                    String docId = (String) recommendation.get("recommendedDocId");
                    if (docId == null || pool.containsKey(docId)) continue;

                    pool.put(docId, RecommendationItem.builder()
                            .docId(docId)
                            .triggerId((String) recommendation.get("reasonTriggerId"))
                            .reason(RecommendationReason.builder()
                                    .type((String) recommendation.get("reasonType"))
                                    .build())
                            .build());
                }
            } catch (Exception e) {
                log.warn("[3-layer cascade] Layer 2 (cold-start) failed for user {}: {}", userId, e.getMessage());
            }
        }

        // ── Layer 3: Trending fallback
        if (pool.size() < poolSize) {
            try {
                int needed = poolSize - pool.size();
                LocalDateTime since = LocalDateTime.now().minusDays(90);
                Page<Document> trendingPage = documentRepository
                        .findRecentDocumentsByRankingScore(since, PageRequest.of(0, needed));


                for (Document doc : trendingPage.getContent()) {
                    if (pool.size() >= poolSize) break;
                    if (pool.containsKey(doc.getId())) continue;

                    pool.put(doc.getId(), RecommendationItem.builder()
                            .docId(doc.getId())
                            // TRENDING items have no trigger entity — triggerId and title remain null.
                            .reason(RecommendationReason.builder().type("TRENDING").build())
                            .build());
                }
            } catch (Exception e) {
                log.error("[3-layer cascade] Layer 3 (trending) failed: {}", e.getMessage());
            }
        }

        List<RecommendationItem> items = new ArrayList<>(pool.values());

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), items.size());

        if (start >= items.size()) return new PageImpl<>(List.of(), pageable, items.size());

        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}
