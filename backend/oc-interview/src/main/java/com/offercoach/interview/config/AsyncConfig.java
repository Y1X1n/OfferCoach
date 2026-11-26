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
     *
     * <p>核心线程数不能太小：每次推送会占住线程最长 30s，而 {@link ThreadPoolTaskExecutor}
     * 只在队列满后才扩容到 maxPoolSize，核心数 2 意味着第 3 个订阅者起就在排队，
     * 常常在自己的超时时间内根本没拿到线程。</p>
     */
    @Bean(name = "sseExecutor")
    @Primary
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("oc-sse-");
        executor.setTaskDecorator(new MdcTaskDecorator());
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
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 优雅停机：排队中的出题/解析任务先跑完，避免重启后会话停在 PREPARING 等看门狗兜底。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
