package vn.edu.hcmut.document.service;

import java.util.*;

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
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.neo4j.DocumentNeo4jRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentRecommendationService {

    final DocumentRepository documentRepository;
    final DocumentNeo4jRepository neo4jRepository;

    @Value("${app.hybrid.threshold-words:10}")
    int thresholdWords;

    @Value("${app.hybrid.weight-semantic-short:0.2}")
    double weightSemanticShort;

    @Value("${app.hybrid.weight-cf-short:0.8}")
    double weightCfShort;

    @Value("${app.hybrid.weight-semantic-long:0.7}")
    double weightSemanticLong;

    @Value("${app.hybrid.weight-cf-long:0.3}")
    double weightCfLong;

    static final int RRF_K = 60; // Hằng số phổ biến trong Reciprocal Rank Fusion

    public Page<RecommendationItem> getHybridRelatedDocumentIds(
            String context, String docId, String vectorStr, Pageable pageable) {

        // Lấy top 50 từ Semantic Search (PGVector)
        Page<String> semanticPage =
                documentRepository.findRelatedDocumentIds(vectorStr, context, docId, PageRequest.of(0, 50));
        List<String> semanticDocs = semanticPage.getContent();

        // Lấy top 50 từ Item-based CF (Neo4j)
        List<Map<String, Object>> cfResults;
        try {
            cfResults = neo4jRepository.findItemBasedCFRecommendations(docId, 50);
        } catch (Exception e) {
            cfResults = new ArrayList<>();
        }

        List<String> cfDocs = cfResults.stream()
                .map(map -> (String) map.get("recommendedDocId"))
                .filter(Objects::nonNull)
                .toList();

        // Nếu CF không có kết quả, fallback hoàn toàn bằng Semantic
        if (cfDocs.isEmpty()) {
            Page<String> ids = documentRepository.findRelatedDocumentIds(vectorStr, context, docId, pageable);
            return ids.map(id -> RecommendationItem.builder()
                    .docId(id)
                    .reason(vn.edu.hcmut.document.dto.response.RecommendationReason.builder()
                            .type("SIMILAR")
                            .build())
                    .build());
        }

        // --- CONTEXTUAL HYBRID LOGIC ---
        int wordCount =
                (context == null || context.isBlank()) ? 0 : context.trim().split("\\s+").length;
        double dynamicWeightSemantic;
        double dynamicWeightCf;

        if (wordCount > thresholdWords) {
            dynamicWeightSemantic = weightSemanticLong;
            dynamicWeightCf = weightCfLong;
        } else {
            dynamicWeightSemantic = weightSemanticShort;
            dynamicWeightCf = weightCfShort;
        }

        // Tính rrf score
        Map<String, Double> finalScores = new HashMap<>();

        // Add semantic RRF
        for (int i = 0; i < semanticDocs.size(); i++) {
            String id = semanticDocs.get(i);
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(id, dynamicWeightSemantic * rrf, Double::sum);
        }

        // Add CF RRF
        for (int i = 0; i < cfDocs.size(); i++) {
            String id = cfDocs.get(i);
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(id, dynamicWeightCf * rrf, Double::sum);
        }

        // Sort theo score giảm dần
        List<String> hybridRankedIds = finalScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        // Paginate kết quả in-memory
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), hybridRankedIds.size());

        if (start > hybridRankedIds.size() || start < 0) {
            return new PageImpl<>(List.of(), pageable, hybridRankedIds.size());
        }

        List<RecommendationItem> items = hybridRankedIds.subList(start, end).stream()
                .map(id -> RecommendationItem.builder()
                        .docId(id)
                        .reason(vn.edu.hcmut.document.dto.response.RecommendationReason.builder()
                                .type("SIMILAR")
                                .build())
                        .build())
                .toList();

        return new PageImpl<>(items, pageable, hybridRankedIds.size());
    }

    public Page<RecommendationItem> getForYouFeed(String userId, Pageable pageable) {
        int poolSize = 200;
        Map<String, RecommendationItem> recommendedItems = new LinkedHashMap<>();

        // Layer 1: User-based CF
        if (userId != null) {
            try {
                List<Map<String, Object>> cfResults = neo4jRepository.findUserBasedCFRecommendations(userId, poolSize);
                for (Map<String, Object> map : cfResults) {
                    String docId = (String) map.get("recommendedDocId");
                    if (docId != null && !recommendedItems.containsKey(docId)) {
                        recommendedItems.put(
                                docId,
                                RecommendationItem.builder()
                                        .docId(docId)
                                        .reason(vn.edu.hcmut.document.dto.response.RecommendationReason.builder()
                                                .type((String) map.get("reasonType"))
                                                .title((String) map.get("reasonTriggerId"))
                                                .build())
                                        .build());
                    }
                }
            } catch (Exception e) {
                // Log
            }
        }

        // Layer 2: Topics & Classes
        if (recommendedItems.size() < poolSize && userId != null) {
            try {
                List<Map<String, Object>> topicResults =
                        neo4jRepository.findColdStartRecommendationsByTopics(userId, poolSize);
                for (Map<String, Object> map : topicResults) {
                    String docId = (String) map.get("recommendedDocId");
                    if (docId != null && !recommendedItems.containsKey(docId)) {
                        recommendedItems.put(
                                docId,
                                RecommendationItem.builder()
                                        .docId(docId)
                                        .reason(vn.edu.hcmut.document.dto.response.RecommendationReason.builder()
                                                .type((String) map.get("reasonType"))
                                                .title((String) map.get("reasonTriggerId"))
                                                .build())
                                        .build());
                        if (recommendedItems.size() >= poolSize) break;
                    }
                }
            } catch (Exception e) {
                // Log
            }
        }

        // Layer 3: Trending
        if (recommendedItems.size() < poolSize) {
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(90);
            List<vn.edu.hcmut.document.entity.Document> trendingDocs =
                    documentRepository.findRecentDocumentsOrderByTrendingScore(since, PageRequest.of(0, poolSize));
            for (vn.edu.hcmut.document.entity.Document doc : trendingDocs) {
                if (!recommendedItems.containsKey(doc.getId())) {
                    recommendedItems.put(
                            doc.getId(),
                            RecommendationItem.builder()
                                    .docId(doc.getId())
                                    .reason(vn.edu.hcmut.document.dto.response.RecommendationReason.builder()
                                            .type("TRENDING")
                                            .build())
                                    .build());
                    if (recommendedItems.size() >= poolSize) break;
                }
            }
        }

        List<RecommendationItem> finalItems = new ArrayList<>(recommendedItems.values());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), finalItems.size());

        if (start >= finalItems.size() || start < 0) {
            return new PageImpl<>(List.of(), pageable, finalItems.size());
        }

        List<RecommendationItem> pagedItems = finalItems.subList(start, end);
        return new PageImpl<>(pagedItems, pageable, finalItems.size());
    }
}
