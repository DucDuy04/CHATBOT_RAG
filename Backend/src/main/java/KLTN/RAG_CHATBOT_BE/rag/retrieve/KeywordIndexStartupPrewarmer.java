package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prewarms keyword indexes for active widgets on backend startup.
 * <p>
 * Triggered by {@link ApplicationReadyEvent} so the DB and all beans are ready.
 * When {@code prewarm-async=true} (default), runs in a single daemon thread so
 * startup is not blocked. One widget failure is logged and skipped; others
 * continue warming.
 * <p>
 * Config namespace: {@code rag.retrieval.keyword-index.prewarm-*}
 */
@Slf4j
@Component
public class KeywordIndexStartupPrewarmer {

    private final KeywordIndexCache keywordIndexCache;
    private final WidgetConfigRepository widgetConfigRepository;
    private final DocumentChunkRepository documentChunkRepository;

    @Value("${rag.retrieval.keyword-index.prewarm-on-startup:true}")
    private boolean prewarmOnStartup;

    @Value("${rag.retrieval.keyword-index.prewarm-async:true}")
    private boolean prewarmAsync;

    @Value("${rag.retrieval.keyword-index.prewarm-max-widgets:20}")
    private int prewarmMaxWidgets;

    @Value("${rag.retrieval.keyword-index.prewarm-corpus-limit:3000}")
    private int prewarmCorpusLimit;

    @Value("${rag.retrieval.keyword-index.prewarm-delay-ms:2000}")
    private long prewarmDelayMs;

    public KeywordIndexStartupPrewarmer(KeywordIndexCache keywordIndexCache,
                                        WidgetConfigRepository widgetConfigRepository,
                                        DocumentChunkRepository documentChunkRepository) {
        this.keywordIndexCache = keywordIndexCache;
        this.widgetConfigRepository = widgetConfigRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!prewarmOnStartup) {
            log.info("[KeywordIndexPrewarm] disabled by config (prewarm-on-startup=false)");
            return;
        }
        if (prewarmAsync) {
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "keyword-index-prewarm");
                t.setDaemon(true);
                return t;
            });
            executor.submit(this::runPrewarm);
            executor.shutdown();
        } else {
            runPrewarm();
        }
    }

    /**
     * Package-private so unit tests can invoke synchronously without mocking the Spring event.
     */
    void runPrewarm() {
        if (prewarmDelayMs > 0) {
            try {
                Thread.sleep(prewarmDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[KeywordIndexPrewarm] interrupted during startup delay, aborting");
                return;
            }
        }

        List<UUID> widgetIds;
        try {
            widgetIds = findEligibleWidgetIds();
        } catch (Exception e) {
            log.error("[KeywordIndexPrewarm] failed to query eligible widgets error={}", e.getMessage(), e);
            return;
        }

        log.info("[KeywordIndexPrewarm] started widgets={} limit={}", widgetIds.size(), prewarmCorpusLimit);
        long globalStart = System.currentTimeMillis();
        int ok = 0;
        int failed = 0;

        for (UUID widgetId : widgetIds) {
            long start = System.currentTimeMillis();
            try {
                keywordIndexCache.warm(widgetId, prewarmCorpusLimit);
                log.info("[KeywordIndexPrewarm] widget={} status=OK elapsedMs={}",
                        widgetId, System.currentTimeMillis() - start);
                ok++;
            } catch (Exception e) {
                log.error("[KeywordIndexPrewarm] widget={} status=FAILED elapsedMs={} error={}",
                        widgetId, System.currentTimeMillis() - start, e.getMessage(), e);
                failed++;
            }
        }

        log.info("[KeywordIndexPrewarm] finished ok={} failed={} totalMs={}",
                ok, failed, System.currentTimeMillis() - globalStart);
    }

    private List<UUID> findEligibleWidgetIds() {
        List<WidgetConfig> widgets = widgetConfigRepository.findAll();
        int cap = Math.max(1, prewarmMaxWidgets);
        return widgets.stream()
                .map(WidgetConfig::getId)
                .filter(id -> documentChunkRepository.existsByWidgetConfigId(id))
                .limit(cap)
                .toList();
    }
}
