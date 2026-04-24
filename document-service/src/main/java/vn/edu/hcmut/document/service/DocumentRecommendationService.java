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

    public Page<String> getHybridRelatedDocumentIds(String context, String docId, String vectorStr, Pageable pageable) {

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
            return documentRepository.findRelatedDocumentIds(vectorStr, context, docId, pageable);
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

        List<String> pagedIds = hybridRankedIds.subList(start, end);
        return new PageImpl<>(pagedIds, pageable, hybridRankedIds.size());
    }

    public Page<String> getForYouFeed(String userId, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        Set<String> recommendedIds = new LinkedHashSet<>();

        // Layer 1: User-based CF (Community behavior)
        try {
            List<Map<String, Object>> cfResults = neo4jRepository.findUserBasedCFRecommendations(userId, pageSize);
            for (Map<String, Object> map : cfResults) {
                String docId = (String) map.get("recommendedDocId");
                if (docId != null) recommendedIds.add(docId);
            }
        } catch (Exception e) {
            // Log and continue to fallback
        }

        // Layer 2: Onboarding Topics & Enrolled Classes (Interest/Domain based) - COLD START SOLUTION
        if (recommendedIds.size() < pageSize) {
            try {
                List<Map<String, Object>> topicResults =
                        neo4jRepository.findColdStartRecommendationsByTopics(userId, pageSize);
                for (Map<String, Object> map : topicResults) {
                    String docId = (String) map.get("recommendedDocId");
                    if (docId != null) recommendedIds.add(docId);
                    if (recommendedIds.size() >= pageSize) break;
                }
            } catch (Exception e) {
                // Log and continue to fallback
            }
        }

        // Layer 3: General Trending (Global fallback)
        if (recommendedIds.size() < pageSize) {
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(90);
            List<vn.edu.hcmut.document.entity.Document> trendingDocs =
                    documentRepository.findRecentDocumentsOrderByRankingScore(since, PageRequest.of(0, pageSize));
            for (vn.edu.hcmut.document.entity.Document doc : trendingDocs) {
                recommendedIds.add(doc.getId());
                if (recommendedIds.size() >= pageSize) break;
            }
        }

        List<String> finalIds = new ArrayList<>(recommendedIds);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), finalIds.size());

        if (start > finalIds.size() || start < 0) {
            return new PageImpl<>(List.of(), pageable, finalIds.size());
        }

        List<String> pagedIds = finalIds.subList(start, end);
        return new PageImpl<>(pagedIds, pageable, finalIds.size());
    }
}
