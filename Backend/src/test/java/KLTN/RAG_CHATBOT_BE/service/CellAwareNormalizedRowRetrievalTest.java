package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CellAwareNormalizedRowRetrievalTest {

    private KeywordSearchService keywordSearch;
    private static final Document DOC = Document.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        keywordSearch = new KeywordSearchService(null);
    }

    @Test
    void exactLabelBoundary_group2BeatsGroup18() {
        String q = "Item Group 2 location?";
        DocumentChunk rowA = rowChunk(1, Map.of("Name", "Item", "Group", "2", "Location", "R101"));
        DocumentChunk rowB = rowChunk(2, Map.of("Name", "Item", "Group", "18", "Location", "R202"));

        assertThat(keywordScore(rowA, q)).isGreaterThan(keywordScore(rowB, q));
    }

    @Test
    void identifierExact_abc123BeatsAbc124() {
        String q = "ABC123 value?";
        DocumentChunk rowA = rowChunk(1, Map.of("Key", "ABC123", "Value", "Alpha"));
        DocumentChunk rowB = rowChunk(2, Map.of("Key", "ABC124", "Value", "Beta"));

        assertThat(keywordScore(rowA, q)).isGreaterThan(keywordScore(rowB, q));
    }

    @Test
    void multiSignalSameRow_itemAGroup2Wins() {
        String q = "Item A Group 2";
        DocumentChunk rowA = rowChunk(1, Map.of("Name", "Item A", "Group", "2", "Location", "R101"));
        DocumentChunk rowB = rowChunk(2, Map.of("Name", "Item A", "Group", "18", "Location", "R202"));
        DocumentChunk rowC = rowChunk(3, Map.of("Name", "Item B", "Group", "2", "Location", "R103"));

        assertThat(keywordScore(rowA, q)).isGreaterThan(keywordScore(rowB, q));
        assertThat(keywordScore(rowA, q)).isGreaterThan(keywordScore(rowC, q));
    }

    @Test
    void compareCoverage_includesBothRuntimeLabels() {
        String q = "Item A Group 1 Group 2";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(q);

        DocumentChunk rowA = rowChunk(1, Map.of("Name", "Item A", "Group", "1", "Location", "R101"));
        DocumentChunk rowB = rowChunk(2, Map.of("Name", "Item A", "Group", "2", "Location", "R102"));
        DocumentChunk rowC = rowChunk(3, Map.of("Name", "Item A", "Group", "18", "Location", "R999"));

        List<RagRetrievalService.ScoredChunk> scored = List.of(
                new RagRetrievalService.ScoredChunk(rowC, 0.9),
                new RagRetrievalService.ScoredChunk(rowA, 0.7),
                new RagRetrievalService.ScoredChunk(rowB, 0.65));

        List<DocumentChunk> covered = RagRetrievalService.applyCompareLabelCoverage(
                List.of(rowC, rowA), scored, signals, 5);

        assertThat(covered).anyMatch(c -> c.getRowIndex() == 1);
        assertThat(covered).anyMatch(c -> c.getRowIndex() == 2);
        assertThat(covered).noneMatch(c -> c.getRowIndex() == 3);
    }

    @Test
    void columnIntent_usesRuntimeHeaderOverlap() {
        String q = "ABC123 Location?";
        DocumentChunk row = rowChunk(1, Map.of("Key", "ABC123", "Location", "R101", "Owner", "John"));
        CellAwareTableRowScorer.CellAwareScore cs =
                CellAwareTableRowScorer.score(row, QuerySignalExtractor.extract(q), q);
        assertThat(cs.columnNameIntentBoost()).isGreaterThan(0);
        assertThat(cs.exactIdentifierCellScore()).isGreaterThan(0);
    }

    @Test
    void summaryDownrank_normalizedRowBeatsTableLikeSummary() {
        String q = "ABC123 value?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(q);

        DocumentChunk normalized = rowChunk(1, Map.of("Key", "ABC123", "Value", "Alpha"));
        String summaryContent = "ABC123 DEF456 | x | y |\n".repeat(20);
        DocumentChunk summary = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(DOC)
                .chunkType("section_summary")
                .content(summaryContent)
                .build();

        List<RagRetrievalService.ScoredChunk> adjusted =
                RagRetrievalService.applyTableRowPriorityAdjustments(q, List.of(
                        new RagRetrievalService.ScoredChunk(summary, 0.85),
                        new RagRetrievalService.ScoredChunk(normalized, 0.80)));

        double normScore = adjusted.stream()
                .filter(s -> "normalized_table_row".equals(s.chunk().getChunkType()))
                .mapToDouble(RagRetrievalService.ScoredChunk::finalScore)
                .findFirst().orElse(0);
        double sumScore = adjusted.stream()
                .filter(s -> "section_summary".equals(s.chunk().getChunkType()))
                .mapToDouble(RagRetrievalService.ScoredChunk::finalScore)
                .findFirst().orElse(0);

        assertThat(normScore).isGreaterThan(sumScore);
        assertThat(CellAwareTableRowScorer.hasExactCellMatch(normalized, signals)).isTrue();
    }

    @Test
    void oosFakeIdentifier_noFalseHighConfidence() {
        String q = "ZZZ999 value?";
        DocumentChunk row = rowChunk(1, Map.of("Key", "ABC123", "Value", "Alpha"));
        CellAwareTableRowScorer.CellAwareScore cs =
                CellAwareTableRowScorer.score(row, QuerySignalExtractor.extract(q), q);
        assertThat(cs.exactIdentifierCellScore()).isZero();
        assertThat(cs.total()).isLessThan(3.0);
    }

    @Test
    void parseCells_requiresStructuredCells() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(DOC)
                .chunkType("normalized_table_row")
                .content("""
                        Table: Schedule.
                        Row: 1.
                        Name: Item A.
                        Group: 2.
                        Location: R101.
                        """)
                .build();

        assertThat(CellAwareTableRowScorer.parseCells(chunk)).isEmpty();
    }

    @Test
    void valueBoundaryDoesNotMatchSubstring() {
        assertThat(CellAwareTableRowScorer.valueMatchesBoundary("label 12", "label", "2")).isFalse();
        assertThat(CellAwareTableRowScorer.valueMatchesBoundary("label 2", "label", "2")).isTrue();
    }

    private double keywordScore(DocumentChunk chunk, String question) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        return keywordSearch.scoreChunk(chunk, signals, Map.of(), question);
    }

    private static DocumentChunk rowChunk(int rowIndex, Map<String, String> cells) {
        String json = NormalizedTableService.cellsToJson(new LinkedHashMap<>(cells));
        String canonical = NormalizedTableService.buildCanonicalText(
                "GenericTable", rowIndex, cells, null, 1);
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(DOC)
                .chunkType("normalized_table_row")
                .content(canonical)
                .rowIndex(rowIndex)
                .cellsJson(json)
                .tableName("GenericTable")
                .pageStart(1)
                .pageEnd(1)
                .build();
    }
}
