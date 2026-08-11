package com.macro.mall.distribution.config;

import com.macro.mall.distribution.service.LineChangeApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LineChangeScheduler {
    private final LineChangeApplicationService service;
    private final DistributedScheduledTaskRunner scheduledTaskRunner;
    @Scheduled(fixedDelayString = "${distribution.line-change.scan-interval-ms:60000}")
    public void executeDueApplications() {
        scheduledTaskRunner.run("line-change-applications", Duration.ofMinutes(10), service::executeDue);
    }
}
