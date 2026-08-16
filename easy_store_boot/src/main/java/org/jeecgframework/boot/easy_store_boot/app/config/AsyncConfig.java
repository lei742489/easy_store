package org.jeecgframework.boot.easy_store_boot.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("myExecutor")
    public Executor asyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);      // 核心线程数
        executor.setMaxPoolSize(500);       // 最大线程数
        executor.setQueueCapacity(200);    // 队列容量
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}
