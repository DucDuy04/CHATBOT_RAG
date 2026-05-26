package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 23G — Top-K as final context top-N after rerank/score selection.
 */
class FinalContextSelectionTest {

    private static final Document SHARED_DOC = Document.builder().id(UUID.randomUUID()).build();

    @Test
    void topKControlsFinalContextCount() {
        List<RagRetrievalService.ScoredChunk> scored = buildScoredPool(25, 0.5);
        List<DocumentChunk> selected = RagRetrievalService.selectTopNByScore(scored, 5, 32000);
        assertTrue(selected.size() <= 5);
    }

    @Test
    void rerankBeforeBudget_selectsHighScoreLateDocumentChunks() {
        List<RagRetrievalService.ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            double score = i < 20 ? 0.1 : 1.0;
            scored.add(new RagRetrievalService.ScoredChunk(mockChunk(i, "sec_" + i), score));
        }
        scored.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));

        List<DocumentChunk> selected = RagRetrievalService.selectTopNByScore(scored, 5, 32000);
        assertEquals(5, selected.size());
        assertTrue(selected.stream().anyMatch(c -> c.getSectionId().equals("sec_24")));
        assertTrue(selected.stream().anyMatch(c -> c.getSectionId().equals("sec_20")));
    }

    @Test
    void documentOrderAfterSelection() {
        List<DocumentChunk> selected = List.of(
                mockChunk(30, "sec_c"),
                mockChunk(10, "sec_a"),
                mockChunk(20, "sec_b")
        );
        List<DocumentChunk> ordered = RagRetrievalService.sortByDocumentOrder(selected, false);
        assertEquals("sec_a", ordered.get(0).getSectionId());
        assertEquals("sec_b", ordered.get(1).getSectionId());
        assertEquals("sec_c", ordered.get(2).getSectionId());
    }

    @Test
    void fixedVectorAnchorK_unaffectedByUiTopK() {
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(1));
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(20));
        assertEquals(1, RagRetrievalService.normalizeFinalContextTopN(1));
        assertEquals(20, RagRetrievalService.normalizeFinalContextTopN(20));
    }

    @Test
    void noFinalContextSelectorOrBudgetStopwords() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("KLTN.RAG_CHATBOT_BE.service.FinalContextSelector"));
    }

    @Test
    void resolveFinalContextTopN_userOverrideBeatsQueryTypeDefault() {
        int n = RagRetrievalService.resolveFinalContextTopN(
                5, QueryAnalyzerService.QueryType.LIST_ALL);
        assertEquals(5, n);
    }

    @Test
    void resolveFinalContextTopN_queryTypeDefaultWhenNull() {
        int listAll = RagRetrievalService.resolveFinalContextTopN(
                null, QueryAnalyzerService.QueryType.LIST_ALL);
        assertEquals(20, listAll);
        int normal = RagRetrievalService.resolveFinalContextTopN(
                null, QueryAnalyzerService.QueryType.NORMAL_FACT);
        assertEquals(RagRetrievalService.DEFAULT_FINAL_CONTEXT_TOP_N, normal);
    }

    @Test
    void adaptiveFinalContextTopN_factLikeUsesLowerEffectiveTopN() {
        int n = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "Which room does item ABC123 use?",
                15,
                QueryAnalyzerService.QueryType.NORMAL_FACT);
        assertEquals(7, n);
    }

    @Test
    void adaptiveFinalContextTopN_listLikeCanUseHigherEffectiveTopN() {
        int n = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "List all entries in this table",
                15,
                QueryAnalyzerService.QueryType.LIST_ALL);
        assertEquals(15, n);
    }

    @Test
    void adaptiveFinalContextTopN_neverExceedsRequestedTopN() {
        int n = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "Which value matches ABC123?",
                5,
                QueryAnalyzerService.QueryType.TABLE_LOOKUP);
        assertEquals(5, n);
    }

    @Test
    void adaptiveFinalContextTopN_multiAttributeLookupKeepsRequestedCeiling() {
        int n = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "Which teacher, day, period, and room does group 2 use?",
                15,
                QueryAnalyzerService.QueryType.TABLE_LOOKUP);
        assertEquals(15, n);
    }

    @Test
    void twoStageScoring_preservesTopExactNormalizedRow() {
        DocumentChunk exact = rowChunk(1, Map.of("Code", "ABC123", "Group", "2", "Room", "R101"));
        DocumentChunk nearby = rowChunk(2, Map.of("Code", "ABC124", "Group", "2", "Room", "R202"));
        DocumentChunk text = mockChunk(3, "sec_text");
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("ABC123 group 2 room?");

        List<RagRetrievalService.CheapScoredCandidate> cheap =
                RagRetrievalService.cheapPreScoreCandidates(
                        List.of(nearby, text, exact),
                        signals,
                        java.util.Set.of(),
                        Map.of(exact.getId(), 1.0, nearby.getId(), 0.4));
        List<DocumentChunk> subset = RagRetrievalService.selectExpensiveScoringSubset(
                cheap, new RagRetrievalService.ScoringBudget(2, 1, "fact_like"), Map.of(exact.getId(), 1.0));

        assertEquals(exact.getId(), subset.getFirst().getId());
        assertTrue(subset.size() <= 2);
    }

    @Test
    void expensiveScoringSubset_isBoundedAndKeepsSafetyTail() {
        List<DocumentChunk> rows = new ArrayList<>();
        Map<UUID, Double> keywordScores = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            DocumentChunk row = rowChunk(i, Map.of("Code", i == 0 ? "ABC123" : "ZZZ" + i, "Value", "V" + i));
            rows.add(row);
            if (i % 5 == 0) {
                keywordScores.put(row.getId(), 0.6);
            }
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("ABC123 value?");
        List<RagRetrievalService.CheapScoredCandidate> cheap =
                RagRetrievalService.cheapPreScoreCandidates(rows, signals, java.util.Set.of(), keywordScores);

        List<DocumentChunk> subset = RagRetrievalService.selectExpensiveScoringSubset(
                cheap, new RagRetrievalService.ScoringBudget(5, 2, "fact_like"), keywordScores);

        assertTrue(subset.size() <= 5);
        assertTrue(subset.stream().anyMatch(c -> c.getCellsJson().contains("ABC123")));
        assertTrue(subset.size() > 1, "safety tail should keep more than only the exact row");
    }

    @Test
    void exactIdentifierDiversity_keepsAlternativeTableSafetyTail() {
        DocumentChunk primary = rowChunk(1, Map.of("Code", "ABC123", "Value", "Alpha"));
        DocumentChunk sameTable = rowChunk(2, Map.of("Code", "ABC123", "Value", "Alpha duplicate"));
        DocumentChunk alternative = rowChunk(3, Map.of("Code", "ABC123", "Value", "Alpha cleaner"));
        sameTable.setSectionId(primary.getSectionId());
        sameTable.setTableName(primary.getTableName());
        alternative.setSectionId("other_section");
        alternative.setTableName("OtherTable");
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("ABC123 value?");

        List<DocumentChunk> diversified = RagRetrievalService.applyExactIdentifierSourceDiversity(
                List.of(primary, sameTable),
                List.of(
                        new RagRetrievalService.ScoredChunk(primary, 1.0),
                        new RagRetrievalService.ScoredChunk(sameTable, 0.9),
                        new RagRetrievalService.ScoredChunk(alternative, 0.8)),
                signals,
                2);

        assertTrue(diversified.stream().anyMatch(c -> "OtherTable".equals(c.getTableName())));
        assertEquals(2, diversified.size());
    }

    @Test
    void exactIdentifierQuery_usesSmallerBudgetThanCompareAndList() {
        var exactBudget = RagRetrievalService.resolveScoringBudget(
                "ABC123 value?", QuerySignalExtractor.extract("ABC123 value?"), 500);
        var compareBudget = RagRetrievalService.resolveScoringBudget(
                "Compare item group 1 and group 2", QuerySignalExtractor.extract("Compare item group 1 and group 2"), 500);
        var listBudget = RagRetrievalService.resolveScoringBudget(
                "List all entries for period 2", QuerySignalExtractor.extract("List all entries for period 2"), 500);

        assertTrue(exactBudget.expensiveLimit() < compareBudget.expensiveLimit());
        assertTrue(compareBudget.expensiveLimit() <= listBudget.expensiveLimit());
    }

    @Test
    void weakOosQuery_usesSmallBudget() {
        var budget = RagRetrievalService.resolveScoringBudget(
                "", QuerySignalExtractor.extract(""), 500);

        assertEquals(60, budget.expensiveLimit());
    }

    @Test
    void latencyTrace_scoringFieldsCanBePopulated() throws Exception {
        RagLatencyTrace trace = RagLatencyTrace.begin();
        try {
            trace.setMergeCandidateStats(30, 120, 180, 150);
            trace.setScoringCandidateStats(150, 40, 25, 110);

            assertEquals(150, intField(trace, "cheapPreScoreCandidates"));
            assertEquals(40, intField(trace, "expensiveCellAwareCandidates"));
            assertEquals(25, intField(trace, "finalRerankCandidates"));
            assertEquals(110, intField(trace, "keywordCandidatesDroppedByCheapGate"));
        } finally {
            trace.close();
        }
    }

    private static List<RagRetrievalService.ScoredChunk> buildScoredPool(int size, double baseScore) {
        List<RagRetrievalService.ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            scored.add(new RagRetrievalService.ScoredChunk(
                    mockChunk(i, "sec_" + i), baseScore + i * 0.01));
        }
        scored.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
        return scored;
    }

    private static DocumentChunk mockChunk(int orderIndex, String sectionId) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(SHARED_DOC)
                .orderIndex(orderIndex)
                .sectionOrder(orderIndex)
                .sectionId(sectionId)
                .content("content-" + sectionId)
                .chunkType("text")
                .build();
    }

    private static DocumentChunk rowChunk(int rowIndex, Map<String, String> cells) {
        String json = NormalizedTableService.cellsToJson(new LinkedHashMap<>(cells));
        String canonical = NormalizedTableService.buildCanonicalText(
                "GenericTable", rowIndex, cells, null, 1);
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(SHARED_DOC)
                .orderIndex(rowIndex)
                .sectionOrder(rowIndex)
                .sectionId("row_" + rowIndex)
                .chunkType("normalized_table_row")
                .content(canonical)
                .rowIndex(rowIndex)
                .cellsJson(json)
                .tableName("GenericTable")
                .build();
    }

    private static int intField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
