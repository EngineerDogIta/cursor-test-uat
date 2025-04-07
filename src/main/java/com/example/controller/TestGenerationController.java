package com.example.controller;

import com.example.dto.*;
import com.example.service.TestGenerationService;
import com.example.model.JobLog;
import com.example.repository.JobLogRepository;
import com.example.model.TestGenerationJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Import JobNotFoundException
import com.example.exception.JobNotFoundException;

/**
 * REST controller exposing API endpoints for UAT test generation jobs.
 */
@RestController
@RequestMapping("/api/v1/test-generation")
@Validated // Ensure validation annotations are processed
public class TestGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationController.class);

    private final TestGenerationService testGenerationService;
    private final JobLogRepository jobLogRepository;

    // Constructor injection
    @Autowired
    public TestGenerationController(
            final TestGenerationService testGenerationService,
            final JobLogRepository jobLogRepository) {
        this.testGenerationService = testGenerationService;
        this.jobLogRepository = jobLogRepository;
    }

    /**
     * Starts an asynchronous test generation job.
     * @param ticketDto DTO containing ticket details.
     * @return ResponseEntity with the accepted job's ID.
     */
    @Operation(summary = "Start asynchronous test generation", description = "Accepts ticket information, creates a job record, returns the job ID, and starts the generation process in the background.")
    @ApiResponses(value = {
            // Update response description and add content schema for JSON
            @ApiResponse(responseCode = "202", description = "Test generation request accepted, job ID returned",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                          schema = @Schema(implementation = JobIdResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                          schema = @Schema(implementation = ErrorResponseDto.class))), // Assuming you have an ErrorResponseDto
            @ApiResponse(responseCode = "500", description = "Failed to create job record",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                          schema = @Schema(implementation = ErrorResponseDto.class))) // Example for internal errors
    })
    @PostMapping("/start")
    // Change return type to reflect JSON response with Job ID
    public ResponseEntity<Map<String, Object>> startTestGeneration(@Valid @RequestBody TicketContentDto ticketDto) {
        logger.info("Received request to start test generation for ticket: {}", ticketDto.getTicketId());

        // Call the synchronous service method which now returns the job
        TestGenerationJob createdJob = testGenerationService.startTestGeneration(ticketDto);

        // Extract the ID
        Long jobId = createdJob.getId();
        logger.info("Job created with ID: {}. Triggering async processing.", jobId);

        // Create response body
        Map<String, Object> responseBody = Map.of("jobId", jobId);

        // Return 202 Accepted with the job ID in the body
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);
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
            logger.debug("Received request for logs of job ID: {}", jobIdLong);
            List<JobLog> logs = jobLogRepository.findByJobIdOrderByTimestampDesc(jobIdLong);
            List<JobLogDto> logDtos = logs.stream()
                    .map(log -> new JobLogDto(log.getTimestamp(), log.getLevel(), log.getMessage()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(logDtos);
        } catch (NumberFormatException e) {
            logger.warn("Invalid job ID format for logs request: {}", jobId);
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
            logger.debug("Received request for test result of job ID: {}", jobIdLong);
            TestGenerationJob job = testGenerationService.getJob(jobIdLong);
            JobTestResultDto resultDto = new JobTestResultDto(job.getTestResult());
            return ResponseEntity.ok(resultDto);
        } catch (NumberFormatException e) {
            logger.warn("Invalid job ID format for test result request: {}", jobId);
            return ResponseEntity.badRequest().build();
        } catch (JobNotFoundException e) { // Catch JobNotFoundException
            logger.warn("Test result requested for non-existent job ID: {}", jobId);
            return ResponseEntity.notFound().build(); // Return 404 Not Found
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

// Helper DTO (can be placed in dto package or as inner class if simple)
// Ensure this (or similar) exists if referenced in @Schema
class JobIdResponseDto {
    @Schema(description = "The unique identifier for the created job", example = "123")
    public Long jobId;
}

// Assume ErrorResponseDto exists for error schemas
class ErrorResponseDto {
     @Schema(description = "Timestamp of the error", example = "2023-10-27T10:15:30Z")
     public String timestamp;
     @Schema(description = "HTTP status code", example = "400")
     public int status;
     @Schema(description = "General error category", example = "Bad Request")
     public String error;
     @Schema(description = "Specific error message", example = "Validation failed")
     public String message;
     @Schema(description = "Request path", example = "/api/v1/test-generation/start")
     public String path;
     // Potentially add validation errors list
} 