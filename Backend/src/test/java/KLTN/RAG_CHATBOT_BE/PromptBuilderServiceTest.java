package KLTN.RAG_CHATBOT_BE;

import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import KLTN.RAG_CHATBOT_BE.service.PromptBuilderService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderServiceTest {

    private final PromptBuilderService promptBuilder = new PromptBuilderService();

    @Test
    void tableLookup_withTableChunk_includesReadTableAndChoiceGuards() {
        List<RetrievedContext> contexts = List.of(
                RetrievedContext.builder()
                        .chunkId(UUID.randomUUID())
                        .documentId(UUID.randomUUID())
                        .fileName("golden.txt")
                        .headingPathText("4. Bảng giá")
                        .pageStart(1)
                        .pageEnd(1)
                        .chunkType("table_row_group")
                        .content("| Tier | Price |\n|------|-------|\n| A    | 100   |\n")
                        .build()
        );

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Giá của hạng A là 100 hay 200?",
                contexts,
                Collections.emptyList(),
                "TABLE_LOOKUP",
                null
        );

        assertTrue(prompt.contains("TABLE_LOOKUP"));
        assertTrue(prompt.contains("lựa chọn"));
        assertTrue(prompt.contains("Tôi không tìm thấy thông tin này trong tài liệu"));
        assertTrue(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
        assertTrue(prompt.contains("table_row_group"));

        String lower = prompt.toLowerCase();
        assertFalse(lower.contains("basic"));
        assertFalse(prompt.contains("99000"));
        assertFalse(prompt.contains("199000"));
        assertFalse(lower.contains("pro"));
        assertFalse(lower.contains("business"));
    }

    @Test
    void tableLookup_withoutTableChunk_omitsContextBanner() {
        List<RetrievedContext> contexts = List.of(
                RetrievedContext.builder()
                        .chunkId(UUID.randomUUID())
                        .documentId(UUID.randomUUID())
                        .fileName("x.txt")
                        .headingPathText("Intro")
                        .pageStart(1)
                        .pageEnd(1)
                        .chunkType("text")
                        .content("Plain paragraph without pipes.")
                        .build()
        );

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Question?",
                contexts,
                Collections.emptyList(),
                "TABLE_LOOKUP",
                null
        );

        assertTrue(prompt.contains("TRA CỨU BẢNG"));
        assertFalse(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void tableLookup_markdownTableInText_triggersContextBanner() {
        List<RetrievedContext> contexts = List.of(
                RetrievedContext.builder()
                        .chunkId(UUID.randomUUID())
                        .documentId(UUID.randomUUID())
                        .fileName("x.txt")
                        .headingPathText("H")
                        .pageStart(1)
                        .pageEnd(1)
                        .chunkType("text")
                        .content("Some intro\n| a | b |\n|---|---|\n")
                        .build()
        );

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Cell value?",
                contexts,
                Collections.emptyList(),
                "TABLE_LOOKUP",
                null
        );

        assertTrue(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void countQuery_doesNotIncludeTableLookupGuards() {
        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Có mấy mục?",
                List.of(RetrievedContext.builder()
                        .chunkId(UUID.randomUUID())
                        .documentId(UUID.randomUUID())
                        .fileName("f")
                        .headingPathText("s")
                        .pageStart(1)
                        .pageEnd(1)
                        .chunkType("table_row_group")
                        .content("|x|")
                        .build()),
                Collections.emptyList(),
                "COUNT_QUERY",
                null
        );

        assertTrue(prompt.contains("ĐẾM"));
        assertFalse(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void normalizedTableRow_promptContentUsesCompactCellsAndOmitsEmptyCells() {
        RetrievedContext row = RetrievedContext.builder()
                .chunkId(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .fileName("table.txt")
                .headingPathText("Rows")
                .pageStart(2)
                .pageEnd(2)
                .chunkType("normalized_table_row")
                .tableName("Schedule")
                .rowIndex(4)
                .groupContext("Group A")
                .cellsJson("{\"Name\":\"Alpha\",\"Room\":\"H101\",\"Empty\":\"\"}")
                .content("Verbose canonical prose that should not be repeated in the prompt.")
                .build();

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Where is Alpha?",
                List.of(row),
                Collections.emptyList(),
                "TABLE_LOOKUP",
                null
        );

        assertTrue(prompt.contains("Table: Schedule"));
        assertTrue(prompt.contains("Row: 4"));
        assertTrue(prompt.contains("Group: Group A"));
        assertTrue(prompt.contains("- Name: Alpha"));
        assertTrue(prompt.contains("- Room: H101"));
        assertFalse(prompt.contains("Empty:"));
        assertFalse(prompt.contains("Verbose canonical prose"));
        assertTrue(prompt.contains("Document: table.txt"));
        assertTrue(prompt.contains("Pages: 2-2"));
    }
}
