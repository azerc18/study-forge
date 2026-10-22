package com.az.studyforge.generation.service;

import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.entity.ClaimedJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "app.generation", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GenerationWorker {

    private final String workerId = workerId();

    private final JobQueue queue;
    private final GenerationJobProcessor processor;
    private final ThreadPoolTaskExecutor generationJobExecutor;
    private final GenerationProperties props;

    @Scheduled(initialDelay = 5, fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        while (generationJobExecutor.getActiveCount() < props.maxConcurrentJobs()) {
            Optional<ClaimedJob> job = queue.claim(workerId);
            if (job.isEmpty()) return;
            log.info("Claimed job {} (quiz set {}, attempt {})", job.get().id(), job.get().quizSetId(), job.get().attempts());
            generationJobExecutor.execute(() -> processor.process(job.get()));
        }
    }

    @Scheduled(initialDelay = 30, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void recoverStuckJobs() {
        int n = queue.requeueStuck(props.stuckTimeout());
        if (n > 0) log.warn("Requeued {} stuck generation job(s)", n);
    }

    private static String workerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        return host + ":" + UUID.randomUUID().toString().substring(0, 8);
    }
}
