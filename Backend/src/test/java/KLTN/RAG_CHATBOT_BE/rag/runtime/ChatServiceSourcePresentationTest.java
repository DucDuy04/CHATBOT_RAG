package KLTN.RAG_CHATBOT_BE.rag.runtime;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import KLTN.RAG_CHATBOT_BE.rag.prompt.PromptBuilderService;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceSourcePresentationTest {

    @Mock private PromptBuilderService promptBuilderService;
    @Mock private OpenAiChatModel chatModel;
    @Mock private LlmFallbackService llmFallbackService;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private WidgetConfigRepository widgetConfigRepository;
    @Mock private RagRetrievalService ragRetrievalService;
    @Mock private QueryAnalyzerService queryAnalyzerService;

    @InjectMocks
    private ChatService chatService;

    @Test
    void isLeadingRefusalAnswer_ossPivotBeforePolicyCodes_returnsTrue() {
        String answer = "Tôi không tìm thấy thông tin này trong tài liệu. "
                + "Tuy nhiên tài liệu có: ALPHA-111, BETA-222.";
        assertTrue(ChatService.isLeadingRefusalAnswer(answer));
    }

    @Test
    void isLeadingRefusalAnswer_factualCodeBeforeRefusal_returnsFalse() {
        String answer = "ALPHA-111. Không tìm thấy thông tin về Eta.";
        assertFalse(ChatService.isLeadingRefusalAnswer(answer));
    }

    @Test
    void isPureRefusalLikeAnswer_noSubstantiveContent_returnsTrue() {
        assertTrue(ChatService.isPureRefusalLikeAnswer(
                "Tôi không tìm thấy thông tin này trong tài liệu."));
    }

    @Test
    void isPureRefusalLikeAnswer_withPolicyCodes_returnsFalse() {
        assertFalse(ChatService.isPureRefusalLikeAnswer(
                "Không tìm thấy. Nhưng có ALPHA-111."));
    }

    @Test
    void applyAnswerAwareSourceCap_leadingRefusal_capsToTwo() {
        List<ChatResponse.SourceDto> sources = IntStream.range(0, 5)
                .mapToObj(i -> ChatResponse.SourceDto.builder().fileName("doc-" + i).build())
                .toList();

        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(false);

        List<ChatResponse.SourceDto> capped = chatService.applyAnswerAwareSourceCap(
                "Tôi không tìm thấy thông tin này trong tài liệu. Có ALPHA-111.",
                sources,
                request);

        assertEquals(2, capped.size());
    }

    @Test
    void applyAnswerAwareSourceCap_playgroundDebug_bypassesCap() {
        List<ChatResponse.SourceDto> sources = IntStream.range(0, 5)
                .mapToObj(i -> ChatResponse.SourceDto.builder().fileName("doc-" + i).build())
                .toList();

        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(true);

        List<ChatResponse.SourceDto> result = chatService.applyAnswerAwareSourceCap(
                "Tôi không tìm thấy thông tin này trong tài liệu.",
                sources,
                request);

        assertEquals(5, result.size());
    }

    @Test
    void applyAnswerAwareSourceCap_partialInScopeAnswer_notCapped() {
        List<ChatResponse.SourceDto> sources = IntStream.range(0, 5)
                .mapToObj(i -> ChatResponse.SourceDto.builder().fileName("doc-" + i).build())
                .toList();

        List<ChatResponse.SourceDto> result = chatService.applyAnswerAwareSourceCap(
                "ALPHA-111. Không tìm thấy Eta.",
                sources,
                new ChatRequest());

        assertEquals(5, result.size());
    }

    @Test
    void resolveSourcePresentationCap_playgroundDebug_usesEffectiveTopK() {
        ChatRequest request = new ChatRequest();
        request.setPlaygroundDebugSources(true);
        ChatService.TopKResolution resolution = new ChatService.TopKResolution(
                ChatService.TopKSource.REQUEST, 10, 10, 10);

        assertEquals(10, ChatService.resolveSourcePresentationCap(request, resolution));
    }

    @Test
    void resolveSourcePresentationCap_production_defaultsToFive() {
        assertEquals(5, ChatService.resolveSourcePresentationCap(new ChatRequest(), null));
    }
}
