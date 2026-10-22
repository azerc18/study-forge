package com.az.studyforge.generation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class GenerationExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor generationJobExecutor(GenerationProperties props) {
        return executor(props.maxConcurrentJobs(), props.maxConcurrentJobs(), "gen-job-");
    }

    @Bean
    ThreadPoolTaskExecutor llmCallExecutor(GenerationProperties props) {
        return executor(props.llmConcurrency(), 200, "llm-");
    }

    private static ThreadPoolTaskExecutor executor(int threads, int queue, String prefix) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(threads);
        ex.setMaxPoolSize(threads);
        ex.setQueueCapacity(queue);
        ex.setThreadNamePrefix(prefix);
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
