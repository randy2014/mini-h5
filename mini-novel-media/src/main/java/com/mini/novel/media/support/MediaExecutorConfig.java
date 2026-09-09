package com.mini.novel.media.support;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 媒体处理线程池：低并发（默认 1-2），守护性质，避免与读流量抢资源。
 * 由 application @EnableAsync 生效，@Async("mediaProcessExecutor") 引用。
 */
@Configuration
public class MediaExecutorConfig {

    @Bean("mediaProcessExecutor")
    public Executor mediaProcessExecutor(
            @Value("${app.media.process-core-pool:1}") int core,
            @Value("${app.media.process-max-pool:2}") int max) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-process-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
