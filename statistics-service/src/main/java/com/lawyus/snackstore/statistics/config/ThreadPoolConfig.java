package com.lawyus.snackstore.statistics.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "statisticsExecutor")
    public Executor statisticsExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                3, 3, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("statistics-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
