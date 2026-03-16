package com.echo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Java 21 virtual thread executor — I/O ağır async görevler için.
     * ThreadPoolTaskExecutor'ın yerini alır: bounded pool + queue yerine
     * her görev için ayrı, hafif virtual thread.
     * AI API çağrıları (OpenAI, Gemini, Claude) bloklayıcı I/O olduğundan
     * virtual thread'ler ile thread başına maliyet minimumdur.
     */
    @Bean(name = "journalProcessingExecutor")
    public Executor journalProcessingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return journalProcessingExecutor();
    }
}
