package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lightweight retrieval pipeline regression (no Spring / MySQL / Qdrant).
 */
class RagRetrievalServiceE2ETest {

    @Test
    void scopedCurriculumQuery_prefersExactKhóaAndHocKy() {
        String query = "Ngành Kiến trúc K46 có những học phần nào ở học kỳ 2?";

        DocumentChunk rowA = curriculumRow(
                "row-a",
                "Kiến trúc K46", "HK2", "KTR2082", "Quản lý đô thị");
        DocumentChunk rowB = curriculumRow(
                "row-b",
                "Kiến trúc K46", "HK1", "KQH3102", "Du lịch và di sản đô thị");
        DocumentChunk rowC = curriculumRow(
                "row-c",
                "Kiến trúc K45", "HK2", "KTR3319", "Đồ án tốt nghiệp");

        List<DocumentChunk> ranked = rankChunks(query, List.of(rowB, rowC, rowA));
        assertEquals(rowA.getId(), ranked.get(0).getId());
        assertTrue(indexOf(ranked, rowA.getId()) < indexOf(ranked, rowB.getId()));
        assertTrue(indexOf(ranked, rowA.getId()) < indexOf(ranked, rowC.getId()));

        List<DocumentChunk> selected = RagRetrievalService.selectTopNByScoreWithBudget(
                toScored(ranked), 2, 50_000, query).chunks();
        assertTrue(selected.stream().anyMatch(c -> rowA.getId().equals(c.getId())));
    }

    @Test
    void scheduleQuery_prefersExactGroupOverWrongGroup() {
        String query = "Kỹ năng mềm Nhóm 4 học phòng nào?";

        DocumentChunk group2 = scheduleRow("g2", "KNM1013", "Kỹ năng mềm - Nhóm 2", "B201");
        DocumentChunk group4 = scheduleRow("g4", "KNM1013", "Kỹ năng mềm - Nhóm 4", "B301");

        List<DocumentChunk> ranked = rankChunks(query, List.of(group2, group4));
        assertEquals(group4.getId(), ranked.get(0).getId());
        assertTrue(indexOf(ranked, group4.getId()) < indexOf(ranked, group2.getId()));
    }

    @Test
    void pipeline_extractsSignals_considersKeywordAndCellAwareScoring() {
        String query = "LUA1012 nhóm 1 phòng";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);

        assertFalse(signals.identifiers().isEmpty());
        assertTrue(KeywordSearchService.isTableLikeQuery(signals, query));

        DocumentChunk row = scheduleRow("lua", "LUA1012", "Pháp luật - Nhóm 1", "E301");
        double keywordScore = new KeywordSearchService(null, null)
                .scoreChunk(row, signals, new java.util.LinkedHashMap<>(), query);
        CellAwareTableRowScorer.CellAwareScore cellScore =
                CellAwareTableRowScorer.score(row, signals, query);

        assertTrue(keywordScore > 0 || cellScore.total() > 0);
    }

    @Test
    void finalSelection_demotesWrongScopeRows_whenTopNIsTight() {
        String query = "Ngành Kiến trúc K46 học kỳ 2 mã KTR2082";
        DocumentChunk exact = curriculumRow("exact", "Kiến trúc K46", "HK2", "KTR2082", "Quản lý đô thị");
        DocumentChunk wrongSemester = curriculumRow("wrong-hk", "Kiến trúc K46", "HK1", "KQH3102", "Du lịch");
        DocumentChunk wrongCohort = curriculumRow("wrong-k", "Kiến trúc K45", "HK2", "KTR3319", "Đồ án");

        List<RagRetrievalService.ScoredChunk> scored = toScored(
                rankChunks(query, List.of(wrongCohort, wrongSemester, exact)));

        List<DocumentChunk> top1 = RagRetrievalService.selectTopNByScoreWithBudget(scored, 1, 50_000, query).chunks();
        assertEquals(1, top1.size());
        assertEquals(exact.getId(), top1.get(0).getId());
    }

    private static List<DocumentChunk> rankChunks(String question, List<DocumentChunk> input) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        List<RagRetrievalService.ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : input) {
            double cell = CellAwareTableRowScorer.score(chunk, signals, question).total();
            double keyword = new KeywordSearchService(null, null)
                    .scoreChunk(chunk, signals, java.util.Map.of(), question);
            scored.add(new RagRetrievalService.ScoredChunk(chunk, cell + keyword));
        }
        scored = RagRetrievalService.applyCellAwareScoreBoost(question, scored);
        scored = RagRetrievalService.applyTableRowPriorityAdjustments(question, scored);
        scored = RagRetrievalService.demoteLeakyTextCandidates(question, scored);
        return scored.stream()
                .sorted(Comparator.comparingDouble(RagRetrievalService.ScoredChunk::finalScore).reversed())
                .map(RagRetrievalService.ScoredChunk::chunk)
                .toList();
    }

    private static List<RagRetrievalService.ScoredChunk> toScored(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(c -> new RagRetrievalService.ScoredChunk(c, 1.0))
                .toList();
    }

    private static DocumentChunk curriculumRow(
            String suffix,
            String khoaNganh,
            String hocKy,
            String maHp,
            String tenHp) {
        return DocumentChunk.builder()
                .id(UUID.nameUUIDFromBytes(("cur-" + suffix).getBytes()))
                .chunkType("normalized_table_row")
                .cellsJson("{\"Khóa ngành\":\"" + khoaNganh + "\",\"Học kỳ\":\"" + hocKy
                        + "\",\"Mã học phần\":\"" + maHp + "\",\"Tên học phần\":\"" + tenHp + "\"}")
                .content(maHp + " " + tenHp)
                .build();
    }

    private static DocumentChunk scheduleRow(String suffix, String maHp, String tenLop, String phong) {
        return DocumentChunk.builder()
                .id(UUID.nameUUIDFromBytes(("sched-" + suffix).getBytes()))
                .chunkType("normalized_table_row")
                .cellsJson("{\"Mã học phần\":\"" + maHp + "\",\"Tên lớp học phần\":\"" + tenLop
                        + "\",\"Phòng\":\"" + phong + "\"}")
                .content(maHp + " " + tenLop + " " + phong)
                .build();
    }

    private static int indexOf(List<DocumentChunk> chunks, UUID id) {
        for (int i = 0; i < chunks.size(); i++) {
            if (id.equals(chunks.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }
}
