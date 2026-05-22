package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    @Value("${app.hybrid.weight-cf-short:0.8}")
    double weightCfShort;

    @Value("${recommendation.weights.alpha1:0.3}")
    double alpha1;

    @Value("${recommendation.weights.alpha2:0.3}")
    double alpha2;

    @Value("${recommendation.weights.alpha3:0.2}")
    double alpha3;

    @Value("${recommendation.weights.beta:0.5}")
    double beta;

    @Value("${recommendation.weights.alpha5:0.1}")
    double alpha5;

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
        int limit = 100;
        long startTime = System.currentTimeMillis();

        // 1. Chạy song song các nhánh độc lập qua Thread Pool
        CompletableFuture<List<Map<String, Object>>> graphCfFuture = CompletableFuture.supplyAsync(() -> {
            if (userId == null) return Collections.emptyList();
            try {
                return neo4jRepository.findUserBasedCFRecommendations(userId, limit);
            } catch (Exception e) {
                log.warn("Layer 1.1 (Graph CF) failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<String>> semanticFuture = CompletableFuture.supplyAsync(() -> {
            if (userId == null) return Collections.emptyList();
            try {
                List<String> recentIds = neo4jRepository.findMostRecentDownloadedDocumentIds(userId, 3);
                if (recentIds.isEmpty()) return Collections.emptyList();
                
                List<Document> recentDocs = documentRepository.findAllById(recentIds);
                if (recentDocs.isEmpty()) return Collections.emptyList();
                
                int dim = 768; // pgvector size
                float[] avgVec = new float[dim];
                int count = 0;
                for (Document d : recentDocs) {
                    if (d.getEmbedding() != null && d.getEmbedding().length == dim) {
                        for (int i = 0; i < dim; i++) avgVec[i] += d.getEmbedding()[i];
                        count++;
                    }
                }
                if (count == 0) return Collections.emptyList();
                for (int i = 0; i < dim; i++) avgVec[i] /= count;
                
                String vectorStr = Arrays.toString(avgVec);
                Page<String> semanticPage = documentRepository.findRelatedDocumentIds(vectorStr, "", "", PageRequest.of(0, limit));
                return semanticPage.getContent();
            } catch (Exception e) {
                log.warn("Layer 1.2 (Semantic) failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<Map<String, Object>>> classBasedFuture = CompletableFuture.supplyAsync(() -> {
            if (userId == null) return Collections.emptyList();
            try {
                return neo4jRepository.findClassBasedRecommendations(userId, limit);
            } catch (Exception e) {
                log.warn("Layer 2 (Class Based) failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<Document>> trendingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return documentRepository.findRecentDocumentsByRankingScore(LocalDateTime.now().minusDays(90), PageRequest.of(0, limit)).getContent();
            } catch (Exception e) {
                log.warn("Layer 3.1 (Trending) failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        });

        // 2. Chờ tất cả các nhánh hoàn thành (Parallel Wait)
        CompletableFuture.allOf(graphCfFuture, semanticFuture, classBasedFuture, trendingFuture).join();

        // 3. Trộn điểm và tính toán RRF Weighted Score
        List<Map<String, Object>> graphCf = graphCfFuture.join();
        List<String> semanticDocs = semanticFuture.join();
        List<Map<String, Object>> classDocs = classBasedFuture.join();
        List<Document> trendingDocs = trendingFuture.join();

        List<Map<String, Object>> activeClasses = new ArrayList<>();
        List<Map<String, Object>> pastClasses = new ArrayList<>();
        for (Map<String, Object> map : classDocs) {
            String status = (String) map.get("classStatus");
            if ("COMPLETED".equals(status)) {
                pastClasses.add(map);
            } else {
                activeClasses.add(map);
            }
        }

        double alpha4 = alpha3 * beta;

        Map<String, Double> scores = new HashMap<>();
        Map<String, RecommendationItem> itemMap = new HashMap<>();
        Map<String, Double> branchMaxScores = new HashMap<>();

        // Helper function to calculate score and update reason if it's the dominant branch
        java.util.function.Consumer<Object[]> processBranch = (args) -> {
            String docId = (String) args[0];
            Double branchScore = (Double) args[1];
            RecommendationItem item = (RecommendationItem) args[2];
            
            scores.merge(docId, branchScore, Double::sum);
            Double currentMax = branchMaxScores.getOrDefault(docId, -1.0);
            if (branchScore > currentMax) {
                branchMaxScores.put(docId, branchScore);
                itemMap.put(docId, item);
            }
        };

        // Branch 1.1: Graph CF
        for (int i = 0; i < graphCf.size(); i++) {
            Map<String, Object> map = graphCf.get(i);
            String docId = (String) map.get("recommendedDocId");
            double s = alpha1 * (61.0 / (60.0 + i + 1));
            processBranch.accept(new Object[]{docId, s, RecommendationItem.builder()
                .docId(docId)
                .triggerId((String) map.get("reasonTriggerId"))
                .reason(RecommendationReason.builder().type("DOWNLOADED").build())
                .build()});
        }

        // Branch 1.2: Semantic
        for (int i = 0; i < semanticDocs.size(); i++) {
            String docId = semanticDocs.get(i);
            double s = alpha2 * (61.0 / (60.0 + i + 1));
            processBranch.accept(new Object[]{docId, s, RecommendationItem.builder()
                .docId(docId)
                .reason(RecommendationReason.builder().type("SIMILAR").build())
                .build()});
        }

        // Branch 2.1: Active Class
        for (int i = 0; i < activeClasses.size(); i++) {
            Map<String, Object> map = activeClasses.get(i);
            String docId = (String) map.get("recommendedDocId");
            double s = alpha3 * (61.0 / (60.0 + i + 1));
            processBranch.accept(new Object[]{docId, s, RecommendationItem.builder()
                .docId(docId)
                .triggerId((String) map.get("reasonTriggerId"))
                .reason(RecommendationReason.builder().type("ACTIVE_CLASS").build())
                .build()});
        }

        // Branch 2.2: Past Class
        for (int i = 0; i < pastClasses.size(); i++) {
            Map<String, Object> map = pastClasses.get(i);
            String docId = (String) map.get("recommendedDocId");
            double s = alpha4 * (61.0 / (60.0 + i + 1));
            processBranch.accept(new Object[]{docId, s, RecommendationItem.builder()
                .docId(docId)
                .triggerId((String) map.get("reasonTriggerId"))
                .reason(RecommendationReason.builder().type("PAST_CLASS").build())
                .build()});
        }

        // Branch 3.1: Trending
        for (int i = 0; i < trendingDocs.size(); i++) {
            String docId = trendingDocs.get(i).getId();
            double s = alpha5 * (61.0 / (60.0 + i + 1));
            processBranch.accept(new Object[]{docId, s, RecommendationItem.builder()
                .docId(docId)
                .reason(RecommendationReason.builder().type("TRENDING").build())
                .build()});
        }

        List<String> sortedIds = scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        long endTime = System.currentTimeMillis();
        log.info("[HYBRID PARALLEL] getForYouFeed executed in {} ms. Total unique items: {}", (endTime - startTime), sortedIds.size());

        if (log.isDebugEnabled() && !sortedIds.isEmpty()) {
            for (int i = 0; i < Math.min(3, sortedIds.size()); i++) {
                String docId = sortedIds.get(i);
                
                int rank11 = -1;
                for (int j=0; j<graphCf.size(); j++) if(docId.equals(graphCf.get(j).get("recommendedDocId"))) { rank11 = j+1; break; }
                int rank12 = semanticDocs.indexOf(docId) >= 0 ? semanticDocs.indexOf(docId) + 1 : -1;
                int rank21 = -1;
                for (int j=0; j<activeClasses.size(); j++) if(docId.equals(activeClasses.get(j).get("recommendedDocId"))) { rank21 = j+1; break; }
                int rank22 = -1;
                for (int j=0; j<pastClasses.size(); j++) if(docId.equals(pastClasses.get(j).get("recommendedDocId"))) { rank22 = j+1; break; }
                int rank31 = -1;
                for (int j=0; j<trendingDocs.size(); j++) if(docId.equals(trendingDocs.get(j).getId())) { rank31 = j+1; break; }

                String debugStr = String.format("[DEBUG] DocID: %s | Rank_1.1: %s (S: %.2f) | Rank_1.2: %s (S: %.2f) | Rank_2.1: %s (S: %.2f) | Rank_2.2: %s (S: %.2f) | Rank_3.1: %s (S: %.2f) | Final Score: %.2f",
                    docId,
                    rank11 > 0 ? String.valueOf(rank11) : "NULL", rank11 > 0 ? alpha1 * (61.0 / (60.0 + rank11)) : 0.0,
                    rank12 > 0 ? String.valueOf(rank12) : "NULL", rank12 > 0 ? alpha2 * (61.0 / (60.0 + rank12)) : 0.0,
                    rank21 > 0 ? String.valueOf(rank21) : "NULL", rank21 > 0 ? alpha3 * (61.0 / (60.0 + rank21)) : 0.0,
                    rank22 > 0 ? String.valueOf(rank22) : "NULL", rank22 > 0 ? alpha4 * (61.0 / (60.0 + rank22)) : 0.0,
                    rank31 > 0 ? String.valueOf(rank31) : "NULL", rank31 > 0 ? alpha5 * (61.0 / (60.0 + rank31)) : 0.0,
                    scores.get(docId)
                );
                log.debug(debugStr);
            }
        }

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), sortedIds.size());

        if (start >= sortedIds.size()) return new PageImpl<>(List.of(), pageable, sortedIds.size());

        List<RecommendationItem> items = sortedIds.subList(start, end).stream()
                .map(itemMap::get)
                .toList();

        return new PageImpl<>(items, pageable, sortedIds.size());
    }
}
