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

    @Value("${app.hybrid.weight-semantic:0.4}")
    double weightSemantic;

    @Value("${app.hybrid.weight-cf:0.6}")
    double weightCf;

    static final int RRF_K = 60; // Hằng số phổ biến trong Reciprocal Rank Fusion

    public Page<String> getHybridRelatedDocumentIds(
            String docId, String vectorStr, String queryString, Pageable pageable) {

        // Lấy top 50 từ Semantic Search (PGVector)
        Page<String> semanticPage =
                documentRepository.findRelatedDocumentIds(vectorStr, queryString, docId, PageRequest.of(0, 50));
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
            return documentRepository.findRelatedDocumentIds(vectorStr, queryString, docId, pageable);
        }

        // Tính rrf score
        Map<String, Double> finalScores = new HashMap<>();

        // Add semantic RRF
        for (int i = 0; i < semanticDocs.size(); i++) {
            String id = semanticDocs.get(i);
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(id, weightSemantic * rrf, Double::sum);
        }

        // Add CF RRF
        for (int i = 0; i < cfDocs.size(); i++) {
            String id = cfDocs.get(i);
            double rrf = 1.0 / (RRF_K + i + 1);
            finalScores.merge(id, weightCf * rrf, Double::sum);
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
        // Lấy từ user-based CF
        List<Map<String, Object>> cfResults;
        try {
            cfResults = neo4jRepository.findUserBasedCFRecommendations(userId, pageable.getPageSize());
        } catch (Exception e) {
            cfResults = new ArrayList<>();
        }

        List<String> cfDocs = cfResults.stream()
                .map(map -> (String) map.get("recommendedDocId"))
                .filter(Objects::nonNull)
                .toList();

        if (!cfDocs.isEmpty()) {
            return new PageImpl<>(cfDocs, pageable, cfDocs.size());
        }

        // Fallback Logic: Lấy Top Trending (RankingScore) nếu feed rỗng (người dùng mới)
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(90);
        List<String> fallbackIds = documentRepository.findRecentDocumentsOrderByRankingScore(since, pageable).stream()
                .map(vn.edu.hcmut.document.entity.Document::getId)
                .toList();

        return new PageImpl<>(fallbackIds, pageable, fallbackIds.size());
    }
}
