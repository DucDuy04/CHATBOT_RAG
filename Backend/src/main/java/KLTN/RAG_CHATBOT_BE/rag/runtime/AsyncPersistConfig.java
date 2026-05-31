package KLTN.RAG_CHATBOT_BE.rag.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded executor for off-request-path assistant message persistence.
 * Only active when {@code rag.runtime.async-persist.enabled=true}.
 *
 * <p>Rejection policy: when the queue is full the task runs in the caller thread
 * (sync fallback). A warning is logged so the operator knows backpressure is high.
 * No persistence is silently dropped.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag.runtime.async-persist", name = "enabled", havingValue = "true")
public class AsyncPersistConfig {

    @Value("${rag.runtime.async-persist.pool-size:2}")
    private int poolSize;

    @Value("${rag.runtime.async-persist.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "chatPersistExecutor")
    public Executor chatPersistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("chat-persist-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("[ChatPersistAsync] queueFull fallback=sync activeThreads={} queueSize={}",
                    pool.getActiveCount(), pool.getQueue().size());
            task.run();
        });
        executor.initialize();
        return executor;
    }
}
