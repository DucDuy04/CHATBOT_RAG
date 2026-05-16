package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;
import technology.tabula.Table;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 23B / 23B3 — cross-page Tabula table merge (repeated header, sparse continuation, negative cases).
 */
class DocumentParserCrossPageMergeTest {

    private static final String ENTITY_HEADER =
            "| Entity | Attributes |\n| --- | --- |\n";

    @Test
    void cross_page_repeated_header_normal_merge_passes() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = basePageWithAcceptedTable(1);

        Table page2 = TabulaTableTestHelper.table(new String[][]{
                {"Entity", "Attributes"},
                {"EntityA", "idA"},
                {"EntityB", "idB, nameB"},
        });

        var outcome = parser.attemptCrossPageMerge(page2, 2, 1, ENTITY_HEADER, new String[]{ENTITY_HEADER}, pages);

        assertThat(outcome.merged()).isTrue();
        assertThat(outcome.mode()).isEqualTo("REPEATED_HEADER");
        assertThat(pages.get(1))
                .contains("EntityA")
                .contains("EntityB")
                .contains("[TABLE_END]");
    }

    @Test
    void cross_page_sparse_continuation_merge_passes() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = basePageWithAcceptedTable(1);

        // Layout 3 cột giống PDF: cột 0 rỗng, cột 1 entity, cột 2 thuộc tính; nhiều cell rỗng
        Table sparsePage2 = TabulaTableTestHelper.table(new String[][]{
                {"", "", ""},
                {"---", "---", "---"},
                {"", "EntityA", "idA"},
                {"", "EntityB", "idB"},
                {"", "", ""},
                {"", "EntityC", "idC, nameC"},
                {"", "", "extraAttr"},
                {"", "", ""},
        });

        var outcome = parser.attemptCrossPageMerge(
                sparsePage2, 2, 1, ENTITY_HEADER, new String[]{ENTITY_HEADER}, pages);

        assertThat(outcome.merged()).isTrue();
        assertThat(outcome.mode()).isEqualTo("SPARSE_CONTINUATION");
        assertThat(pages.get(1))
                .contains("EntityA")
                .contains("EntityB")
                .contains("EntityC");
    }

    @Test
    void independent_sparse_table_on_non_adjacent_page_must_not_merge() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = basePageWithAcceptedTable(1);
        pages.put(2, "filler page without table\n");

        Table sparseOrphan = TabulaTableTestHelper.table(new String[][]{
                {"", "OrphanX", "valX"},
                {"", "", ""},
                {"", "OrphanY", "valY"},
                {"", "", ""},
                {"", "", ""},
        });

        // lastTableHeaderPage=1 but current page=3 → không liền kề
        var outcome = parser.attemptCrossPageMerge(
                sparseOrphan, 3, 1, ENTITY_HEADER, new String[]{ENTITY_HEADER}, pages);

        assertThat(outcome.merged()).isFalse();
        assertThat(pages.get(1)).doesNotContain("OrphanX").doesNotContain("OrphanY");
    }

    @Test
    void sparse_continuation_with_heading_only_in_later_row_still_merges() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = basePageWithAcceptedTable(1);

        Table sparseWithLateHeading = TabulaTableTestHelper.table(new String[][]{
                {"", "", ""},
                {"---", "---", "---"},
                {"", "", "viên, tailAttr"},
                {"", "EntityA", "idA"},
                {"5. New Section Title", "", ""},
                {"", "EntityB", "idB"},
        });

        var outcome = parser.attemptCrossPageMerge(
                sparseWithLateHeading, 2, 1, ENTITY_HEADER, new String[]{ENTITY_HEADER}, pages);

        assertThat(outcome.merged()).isTrue();
        assertThat(outcome.mode()).isEqualTo("SPARSE_CONTINUATION");
        assertThat(pages.get(1)).contains("EntityA").contains("EntityB");
    }

    @Test
    void sparse_table_with_new_section_heading_must_not_merge() {
        DocumentParserService parser = new DocumentParserService();
        Map<Integer, String> pages = basePageWithAcceptedTable(1);

        Table withHeading = TabulaTableTestHelper.table(new String[][]{
                {"4. New Section Title", "", ""},
                {"", "EntityZ", "idZ"},
                {"", "", ""},
        });

        var outcome = parser.attemptCrossPageMerge(
                withHeading, 2, 1, ENTITY_HEADER, new String[]{ENTITY_HEADER}, pages);

        assertThat(outcome.merged()).isFalse();
        assertThat(pages.get(1)).doesNotContain("EntityZ");
    }

    private static Map<Integer, String> basePageWithAcceptedTable(int pageNum) {
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(pageNum, """
                Section intro
                [TABLE_START]
                | Entity | Attributes |
                | --- | --- |
                | EntitySeed | seedAttr |
                [TABLE_END]
                """);
        return pages;
    }
}
