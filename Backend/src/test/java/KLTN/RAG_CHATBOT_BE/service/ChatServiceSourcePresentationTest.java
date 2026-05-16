package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
                five
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
                five
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
        return RetrievedContext.builder()
                .chunkId(chunkId)
                .fileName(fileName)
                .sectionTitle(section)
                .chunkType(chunkType)
                .content(content)
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
