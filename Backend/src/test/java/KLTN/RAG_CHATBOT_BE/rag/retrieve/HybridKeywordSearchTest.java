package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridKeywordSearchTest {

    private static final UUID WIDGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    private KeywordSearchService keywordSearchService;

    @BeforeEach
    void setUp() {
        keywordSearchService = new KeywordSearchService(
                documentChunkRepository, new KeywordIndexCache(documentChunkRepository));
        ReflectionTestUtils.setField(keywordSearchService, "hybridEnabled", true);
        ReflectionTestUtils.setField(keywordSearchService, "keywordTopM", 5);
        ReflectionTestUtils.setField(keywordSearchService, "maxKeywordScanChunks", 500);
    }

    @Test
    void keywordOnlyCandidate_appearsWhenVectorMisses() {
        String query = "Kỹ năng mềm Nhóm 4 phòng nào";
        DocumentChunk target = normalizedRow(
                UUID.randomUUID(),
                "{\"Mã học phần\":\"KNM1013\",\"Tên lớp học phần\":\"Kỹ năng mềm - Nhóm 4\",\"Phòng\":\"B301\"}",
                "KNM1013 Kỹ năng mềm - Nhóm 4 B301");
        DocumentChunk noise = textChunk(UUID.randomUUID(), "unrelated prose about registration policy");

        when(documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID))
                .thenReturn(List.of(noise, target));

        KeywordSearchService.KeywordSearchResult result = keywordSearchService.search(query, WIDGET_ID);
        Set<UUID> hitIds = result.topChunks().stream()
                .map(sc -> sc.chunk().getId())
                .collect(Collectors.toSet());

        assertTrue(hitIds.contains(target.getId()), "keyword hit should include cells_json row");
        assertFalse(result.signals().contentTokens().isEmpty());
        assertTrue(containsUnicodeToken(result.signals().contentTokens(), "kỹ")
                || containsUnicodeToken(result.signals().ngrams(), "nang"));
    }

    @Test
    void vectorAndKeywordDuplicate_areDedupedWithBothSource() {
        UUID chunkId = UUID.randomUUID();
        DocumentChunk chunk = normalizedRow(chunkId, "{\"Phòng\":\"B301\"}", "B301");

        KeywordSearchService.KeywordSearchResult keywordResult =
                new KeywordSearchService.KeywordSearchResult(
                        List.of(new KeywordSearchService.ScoredKeywordChunk(chunk, 2.0, 0.9)),
                        Map.of(chunkId, 0.9),
                        QuerySignalExtractor.extract("B301"));

        List<KeywordSearchService.MergedCandidate> merged = KeywordSearchService.mergeCandidates(
                List.of(chunk),
                Set.of(chunkId),
                keywordResult);

        assertEquals(1, merged.size());
        assertEquals(KeywordSearchService.CandidateSource.BOTH, merged.get(0).source());
        assertTrue(merged.get(0).vectorScore() > 0);
        assertTrue(merged.get(0).keywordScore() > 0);
        assertEquals(1, KeywordSearchService.countBothSource(merged));
    }

    @Test
    void scopedCurriculumQuery_matchesCellsJsonWithUnicodePreserved() {
        String query = "Ngành Kiến trúc K46 học kỳ 2 có học phần nào?";
        DocumentChunk row = normalizedRow(
                UUID.randomUUID(),
                "{\"Khóa ngành\":\"Kiến trúc K46\",\"Học kỳ\":\"HK2\","
                        + "\"Mã học phần\":\"KTR2082\",\"Tên học phần\":\"Quản lý đô thị\"}",
                "KTR2082 Quản lý đô thị");

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        double score = keywordSearchService.scoreChunk(row, signals, Map.of(), query);

        assertTrue(score > 0, "curriculum row should be keyword-relevant");
        assertTrue(row.getCellsJson().contains("Kiến trúc K46"));
        assertTrue(query.contains("Kiến trúc"));
    }

    @Test
    void normalizedRow_groupContext_contributesToHaystackMatch() {
        String query = "Ngành Công nghệ thông tin LUA1012";
        DocumentChunk row = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .chunkType("normalized_table_row")
                .cellsJson("{\"Mã học phần\":\"LUA1012\"}")
                .content("LUA1012")
                .groupContext("Ngành: Công nghệ thông tin - Khóa K46")
                .build();

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        double score = keywordSearchService.scoreChunk(row, signals, Map.of(), query);

        assertTrue(score > 0);
        CellAwareTableRowScorer.CellAwareScore cellScore =
                CellAwareTableRowScorer.score(row, signals, query);
        assertTrue(cellScore.groupContextScore() > 0 || cellScore.total() > 0);
    }

    @Test
    void topK_isRespectedForKeywordSearch() {
        String query = "học phần mã";
        List<DocumentChunk> corpus = IntStream.range(0, 20)
                .mapToObj(i -> normalizedRow(
                        UUID.randomUUID(),
                        "{\"Mã học phần\":\"HP" + i + "\"}",
                        "học phần HP" + i))
                .collect(Collectors.toCollection(ArrayList::new));

        when(documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(any()))
                .thenReturn(corpus);

        KeywordSearchService.KeywordSearchResult result = keywordSearchService.search(query, WIDGET_ID);
        assertTrue(result.topChunks().size() <= 5, "keywordTopM=5 should cap results");
    }

    @Test
    void merge_keywordOnlyChunk_appearsWhenNotInVectorSet() {
        UUID keywordOnlyId = UUID.randomUUID();
        DocumentChunk keywordOnly = normalizedRow(keywordOnlyId, "{\"Phòng\":\"E301\"}", "E301");

        KeywordSearchService.KeywordSearchResult keywordResult =
                new KeywordSearchService.KeywordSearchResult(
                        List.of(new KeywordSearchService.ScoredKeywordChunk(keywordOnly, 1.5, 1.0)),
                        Map.of(keywordOnlyId, 1.0),
                        QuerySignalExtractor.extract("E301"));

        List<KeywordSearchService.MergedCandidate> merged = KeywordSearchService.mergeCandidates(
                List.of(),
                Set.of(),
                keywordResult);

        assertEquals(1, merged.size());
        assertEquals(KeywordSearchService.CandidateSource.KEYWORD, merged.get(0).source());
    }

    private static DocumentChunk normalizedRow(UUID id, String cellsJson, String content) {
        return DocumentChunk.builder()
                .id(id)
                .chunkType("normalized_table_row")
                .cellsJson(cellsJson)
                .content(content)
                .build();
    }

    private static DocumentChunk textChunk(UUID id, String content) {
        return DocumentChunk.builder()
                .id(id)
                .chunkType("text")
                .content(content)
                .build();
    }

    private static boolean containsUnicodeToken(List<String> tokens, String fragment) {
        return tokens.stream().anyMatch(t -> t != null && t.contains(fragment));
    }
}
