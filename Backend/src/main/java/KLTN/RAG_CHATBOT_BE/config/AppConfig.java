package KLTN.RAG_CHATBOT_BE.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    /**
     * Thread pool dùng cho SSE streaming (chatStream).
     * Thay thế new Thread().start() để có giới hạn concurrency,
     * lifecycle management và tránh resource exhaustion dưới tải cao.
     *
     * Sizing:
     *  - corePoolSize = 5    : luôn sẵn 5 thread cho streaming
     *  - maxPoolSize  = 20   : tối đa 20 concurrent stream requests
     *  - queueCapacity = 50  : queue buffer trước khi reject
     *  - keepAlive = 60s     : giải phóng thread idle sau 60s
     */
    @Bean(name = "streamingExecutor")
    public Executor streamingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();
        return executor;
    }
}
