package com.example.controller;

import com.example.model.TestGenerationJob;
import com.example.model.JobLog;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
import com.example.service.TestGenerationService;
import com.example.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/uat")
public class UatGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(UatGenerationController.class);

    private final TestGenerationService testGenerationService;
    private final TestGenerationJobRepository testGenerationRepository;
    private final JobLogRepository jobLogRepository;

    @Autowired
    public UatGenerationController(
            TestGenerationService testGenerationService,
            TestGenerationJobRepository testGenerationRepository,
            JobLogRepository jobLogRepository) {
        this.testGenerationService = testGenerationService;
        this.testGenerationRepository = testGenerationRepository;
        this.jobLogRepository = jobLogRepository;
        logger.info("UatGenerationController initialized with TestGenerationService");
    }

    @PostMapping("/generate")
    public ResponseEntity<TestGenerationResponseDto> startTestGeneration(@Valid @RequestBody TicketContentDto ticketDto) {
        if (ticketDto.getContent() == null || ticketDto.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new TestGenerationResponseDto(
                null,
                null,
                "Il contenuto del ticket non può essere vuoto"
            ));
        }

        if (ticketDto.getTicketId() == null || ticketDto.getTicketId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new TestGenerationResponseDto(
                null,
                null,
                "L'ID del ticket non può essere vuoto"
            ));
        }

        String jobId = UUID.randomUUID().toString(); 
        logger.info("Starting asynchronous UAT generation for ticket: {} with jobId: {}",
                   ticketDto.getTicketId(), jobId);

        testGenerationService.startTestGeneration(ticketDto); 

        return ResponseEntity.ok(new TestGenerationResponseDto(
            jobId,
            "Generazione UAT avviata con successo",
            null
        ));
    }

    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<JobStatusDto> getJobStatus(@PathVariable String jobId) {
        logger.info("Checking status for jobId: {}", jobId);
        Map<String, Object> statusMap = testGenerationService.getJobStatus(jobId);

        if (statusMap == null || statusMap.get("status").equals("NOT_FOUND")) {
            return ResponseEntity.status(404).body(new JobStatusDto("NOT_FOUND", "Job non trovato"));
        }

        return ResponseEntity.ok(new JobStatusDto(
            (String) statusMap.get("status"),
            (String) statusMap.get("error")
        ));
    }

    @GetMapping("/jobs/{jobId}/logs")
    public ResponseEntity<List<JobLogDto>> getJobLogs(@PathVariable String jobId) {
        try {
            Long jobIdLong = Long.parseLong(jobId); 
            List<JobLog> logs = jobLogRepository.findByJobIdOrderByTimestampDesc(jobIdLong);

            List<JobLogDto> logDtos = logs.stream()
                .map(log -> new JobLogDto(log.getTimestamp(), log.getLevel(), log.getMessage()))
                .collect(Collectors.toList());

            return ResponseEntity.ok(logDtos);
        } catch (NumberFormatException e) {
             logger.warn("Invalid jobId format received for logs: {}", jobId);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/jobs/{jobId}/test-result")
    public ResponseEntity<JobTestResultDto> getJobTestResult(@PathVariable String jobId) {
        try {
             Long jobIdLong = Long.parseLong(jobId);
            TestGenerationJob job = testGenerationRepository.findById(jobIdLong).orElse(null);

            if (job == null) {
                 logger.warn("Test result requested for non-existent jobId: {}", jobId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(new JobTestResultDto(job.getTestResult()));
        } catch (NumberFormatException e) {
             logger.warn("Invalid jobId format received for test result: {}", jobId);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<String> deleteJob(@PathVariable String jobId) {
        try {
             Long jobIdLong = Long.parseLong(jobId);
            testGenerationService.deleteJob(jobIdLong);
            logger.info("Successfully deleted job: {}", jobId);
            return ResponseEntity.ok("Job eliminato con successo");
        } catch (NumberFormatException e) {
             logger.warn("Invalid jobId format received for deletion: {}", jobId);
            return ResponseEntity.badRequest().body("ID job non valido");
        } catch (Exception e) {
             logger.error("Error deleting job {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Errore durante l'eliminazione del job");
        }
    }
} 