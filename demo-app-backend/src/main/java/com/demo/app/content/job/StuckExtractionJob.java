package com.demo.app.content.job;

import com.demo.app.content.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckExtractionJob {

    private final DocumentService documentService;

    @Scheduled(fixedDelay = 600_000)
    public void timeoutStuckExtractions() {
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);
        log.info("Checking for PROCESSING documents stuck before {}", cutoff);
        documentService.timeoutStuckProcessing(cutoff);
    }
}
