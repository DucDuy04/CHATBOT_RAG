package KLTN.RAG_CHATBOT_BE.rag.prompt;

import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderServiceTest {

    private PromptBuilderService promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilderService();
    }

    @Test
    void tableLookup_withNormalizedRow_includesTableGuardBanner() {
        RetrievedContext ctx = RetrievedContext.builder()
                .chunkId(UUID.randomUUID())
                .chunkType("normalized_table_row")
                .content("STT: 1 | Mã: X")
                .fileName("doc.pdf")
                .headingPathText("Section")
                .pageStart(1)
                .pageEnd(1)
                .build();

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Hạng A là 100 hay 200?",
                List.of(ctx),
                List.of(),
                "TABLE_LOOKUP");

        assertTrue(prompt.contains("TRA CỨU BẢNG"));
        assertTrue(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
        assertTrue(prompt.contains("normalized_table_row"));
    }

    @Test
    void tableLookup_plainText_noPipe_doesNotRequireMarkdownTableBanner() {
        RetrievedContext ctx = RetrievedContext.builder()
                .chunkType("text")
                .content("Đoạn văn không có bảng.")
                .fileName("doc.pdf")
                .headingPathText("S")
                .pageStart(1)
                .pageEnd(1)
                .build();

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Hạng A là 100 hay 200?",
                List.of(ctx),
                List.of(),
                "TABLE_LOOKUP");

        assertTrue(prompt.contains("TRA CỨU BẢNG"));
        assertFalse(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void tableLookup_markdownInText_includesTableBanner() {
        RetrievedContext ctx = RetrievedContext.builder()
                .chunkType("text")
                .content("| Cột A | Cột B |\n| --- | --- |\n| 1 | 2 |")
                .fileName("doc.pdf")
                .headingPathText("S")
                .pageStart(1)
                .pageEnd(1)
                .build();

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "So sánh cột?",
                List.of(ctx),
                List.of(),
                "TABLE_LOOKUP");

        assertTrue(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void countQuery_doesNotIncludeTableLookupBanner() {
        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Có bao nhiêu mục?",
                List.of(),
                List.of(),
                "COUNT_QUERY");

        assertTrue(prompt.contains("ĐẾM"));
        assertFalse(prompt.contains("NHẮN NGỮ CẢNH BẢNG"));
    }

    @Test
    void promptInstructions_containNoHardcodedPricingLiterals() {
        RetrievedContext ctx = RetrievedContext.builder()
                .chunkType("normalized_table_row")
                .content("| plan | price |")
                .fileName("f")
                .headingPathText("h")
                .pageStart(1)
                .pageEnd(1)
                .build();

        String prompt = promptBuilder.buildUserPromptFromRetrievedContexts(
                "Hạng A là 100 hay 200?",
                List.of(ctx),
                List.of(),
                "TABLE_LOOKUP");

        assertFalse(prompt.contains("Basic"));
        assertFalse(prompt.contains("99000"));
        assertFalse(prompt.contains("199000"));
    }
}
