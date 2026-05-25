package com.echo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    // Java 21 virtual threads for I/O-bound async tasks (AI API calls).
    // each task gets a lightweight virtual thread instead of pooled platform thread.
    @Bean(name = "journalProcessingExecutor")
    public Executor journalProcessingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return journalProcessingExecutor();
    }

    // Without this, exceptions escaping @Async void methods are swallowed by the default handler.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            String args = Arrays.stream(params)
                    .map(p -> p == null ? "null" : p.getClass().getSimpleName())
                    .collect(Collectors.joining(","));
            log.error("Uncaught async exception in {}.{}({})",
                    method.getDeclaringClass().getSimpleName(), method.getName(), args, ex);
        };
    }
}
