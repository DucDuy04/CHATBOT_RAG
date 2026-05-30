package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellAwareNormalizedRowRetrievalTest {

    @Test
    void score_normalizedRow_exactIdentifierInCells_boostsScore() {
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkType("normalized_table_row")
                .cellsJson("{\"Mã học phần\":\"LUA1012\",\"Nhóm\":\"Nhóm 1\"}")
                .content("canonical")
                .build();

        KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.QuerySignals signals = KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.extract("LUA1012 nhóm 1");

        CellAwareTableRowScorer.CellAwareScore score = CellAwareTableRowScorer.score(
                chunk, signals, "LUA1012 nhóm 1");

        assertTrue(score.total() > 0);
        assertTrue(score.exactIdentifierCellScore() > 0);
    }

    @Test
    void score_nonTableChunk_returnsZero() {
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkType("text")
                .content("plain")
                .build();

        CellAwareTableRowScorer.CellAwareScore score = CellAwareTableRowScorer.score(
                chunk,
                KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.extract("LUA1012"),
                "LUA1012");

        assertEquals(0.0, score.total());
    }

    @Test
    void score_emptyCellsJson_returnsZero() {
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkType("normalized_table_row")
                .cellsJson("{}")
                .build();

        CellAwareTableRowScorer.CellAwareScore score = CellAwareTableRowScorer.score(
                chunk,
                KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.extract("anything"),
                "anything");

        assertEquals(0.0, score.total());
    }

    @Test
    void isCompareQuery_detectsVietnameseComparePhrase() {
        KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.QuerySignals signals = KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor.extract("So sánh nhóm 2 và nhóm 4");
        assertTrue(CellAwareTableRowScorer.isCompareQuery("So sánh nhóm 2 và nhóm 4", signals));
    }
}
