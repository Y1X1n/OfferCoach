package com.offercoach.interview.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务按场景隔离：SSE 推送、AI 生成/解析、定时任务互不挤占线程。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * SSE 长连接推送：线程只负责写事件，数量受连接数约束。
     */
    @Bean(name = "sseExecutor")
    @Primary
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("oc-sse-");
        executor.initialize();
        return executor;
    }

    /**
     * AI 相关后台任务：出题、简历解析、反馈生成与报告生成，与 SSE 连接池隔离。
     */
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("oc-ai-");
        executor.initialize();
        return executor;
    }
}
