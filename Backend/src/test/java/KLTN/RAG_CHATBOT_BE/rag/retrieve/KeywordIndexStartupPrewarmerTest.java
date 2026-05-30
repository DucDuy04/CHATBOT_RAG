package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KeywordIndexStartupPrewarmer}.
 * No live MySQL / Qdrant / API required.
 */
@ExtendWith(MockitoExtension.class)
class KeywordIndexStartupPrewarmerTest {

    @Mock private KeywordIndexCache keywordIndexCache;
    @Mock private WidgetConfigRepository widgetConfigRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private ApplicationReadyEvent event;

    private KeywordIndexStartupPrewarmer prewarmer;

    @BeforeEach
    void setUp() {
        prewarmer = new KeywordIndexStartupPrewarmer(
                keywordIndexCache, widgetConfigRepository, documentChunkRepository);
        // Synchronous, zero delay — keeps tests fast and deterministic
        ReflectionTestUtils.setField(prewarmer, "prewarmOnStartup", true);
        ReflectionTestUtils.setField(prewarmer, "prewarmAsync", false);
        ReflectionTestUtils.setField(prewarmer, "prewarmMaxWidgets", 20);
        ReflectionTestUtils.setField(prewarmer, "prewarmCorpusLimit", 3000);
        ReflectionTestUtils.setField(prewarmer, "prewarmDelayMs", 0L);
    }

    // --- Test 1 ---

    @Test
    void disabled_config_does_nothing() {
        ReflectionTestUtils.setField(prewarmer, "prewarmOnStartup", false);

        prewarmer.onApplicationReady(event);

        verifyNoInteractions(widgetConfigRepository, documentChunkRepository, keywordIndexCache);
    }

    // --- Test 2 ---

    @Test
    void warms_active_widgets_with_chunks_only() {
        UUID widgetA = UUID.randomUUID();
        UUID widgetB = UUID.randomUUID();
        // widgetC is "deleted" — filtered by @SQLRestriction so findAll() never returns it
        UUID widgetD = UUID.randomUUID();  // has no active chunks

        when(widgetConfigRepository.findAll()).thenReturn(
                List.of(widget(widgetA), widget(widgetB), widget(widgetD)));
        when(documentChunkRepository.existsByWidgetConfigId(widgetA)).thenReturn(true);
        when(documentChunkRepository.existsByWidgetConfigId(widgetB)).thenReturn(true);
        when(documentChunkRepository.existsByWidgetConfigId(widgetD)).thenReturn(false);

        prewarmer.onApplicationReady(event);

        verify(keywordIndexCache).warm(widgetA, 3000);
        verify(keywordIndexCache).warm(widgetB, 3000);
        verify(keywordIndexCache, never()).warm(eq(widgetD), anyInt());
    }

    // --- Test 3 ---

    @Test
    void respects_prewarm_max_widgets_cap() {
        ReflectionTestUtils.setField(prewarmer, "prewarmMaxWidgets", 2);

        List<UUID> ids = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        when(widgetConfigRepository.findAll()).thenReturn(
                ids.stream().map(this::widget).toList());
        when(documentChunkRepository.existsByWidgetConfigId(any())).thenReturn(true);

        prewarmer.onApplicationReady(event);

        verify(keywordIndexCache, times(2)).warm(any(UUID.class), anyInt());
    }

    // --- Test 4 ---

    @Test
    void one_widget_failure_does_not_stop_others() {
        UUID widgetA = UUID.randomUUID();
        UUID widgetB = UUID.randomUUID();

        when(widgetConfigRepository.findAll()).thenReturn(
                List.of(widget(widgetA), widget(widgetB)));
        when(documentChunkRepository.existsByWidgetConfigId(any())).thenReturn(true);
        doThrow(new RuntimeException("index build failed")).when(keywordIndexCache).warm(widgetA, 3000);

        // Must not throw — prewarmer swallows per-widget errors
        prewarmer.onApplicationReady(event);

        verify(keywordIndexCache).warm(widgetA, 3000);
        verify(keywordIndexCache).warm(widgetB, 3000);
    }

    // --- Test 5 ---

    @Test
    void uses_configured_corpus_limit() {
        ReflectionTestUtils.setField(prewarmer, "prewarmCorpusLimit", 1234);

        UUID widgetA = UUID.randomUUID();
        when(widgetConfigRepository.findAll()).thenReturn(List.of(widget(widgetA)));
        when(documentChunkRepository.existsByWidgetConfigId(widgetA)).thenReturn(true);

        prewarmer.onApplicationReady(event);

        verify(keywordIndexCache).warm(widgetA, 1234);
    }

    // --- Helpers ---

    private WidgetConfig widget(UUID id) {
        WidgetConfig wc = WidgetConfig.builder().build();
        ReflectionTestUtils.setField(wc, "id", id);
        return wc;
    }
}
