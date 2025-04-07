package com.example.controller;

import com.example.dto.*;
import com.example.model.TestGenerationJob;
import com.example.model.JobLog;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
import com.example.service.TestGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing API endpoints for UAT test generation jobs.
 */
@RestController
@RequestMapping("/api/v1/test-generation")
@Validated // Ensure validation annotations are processed
public class TestGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationController.class);

    private final TestGenerationService testGenerationService;
    private final TestGenerationJobRepository testGenerationJobRepository;
    private final JobLogRepository jobLogRepository;

    // Constructor injection
    @Autowired
    public TestGenerationController(
            final TestGenerationService testGenerationService,
            final TestGenerationJobRepository testGenerationJobRepository,
            final JobLogRepository jobLogRepository) {
        this.testGenerationService = testGenerationService;
        this.testGenerationJobRepository = testGenerationJobRepository;
        this.jobLogRepository = jobLogRepository;
    }

    /**
     * Starts an asynchronous test generation job.
     * @param ticketDto DTO containing ticket details.
     * @return ResponseEntity with job information or error.
     */
    @Operation(summary = "Start asynchronous test generation", description = "Accepts ticket information and starts the test generation process in the background.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Test generation request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PostMapping("/start")
    public ResponseEntity<String> startTestGeneration(@Valid @RequestBody TicketContentDto ticketDto) {
        // Input validation is now handled by @Valid and GlobalExceptionHandler
        logger.info("Received request to start test generation for ticket: {}", ticketDto.getTicketId());
        testGenerationService.startTestGeneration(ticketDto);
        // No try-catch needed here for service exceptions, handled globally
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Test generation process started for ticket: " + ticketDto.getTicketId());
    }

    /**
     * Gets the status of a specific job.
     * @param jobId The ID of the job.
     * @return ResponseEntity with job status or error.
     */
    @Operation(summary = "Get job status", description = "Retrieves the current status and any error message for a specific test generation job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "400", description = "Invalid Job ID format") // Handled by GlobalExceptionHandler if needed
    })
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        logger.debug("Received request for status of job ID: {}", jobId);
        // No try-catch needed for NumberFormatException or JobNotFoundException, handled globally
        Map<String, Object> status = testGenerationService.getJobStatus(jobId);
        return ResponseEntity.ok(status);
    }

    /**
     * Gets the logs for a specific job.
     * @param jobId The ID of the job.
     * @return ResponseEntity with a list of job logs or error.
     */
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
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Gets the generated test result for a specific job.
     * @param jobId The ID of the job.
     * @return ResponseEntity with the test result or error.
     */
    @GetMapping("/jobs/{jobId}/test-result")
    public ResponseEntity<JobTestResultDto> getJobTestResult(@PathVariable String jobId) {
        try {
            Long jobIdLong = Long.parseLong(jobId);
            TestGenerationJob job = testGenerationJobRepository.findById(jobIdLong)
                    .orElse(null);
            
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(new JobTestResultDto(job.getTestResult()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deletes a specific job.
     * @param jobId The ID of the job to delete.
     * @return ResponseEntity indicating success or failure.
     */
    @Operation(summary = "Delete job", description = "Deletes a specific test generation job. Cannot delete jobs that are IN_PROGRESS.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Job deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Cannot delete job in IN_PROGRESS state")
    })
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long jobId) {
        logger.info("Received request to delete job ID: {}", jobId);
        // No try-catch needed for JobNotFoundException or InvalidJobStateException, handled globally
        testGenerationService.deleteJob(jobId);
        return ResponseEntity.noContent().build(); // 204 No Content for successful deletion
    }
} 