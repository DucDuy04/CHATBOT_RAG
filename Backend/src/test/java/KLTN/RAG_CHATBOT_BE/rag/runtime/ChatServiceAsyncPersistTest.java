package KLTN.RAG_CHATBOT_BE.rag.runtime;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.domain.enums.MessageRole;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import KLTN.RAG_CHATBOT_BE.rag.prompt.PromptBuilderService;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Unit tests for async assistant message persistence in {@link ChatService}.
 * No MySQL, Qdrant, or external API calls — all dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceAsyncPersistTest {

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

    private ThreadPoolTaskExecutor persistExecutor;
    private UUID widgetId;
    private ChatSession mockSession;

    @BeforeEach
    void setUp() {
        widgetId = UUID.randomUUID();

        mockSession = new ChatSession();
        mockSession.setId(UUID.randomUUID());
        mockSession.setSessionKey(UUID.randomUUID());

        // Set required @Value fields
        ReflectionTestUtils.setField(chatService, "groqApiKey", "test-key");
        ReflectionTestUtils.setField(chatService, "groqChatModel", "test-model");
        ReflectionTestUtils.setField(chatService, "groqBaseUrl", "https://api.groq.com");
        ReflectionTestUtils.setField(chatService, "logPersistPayloadSize", false);

        // Common mock setup
        when(chatSessionRepository.findBySessionKeyAndWidgetConfigId(any(), any()))
                .thenReturn(Optional.of(mockSession));
        when(widgetConfigRepository.findById(any()))
                .thenReturn(Optional.empty());
        when(queryAnalyzerService.analyze(any(), any()))
                .thenReturn(QueryAnalyzerService.QueryType.NORMAL_FACT);
        when(ragRetrievalService.retrieveWithMetadata(any(), any(), any()))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), null));
        when(promptBuilderService.getSystemPrompt()).thenReturn("system");
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatSessionRepository.getReferenceById(any())).thenReturn(mockSession);
    }

    // ─────────────────────────────────────────────────────────
    // Test 1: async enabled → response returns before save completes
    // ─────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void asyncEnabled_responseReturnedBeforeAssistantSaveCompletes() throws Exception {
        // Latch that blocks the assistant save task for 600ms
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);

        // Only block ASSISTANT saves (the second save call in the async task)
        AtomicBoolean userSaved = new AtomicBoolean(false);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.USER) {
                userSaved.set(true);
                return msg;
            }
            // ASSISTANT save — block to simulate slow DB write
            saveStarted.countDown();
            try {
                releaseSave.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return msg;
        });

        persistExecutor = buildBoundedExecutor();
        enableAsyncPersist(persistExecutor);

        ChatRequest request = buildRequest(mockSession.getSessionKey().toString());

        long start = System.currentTimeMillis();
        ChatResponse response = chatService.chat(request, widgetId);
        long elapsed = System.currentTimeMillis() - start;

        // Response should come back well before the 600ms save delay
        assertNotNull(response);
        assertTrue(elapsed < 500,
                "Expected response in <500ms but took " + elapsed + "ms (save was blocked for 600ms)");

        // Wait for async save to be triggered
        assertTrue(saveStarted.await(2, TimeUnit.SECONDS),
                "Async assistant save should have started");

        // Release the blocked save
        releaseSave.countDown();

        // Give async task time to complete
        persistExecutor.shutdown();
        persistExecutor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);

        // Verify the assistant save WAS eventually called (in the async task)
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, atLeastOnce()).save(captor.capture());
        boolean assistantSaved = captor.getAllValues().stream()
                .anyMatch(m -> m.getRole() == MessageRole.ASSISTANT);
        assertTrue(assistantSaved, "Assistant message should have been saved asynchronously");
    }

    // ─────────────────────────────────────────────────────────
    // Test 2: async disabled → sync behavior preserved
    // ─────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void asyncDisabled_assistantSaveHappensBeforeResponseReturns() {
        // async disabled: chatPersistExecutor = null, asyncPersistEnabled = false
        ReflectionTestUtils.setField(chatService, "asyncPersistEnabled", false);
        ReflectionTestUtils.setField(chatService, "chatPersistExecutor", null);

        AtomicBoolean assistantSavedBeforeReturn = new AtomicBoolean(false);
        AtomicBoolean responseReturned = new AtomicBoolean(false);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.ASSISTANT) {
                // Should be called BEFORE chat() returns
                assertFalse(responseReturned.get(), "Assistant save must happen before response returns in sync mode");
                assistantSavedBeforeReturn.set(true);
            }
            return msg;
        });

        ChatRequest request = buildRequest(mockSession.getSessionKey().toString());
        ChatResponse response = chatService.chat(request, widgetId);
        responseReturned.set(true);

        assertNotNull(response);
        assertTrue(assistantSavedBeforeReturn.get(), "Assistant message must be saved synchronously");
    }

    // ─────────────────────────────────────────────────────────
    // Test 3: async persist failure → response still returned, exception not propagated
    // ─────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void asyncPersistFailure_doesNotFailResponse() throws Exception {
        CountDownLatch saveFailed = new CountDownLatch(1);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.USER) {
                return msg;
            }
            // ASSISTANT save fails
            saveFailed.countDown();
            throw new RuntimeException("Simulated DB failure");
        });
        when(chatSessionRepository.getReferenceById(any())).thenReturn(mockSession);

        persistExecutor = buildBoundedExecutor();
        enableAsyncPersist(persistExecutor);

        ChatRequest request = buildRequest(mockSession.getSessionKey().toString());

        // Should NOT throw even though async save will fail
        ChatResponse response = assertDoesNotThrow(() -> chatService.chat(request, widgetId));
        assertNotNull(response);

        // Wait for the async failure
        assertTrue(saveFailed.await(2, TimeUnit.SECONDS), "Async save should have been attempted");

        persistExecutor.shutdown();
        persistExecutor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────────────────────
    // Test 4: queue full → sync fallback, no silent drop
    // ─────────────────────────────────────────────────────────

    @Test
    @Timeout(10)
    void queueFull_fallbackToSync_noPersistDrop() throws Exception {
        // Tiny pool: 1 thread, queue=0 to force immediate rejection
        ThreadPoolTaskExecutor tinyExecutor = new ThreadPoolTaskExecutor();
        tinyExecutor.setCorePoolSize(1);
        tinyExecutor.setMaxPoolSize(1);
        tinyExecutor.setQueueCapacity(0);
        tinyExecutor.setThreadNamePrefix("chat-persist-test-");
        tinyExecutor.setRejectedExecutionHandler((task, pool) -> {
            // Sync fallback (same as AsyncPersistConfig)
            task.run();
        });
        tinyExecutor.initialize();

        CountDownLatch blockFirstTask = new CountDownLatch(1);
        CountDownLatch firstTaskRunning = new CountDownLatch(1);
        AtomicBoolean assistantSaved = new AtomicBoolean(false);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.ASSISTANT) {
                firstTaskRunning.countDown();
                try {
                    // Block the pool thread to force rejection of subsequent tasks
                    blockFirstTask.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                assistantSaved.set(true);
            }
            return msg;
        });
        when(chatSessionRepository.getReferenceById(any())).thenReturn(mockSession);
        enableAsyncPersist(tinyExecutor);

        // First request — occupies the pool thread
        ChatRequest req1 = buildRequest(mockSession.getSessionKey().toString());
        chatService.chat(req1, widgetId);
        // Wait until pool thread is occupied
        assertTrue(firstTaskRunning.await(2, TimeUnit.SECONDS));

        // Reset mock for second call
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.ASSISTANT) {
                assistantSaved.set(true);
            }
            return msg;
        });

        // Second request — pool is full, should fallback to sync
        ChatRequest req2 = buildRequest(mockSession.getSessionKey().toString());
        ChatResponse response = chatService.chat(req2, widgetId);

        assertNotNull(response);
        // In sync fallback the assistant is saved before response returns
        assertTrue(assistantSaved.get(), "Assistant message must still be persisted (sync fallback)");

        // Release first task
        blockFirstTask.countDown();
        tinyExecutor.shutdown();
        tinyExecutor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────────────────────
    // Test 5: user message always sync regardless of async setting
    // ─────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void userMessageAlwaysSavedSync_beforeLlmCall() {
        persistExecutor = buildBoundedExecutor();
        enableAsyncPersist(persistExecutor);

        AtomicBoolean userSavedBeforeRetrieve = new AtomicBoolean(false);

        // Track order: user save must precede retrieval
        when(ragRetrievalService.retrieveWithMetadata(any(), any(), any())).thenAnswer(inv -> {
            // By this point, user message must already be in DB (sync)
            userSavedBeforeRetrieve.set(true);
            return new RagRetrievalService.RetrievalResult(List.of(), null);
        });

        AtomicBoolean userSavedAtAll = new AtomicBoolean(false);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getRole() == MessageRole.USER) {
                // This should run BEFORE retrieval (i.e., before the retrieval mock above)
                assertFalse(userSavedBeforeRetrieve.get(),
                        "User save must happen BEFORE retrieve is called");
                userSavedAtAll.set(true);
            }
            return msg;
        });

        ChatRequest request = buildRequest(mockSession.getSessionKey().toString());
        ChatResponse response = chatService.chat(request, widgetId);

        assertNotNull(response);
        assertTrue(userSavedAtAll.get(), "User message must always be saved");
        assertTrue(userSavedBeforeRetrieve.get(), "Retrieval should have been called");

        persistExecutor.shutdown();
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private ChatRequest buildRequest(String sessionId) {
        ChatRequest req = new ChatRequest();
        req.setMessage("Test question");
        req.setSessionId(sessionId);
        return req;
    }

    private ThreadPoolTaskExecutor buildBoundedExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("chat-persist-test-");
        exec.setRejectedExecutionHandler((task, pool) -> task.run());
        exec.initialize();
        return exec;
    }

    private void enableAsyncPersist(ThreadPoolTaskExecutor executor) {
        ReflectionTestUtils.setField(chatService, "asyncPersistEnabled", true);
        ReflectionTestUtils.setField(chatService, "chatPersistExecutor", executor);
    }
}
