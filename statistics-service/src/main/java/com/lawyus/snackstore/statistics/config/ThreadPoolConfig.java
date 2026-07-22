package com.lawyus.snackstore.statistics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "statisticsExecutor")
    public Executor statisticsExecutor() {
        return Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("statistics-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });
    }
}
