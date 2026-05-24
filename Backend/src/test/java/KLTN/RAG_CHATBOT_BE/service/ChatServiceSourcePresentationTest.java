package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceSourcePresentationTest {

    @Test
    void dedupeByChunkId_keepsFirstOccurrenceOrder() {
        UUID id = UUID.randomUUID();
        List<RetrievedContext> contexts = List.of(
                ctx(id, "a.txt", "S1", "text", "alpha"),
                ctx(id, "a.txt", "S1", "text", "alpha duplicate")
        );

        List<RetrievedContext> deduped = ChatService.dedupeContextsForPresentation(contexts);

        assertEquals(1, deduped.size());
        assertEquals("alpha", deduped.getFirst().getContent());
    }

    @Test
    void buildSourceDtosForResponse_capsAtFive() {
        ChatService service = newMinimalChatService();
        List<RetrievedContext> contexts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            contexts.add(ctx(UUID.randomUUID(), "golden.txt", "Section " + i, "text", "body " + i));
        }

        List<ChatResponse.SourceDto> sources = service.buildSourceDtosForResponse(contexts);

        assertEquals(5, sources.size());
        assertEquals("body 0", sources.getFirst().getChunkText());
        assertEquals("body 4", sources.get(4).getChunkText());
    }

    @Test
    void buildSourceDtosForResponse_playgroundCapFollowsTopK() {
        ChatService service = newMinimalChatService();
        List<RetrievedContext> contexts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            contexts.add(ctx(UUID.randomUUID(), "golden.txt", "Section " + i, "text", "body " + i));
        }

        List<ChatResponse.SourceDto> sources = service.buildSourceDtosForResponse(contexts, 10);

        assertEquals(10, sources.size());
        assertEquals("body 9", sources.get(9).getChunkText());
    }

    @Test
    void tableHeaderDisplayCleaner_removesAdjacentDuplicateTokens() {
        assertEquals("STT", TableHeaderDisplayCleaner.cleanDisplayHeader("STT STT", 0));
    }

    @Test
    void tableHeaderDisplayCleaner_removesRepeatedPhrases() {
        assertEquals("Code Name", TableHeaderDisplayCleaner.cleanDisplayHeader("Code Code Name Name", 0));
    }

    @Test
    void tableHeaderDisplayCleaner_reducesOverlap() {
        assertEquals("A B C", TableHeaderDisplayCleaner.cleanDisplayHeader("A B A C", 0));
    }

    @Test
    void tableHeaderDisplayCleaner_usesColumnFallbackForEmptyHeader() {
        assertEquals("col_1", TableHeaderDisplayCleaner.cleanDisplayHeader("", 0));
    }

    @Test
    void tableHeaderDisplayCleaner_keepsDisplayKeysUnique() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("A A", "first");
        raw.put("A", "second");

        Map<String, String> display = TableHeaderDisplayCleaner.cleanCellsForDisplay(raw);

        assertEquals(List.of("A", "A_2"), new ArrayList<>(display.keySet()));
        assertEquals("first", display.get("A"));
        assertEquals("second", display.get("A_2"));
    }

    @Test
    void tableHeaderDisplayCleaner_boundsLongNoisyHeaders() {
        String cleaned = TableHeaderDisplayCleaner.cleanDisplayHeader("A B C D E F G H I J K", 0);

        assertEquals("A B C D E F G H", cleaned);
        assertTrue(cleaned.length() <= TableHeaderDisplayCleaner.MAX_DISPLAY_CHARS);
    }

    @Test
    void buildSourceDtosForResponse_normalizedRowUsesDisplayCellsAndKeepsRawCells() {
        ChatService service = newMinimalChatService();
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("STT STT", "9");
        cells.put("Code Code Name Name", "X-1");
        cells.put("A", "value");
        String content = NormalizedTableService.buildCanonicalText("sample", 3, cells, null, 7);
        RetrievedContext context = ctx(
                UUID.randomUUID(),
                "golden.txt",
                "Section",
                "normalized_table_row",
                content,
                NormalizedTableService.cellsToJson(cells)
        );

        ChatResponse.SourceDto source = service.buildSourceDtosForResponse(List.of(context)).getFirst();

        assertEquals("9", source.getDisplayCells().get("STT"));
        assertEquals("X-1", source.getDisplayCells().get("Code Name"));
        assertEquals("9", source.getRawCells().get("STT STT"));
        assertTrue(source.getChunkText().contains("STT: 9."));
        assertTrue(source.getChunkText().contains("Code Name: X-1."));
        assertFalse(source.getChunkText().contains("STT STT: 9."));
        assertEquals(content, source.getRawChunkText());
    }

    @Test
    void resolveSourcePresentationCap_playgroundUsesEffectiveTopK() {
        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(true);
        ChatService.TopKResolution topK = ChatService.resolveTopK(10, 5);

        assertEquals(10, ChatService.resolveSourcePresentationCap(request, topK));
    }

    @Test
    void resolveSourcePresentationCap_productionStaysAtFive() {
        ChatRequest request = new ChatRequest();
        ChatService.TopKResolution topK = ChatService.resolveTopK(10, 5);

        assertEquals(ChatService.MAX_RESPONSE_SOURCES, ChatService.resolveSourcePresentationCap(request, topK));
    }

    @Test
    void applyAnswerAwareSourceCap_refusalTrimsToTwo() {
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> five = List.of(
                source("f", "s1"),
                source("f", "s2"),
                source("f", "s3"),
                source("f", "s4"),
                source("f", "s5")
        );

        List<ChatResponse.SourceDto> capped = service.applyAnswerAwareSourceCap(
                "Tôi không tìm thấy thông tin CEO trong tài liệu.",
                five,
                null
        );

        assertEquals(2, capped.size());
        assertEquals("s1", capped.get(0).getSectionTitle());
        assertEquals("s2", capped.get(1).getSectionTitle());
    }

    @Test
    void applyAnswerAwareSourceCap_factualAnswerKeepsFive() {
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> five = List.of(
                source("f", "s1"),
                source("f", "s2"),
                source("f", "s3"),
                source("f", "s4"),
                source("f", "s5")
        );

        List<ChatResponse.SourceDto> capped = service.applyAnswerAwareSourceCap(
                "Công ty TNHH AlphaDemo.",
                five,
                null
        );

        assertEquals(5, capped.size());
    }

    @Test
    void isRefusalLikeAnswer_detectsStandardPhrases() {
        assertTrue(ChatService.isRefusalLikeAnswer("Tôi không tìm thấy thông tin này trong tài liệu."));
        assertTrue(ChatService.isRefusalLikeAnswer("Không có thông tin về tỷ giá trong tài liệu."));
        assertFalse(ChatService.isRefusalLikeAnswer("Gói Basic có giá 99000 VNĐ."));
    }

    @Test
    void applyAnswerAwareSourceCap_playgroundDebugSkipsRefusalCap() {
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(source("f", "s" + i));
        }
        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(true);

        String partialRefusal = "1. Alpha: ALPHA-111\nKhông tìm thấy thông tin về Epsilon và Eta trong tài liệu.";
        List<ChatResponse.SourceDto> capped = service.applyAnswerAwareSourceCap(partialRefusal, six, request);

        assertEquals(6, capped.size());
    }

    @Test
    void applyAnswerAwareSourceCap_partialAnswerWithCodesNotCappedToTwo() {
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> five = List.of(
                source("f", "s1"),
                source("f", "s2"),
                source("f", "s3"),
                source("f", "s4"),
                source("f", "s5")
        );

        String partial = "Mã Alpha là ALPHA-111. Không tìm thấy thông tin về Epsilon trong tài liệu.";
        List<ChatResponse.SourceDto> capped = service.applyAnswerAwareSourceCap(partial, five, null);

        assertEquals(5, capped.size());
        assertFalse(ChatService.isPureRefusalLikeAnswer(partial));
    }

    @Test
    void isPureRefusalLikeAnswer_pureRefusalWithoutFacts() {
        assertTrue(ChatService.isPureRefusalLikeAnswer("Tôi không tìm thấy thông tin này trong tài liệu."));
        assertFalse(ChatService.isPureRefusalLikeAnswer(
                "1. Alpha: ALPHA-111\nKhông tìm thấy thông tin về Eta."));
    }

    // ── isLeadingRefusalAnswer ──────────────────────────────────────────────────

    @Test
    void isLeadingRefusalAnswer_pureOosRefusal() {
        assertTrue(ChatService.isLeadingRefusalAnswer("Tôi không tìm thấy thông tin này trong tài liệu."));
    }

    @Test
    void isLeadingRefusalAnswer_oosPivotAnswer_refusalLeadsBeforeCodes() {
        String oosPivot =
                "Tôi không tìm thấy thông tin về tỷ giá USD/VND trong tài liệu. " +
                "Tuy nhiên, tôi có thể liệt kê các mã chính sách:\n" +
                "- Alpha: ALPHA-111\n- Beta: BETA-222";
        assertTrue(ChatService.isLeadingRefusalAnswer(oosPivot));
    }

    @Test
    void isLeadingRefusalAnswer_partialInScope_codeBeforeRefusal() {
        // Factual code comes BEFORE refusal → not a leading refusal
        String partialInScope = "Mã Alpha là ALPHA-111. Không tìm thấy thông tin về Epsilon trong tài liệu.";
        assertFalse(ChatService.isLeadingRefusalAnswer(partialInScope));
    }

    @Test
    void isLeadingRefusalAnswer_numberedListBeforeRefusal() {
        // Numbered list item before refusal → not a leading refusal
        String partialList = "1. Alpha: ALPHA-111\nKhông tìm thấy thông tin về Eta trong tài liệu.";
        assertFalse(ChatService.isLeadingRefusalAnswer(partialList));
    }

    @Test
    void isLeadingRefusalAnswer_factualAnswer_noRefusal() {
        assertFalse(ChatService.isLeadingRefusalAnswer("Gói Basic có giá 99000 VNĐ."));
    }

    // ── applyAnswerAwareSourceCap — OOS pivot regression fix ───────────────────

    @Test
    void applyAnswerAwareSourceCap_oosPivotAnswerCapsToTwo() {
        // Production path (no playground debug): OOS pivot answer must be capped to ≤2
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> five = List.of(
                source("f", "s1"), source("f", "s2"), source("f", "s3"),
                source("f", "s4"), source("f", "s5")
        );
        String oosWithPivot =
                "Tôi không tìm thấy thông tin về tỷ giá USD/VND hôm nay trong tài liệu. " +
                "Tuy nhiên, tôi có thể liệt kê các mã chính sách:\n" +
                "- Alpha: ALPHA-111\n- Beta: BETA-222\n- Gamma: GAMMA-333";
        List<ChatResponse.SourceDto> capped = service.applyAnswerAwareSourceCap(oosWithPivot, five, null);
        assertEquals(2, capped.size());
    }

    @Test
    void applyAnswerAwareSourceCap_playgroundDebugBypasses_evenWithLeadingRefusal() {
        // Playground debug bypasses cap even when answer has a leading refusal
        ChatService service = newMinimalChatService();
        List<ChatResponse.SourceDto> five = List.of(
                source("f", "s1"), source("f", "s2"), source("f", "s3"),
                source("f", "s4"), source("f", "s5")
        );
        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(true);
        String leadingRefusalWithPivot =
                "Tôi không tìm thấy thông tin về tỷ giá USD/VND trong tài liệu. " +
                "Tuy nhiên: - Alpha: ALPHA-111 - Beta: BETA-222";
        List<ChatResponse.SourceDto> result = service.applyAnswerAwareSourceCap(
                leadingRefusalWithPivot, five, request);
        assertEquals(5, result.size());
    }

    @Test
    void dedupeDifferentChunkTypesSameSection_keepsBoth() {
        List<RetrievedContext> contexts = List.of(
                ctx(UUID.randomUUID(), "g.txt", "4. Bảng", "table_summary", "summary"),
                ctx(UUID.randomUUID(), "g.txt", "4. Bảng", "table_row_group", "rows")
        );

        assertEquals(2, ChatService.dedupeContextsForPresentation(contexts).size());
    }

    private static ChatService newMinimalChatService() {
        return new ChatService(null, null, null, null, null, null, null, null);
    }

    private static RetrievedContext ctx(
            UUID chunkId,
            String fileName,
            String section,
            String chunkType,
            String content
    ) {
        return ctx(chunkId, fileName, section, chunkType, content, null);
    }

    private static RetrievedContext ctx(
            UUID chunkId,
            String fileName,
            String section,
            String chunkType,
            String content,
            String cellsJson
    ) {
        return RetrievedContext.builder()
                .chunkId(chunkId)
                .fileName(fileName)
                .sectionTitle(section)
                .chunkType(chunkType)
                .content(content)
                .cellsJson(cellsJson)
                .build();
    }

    private static ChatResponse.SourceDto source(String fileName, String section) {
        return ChatResponse.SourceDto.builder()
                .fileName(fileName)
                .sectionTitle(section)
                .pages("")
                .chunkType("text")
                .chunkText("x")
                .build();
    }
}
