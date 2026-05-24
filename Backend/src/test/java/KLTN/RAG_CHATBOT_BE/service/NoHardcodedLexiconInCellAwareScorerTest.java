package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NoHardcodedLexiconInCellAwareScorerTest {

    private static final Document DOC = Document.builder().id(UUID.randomUUID()).build();

    @Test
    void productionCellAwareCodeHasNoFixedLexiconStructures() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/KLTN/RAG_CHATBOT_BE/service/CellAwareTableRowScorer.java"));

        assertThat(source).doesNotContain(
                "STRUCTURED_LABEL",
                "CANONICAL_META_PREFIXES",
                "COLUMN_INTENT_SYNONYMS",
                "switch (normType)",
                "case \"");

        List<String> stringLiterals = stringLiterals(source);
        assertThat(stringLiterals).doesNotContain(
                "nhom", "nh\u00f3m", "phong", "ph\u00f2ng", "giang vien", "gi\u1ea3ng vi\u00ean",
                "tiet", "ti\u1ebft", "tin chi", "t\u00edn ch\u1ec9", "hoc ky", "h\u1ecdc k\u1ef3",
                "ma hoc phan", "m\u00e3 h\u1ecdc ph\u1ea7n", "ten hoc phan", "t\u00ean h\u1ecdc ph\u1ea7n",
                "bang", "b\u1ea3ng", "dong", "d\u00f2ng", "trang",
                "group", "room", "teacher", "period", "credit", "code", "name", "date",
                "section", "chapter", "class");
    }

    @Test
    void labelPrefixComesFromRuntimeHeader() {
        String query = "Alpha Group 2 location?";
        DocumentChunk a = row(1, Map.of("Name", "Alpha", "Group", "2", "Location", "R101"));
        DocumentChunk b = row(2, Map.of("Name", "Alpha", "Group", "18", "Location", "R202"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
    }

    @Test
    void vietnameseRuntimeHeadersWorkWithoutProductionDictionary() {
        String query = "Alpha Nh\u00f3m 2 \u1edf \u0111\u00e2u?";
        DocumentChunk a = row(1, Map.of("T\u00ean", "Alpha", "Nh\u00f3m", "2", "Ph\u00f2ng", "R101"));
        DocumentChunk b = row(2, Map.of("T\u00ean", "Alpha", "Nh\u00f3m", "18", "Ph\u00f2ng", "R202"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
    }

    @Test
    void arbitraryRuntimeDomainWorks() {
        String query = "Policy Tier 3 limit?";
        DocumentChunk a = row(1, Map.of("Policy", "Policy", "Tier", "3", "Limit", "100"));
        DocumentChunk b = row(2, Map.of("Policy", "Policy", "Tier", "30", "Limit", "900"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
    }

    @Test
    void exactIdentifierWins() {
        String query = "ABC123 value?";
        DocumentChunk a = row(1, Map.of("Key", "ABC123", "Value", "Alpha"));
        DocumentChunk b = row(2, Map.of("Key", "ABC124", "Value", "Beta"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
    }

    @Test
    void numericBoundaryPreventsSubstringMatch() {
        String query = "Group 2";
        DocumentChunk a = row(1, Map.of("Group", "2", "Value", "A"));
        DocumentChunk b = row(2, Map.of("Group", "18", "Value", "B"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
        assertThat(CellAwareTableRowScorer.valueMatchesBoundary("18", "Group", "2")).isFalse();
    }

    @Test
    void multiSignalSameRowWins() {
        String query = "Alpha Group 2";
        DocumentChunk a = row(1, Map.of("Name", "Alpha", "Group", "2"));
        DocumentChunk b = row(2, Map.of("Name", "Alpha", "Group", "18"));
        DocumentChunk c = row(3, Map.of("Name", "Beta", "Group", "2"));

        assertThat(score(a, query)).isGreaterThan(score(b, query));
        assertThat(score(a, query)).isGreaterThan(score(c, query));
    }

    @Test
    void noCanonicalMetaPrefixDependency() {
        DocumentChunk canonicalOnly = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(DOC)
                .chunkType("normalized_table_row")
                .content("Table: X\nRow: 1\nGroup: 2\n")
                .build();
        DocumentChunk structured = row(1, Map.of("Group", "2", "Value", "A"));

        assertThat(CellAwareTableRowScorer.parseCells(canonicalOnly)).isEmpty();
        assertThat(score(structured, "Group 2")).isGreaterThan(0);
    }

    private static List<String> stringLiterals(String source) {
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(source);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group(1).toLowerCase());
        }
        return out;
    }

    private static double score(DocumentChunk chunk, String query) {
        return CellAwareTableRowScorer.score(chunk, QuerySignalExtractor.extract(query), query).total();
    }

    private static DocumentChunk row(int rowIndex, Map<String, String> cells) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(DOC)
                .chunkType("normalized_table_row")
                .rowIndex(rowIndex)
                .tableName("Runtime")
                .content("")
                .cellsJson(NormalizedTableService.cellsToJson(new LinkedHashMap<>(cells)))
                .build();
    }
}
