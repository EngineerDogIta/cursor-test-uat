package com.example.service;

import com.example.agent.TestGeneratorAgent;
import com.example.dto.TicketContentDto;
import com.example.exception.InvalidJobStateException;
import com.example.exception.JobNotFoundException;
import com.example.exception.JobProcessingException;
import com.example.model.TestGenerationJob;
import com.example.repository.TestGenerationJobRepository;
import com.example.services.JobLogService; // Ensure correct import path
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for orchestrating the UAT test generation process.
 */
@Service
public class TestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

    // Dependencies are now final
    private final TestGeneratorAgent testGenerator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final com.example.services.JobLogService jobLogService;

    @Autowired
    public TestGenerationService(
            final TestGeneratorAgent testGenerator,
            final TestGenerationJobRepository testGenerationRepository,
            final com.example.services.JobLogService jobLogService) {
        this.testGenerator = testGenerator;
        this.testGenerationRepository = testGenerationRepository;
        this.jobLogService = jobLogService;
    }

    /**
     * Starts the asynchronous test generation process for a given ticket.
     * Creates a job record and initiates the generation.
     *
     * @param ticketDto DTO containing the ticket information.
     */
    @Async("taskExecutor")
    public void startTestGeneration(final TicketContentDto ticketDto) {
        TestGenerationJob job = null;
        Long jobId = null; // Store jobId separately
        try {
            job = new TestGenerationJob();
            job.setJiraTicket(ticketDto.getTicketId());
            job.setDescription(ticketDto.getContent());
            if (ticketDto.getComponents() != null && !ticketDto.getComponents().isEmpty()) {
                job.setComponents(String.join(", ", ticketDto.getComponents()));
            } else {
                job.setComponents("N/A");
            }
            job.setStatus(TestGenerationJob.JobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            job = testGenerationRepository.saveAndFlush(job);
            jobId = job.getId(); // Get the ID after saving

            final Long currentJobId = jobId; // Use final variable if needed
            jobLogService.addJobLog(job, "INFO", "Job created with ID: " + currentJobId + " for ticket: " + ticketDto.getTicketId());
            jobLogService.addJobLog(job, "INFO", "Components: " + job.getComponents());
            processTestGeneration(currentJobId, ticketDto.getContent());

        } catch (Exception e) {
            logger.error("Error starting test generation for ticket: {}", ticketDto.getTicketId(), e);
            if (jobId != null) { // Use the stored jobId
                // Use JobProcessingException or a more specific startup exception if desired
                failJob(jobId, "Error during startup: " + e.getMessage(), e);
            } else {
                logger.error("Failed to create or save job for ticket: {}", ticketDto.getTicketId());
                // Consider if a specific exception should be thrown even if job wasn't saved
            }
        }
    }

    /**
     * The core asynchronous processing logic for generating tests.
     *
     * @param jobId The ID of the job being processed.
     * @param ticketContent The content/description of the ticket to generate tests for.
     */
    @Async("taskExecutor")
    protected void processTestGeneration(final Long jobId, final String ticketContent) {
        TestGenerationJob job = null;
        try {
            job = testGenerationRepository.findById(jobId)
                    .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + jobId)); // Throw JobNotFoundException

            jobLogService.addJobLog(job, "INFO", "Processing job ID: " + jobId);
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job);
            jobLogService.addJobLog(job, "INFO", "Job status updated to IN_PROGRESS");

            jobLogService.addJobLog(job, "INFO", "Calling Test Generator Agent directly with ticket content.");
            String generatedTests = testGenerator.generateTests(ticketContent);
            jobLogService.addJobLog(job, "INFO", "Test Generator Agent finished.");

            if (generatedTests != null && generatedTests.startsWith("Error:")) {
                logger.error("Test Generation Agent returned an error for job {}: {}", jobId, generatedTests);
                // Throw JobProcessingException when generator indicates failure
                throw new JobProcessingException("Test Generation failed: " + generatedTests);
            }

            jobLogService.addJobLog(job, "DEBUG", "Generated tests:\n" + generatedTests);
            job.setTestResult(generatedTests);
            jobLogService.addJobLog(job, "INFO", "Successfully generated tests.");
            job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            testGenerationRepository.saveAndFlush(job);
            jobLogService.addJobLog(job, "INFO", "Job completed and saved.");

        } catch (JobProcessingException | JobNotFoundException e) { // Catch specific exceptions first
             logger.error("Error during test generation process for job {}: {}", jobId, e.getMessage(), e);
             failJob(jobId, e.getMessage(), e); // Pass cause
        } catch (Exception e) { // Catch broader exceptions
            logger.error("Unexpected error during test generation process for jobId: {}", jobId, e);
            String errorMessage = (e.getMessage() != null) ? e.getMessage() : "Unknown error";
            failJob(jobId, "Process failed unexpectedly: " + errorMessage, e); // Pass cause
        }
    }

    /**
     * Helper method to mark a job as FAILED.
     *
     * @param jobId The ID of the job to fail.
     * @param errorMessage The reason for the failure.
     * @param cause The original exception causing the failure (optional).
     */
    private void failJob(final Long jobId, final String errorMessage, final Throwable cause) { // Accept cause
        TestGenerationJob jobToFail = null;
        try {
            jobToFail = testGenerationRepository.findById(jobId)
                    // Throw JobNotFoundException if job doesn't exist when trying to fail it
                    .orElseThrow(() -> new JobNotFoundException("Attempted to fail non-existent job: " + jobId));
            jobToFail.setStatus(TestGenerationJob.JobStatus.FAILED);
            // Limit error message length if necessary
            jobToFail.setErrorMessage(errorMessage.length() > 255 ? errorMessage.substring(0, 252) + ".." : errorMessage);
            jobToFail.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(jobToFail);
            logger.warn("Job {} marked as FAILED. Reason: {}", jobId, errorMessage, cause); // Log cause
             if (jobToFail != null) {
                jobLogService.addJobLog(jobToFail, "ERROR", "Job failed: " + errorMessage); // Add failure log
                jobLogService.addJobLog(jobToFail, "INFO", "Job failed and saved.");
             }
        } catch (JobNotFoundException e) {
            logger.error("Critical error: Attempted to fail job {} which was not found.", jobId, e);
        } catch (Exception ex) {
            logger.error("Critical error: Failed to update job {} status to FAILED. Reason: {}", jobId, ex.getMessage(), ex);
            // Avoid throwing from finally/catch block if possible
        }
    }

    /**
     * Retrieves the status and error message for a job.
     *
     * @param jobId The string representation of the job ID.
     * @return A map containing the job status and error message.
     * @throws NumberFormatException if jobId is not a valid long.
     * @throws JobNotFoundException if the job is not found.
     */
    public Map<String, Object> getJobStatus(final String jobId) {
        Long jobIdLong = Long.parseLong(jobId); // Can throw NumberFormatException
        TestGenerationJob job = testGenerationRepository.findById(jobIdLong)
            .orElseThrow(() -> new JobNotFoundException("Job status not found for ID: " + jobId)); // Throw JobNotFoundException
        return Map.of(
            "status", job.getStatus().name(),
            "error", job.getErrorMessage() != null ? job.getErrorMessage() : ""
        );
    }

    /**
     * Retrieves all active (PENDING or IN_PROGRESS) jobs.
     *
     * @return A list of active TestGenerationJob entities.
     */
    public List<TestGenerationJob> getActiveJobs() {
        return testGenerationRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.PENDING,
            TestGenerationJob.JobStatus.IN_PROGRESS
        ));
    }

    /**
     * Retrieves all completed (COMPLETED or FAILED) jobs.
     *
     * @return A list of completed TestGenerationJob entities.
     */
    public List<TestGenerationJob> getCompletedJobs() {
        return testGenerationRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.COMPLETED,
            TestGenerationJob.JobStatus.FAILED
        ));
    }

    /**
     * Retrieves a specific job by its ID.
     *
     * @param id The job ID.
     * @return The TestGenerationJob entity.
     * @throws JobNotFoundException if the job is not found.
     */
    public TestGenerationJob getJob(final Long id) {
        return testGenerationRepository.findById(id)
            .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + id)); // Throw JobNotFoundException
    }

    /**
     * Creates a job record manually.
     * Note: This method is potentially deprecated and does not trigger processing.
     *
     * @param job The job entity to create (status and dates will be set).
     * @return The persisted TestGenerationJob entity.
     */
    @Transactional
    public TestGenerationJob createJob(final TestGenerationJob job) {
        // Implementation of createJob method
        // This method is not provided in the original file or the new code block
        // It's assumed to exist as it's called in the deleteJob method
        return null; // Placeholder return, actual implementation needed
    }

    /**
     * Deletes a job by its ID.
     * Cannot delete jobs that are currently IN_PROGRESS.
     *
     * @param jobId The ID of the job to delete.
     * @throws JobNotFoundException if the job is not found.
     * @throws InvalidJobStateException if the job is IN_PROGRESS.
     */
    @Transactional
    public void deleteJob(final Long jobId) {
        TestGenerationJob job = getJob(jobId); // Throws JobNotFoundException if not found
        if (job.getStatus() == TestGenerationJob.JobStatus.IN_PROGRESS) {
            // Throw InvalidJobStateException for specific state issue
            throw new InvalidJobStateException("Cannot delete a job that is IN_PROGRESS. Job ID: " + jobId);
        }
        testGenerationRepository.delete(job);
        logger.info("Deleted job with ID: {}", jobId);
        // Consider deleting logs: jobLogService.deleteLogsForJob(jobId);
    }
} 