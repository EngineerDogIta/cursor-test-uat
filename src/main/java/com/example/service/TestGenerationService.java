package com.example.service;

import com.example.agent.TestGeneratorAgent;
import com.example.dto.TicketContentDto;
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

@Service
public class TestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

    private final TestGeneratorAgent testGenerator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final JobLogService jobLogService; // Corrected type if needed based on actual package

    @Autowired
    public TestGenerationService(
            TestGeneratorAgent testGenerator,
            TestGenerationJobRepository testGenerationRepository,
            JobLogService jobLogService) {
        this.testGenerator = testGenerator;
        this.testGenerationRepository = testGenerationRepository;
        this.jobLogService = jobLogService;
    }

    @Async("taskExecutor")
    public void startTestGeneration(TicketContentDto ticketDto) {
        TestGenerationJob job = null; // Initialize job to null
        try {
            job = new TestGenerationJob();
            // Ensure getTicketId() and getContent() are non-null or handle potential NullPointerException
            String ticketId = ticketDto.getTicketId() != null ? ticketDto.getTicketId() : "UNKNOWN";
            String ticketContent = ticketDto.getContent() != null ? ticketDto.getContent() : "";

            job.setJiraTicket(ticketId);
            job.setDescription(ticketContent);

            // Components might be null or empty, handle gracefully
            if (ticketDto.getComponents() != null && !ticketDto.getComponents().isEmpty()) {
                job.setComponents(String.join(", ", ticketDto.getComponents()));
            } else {
                job.setComponents("N/A"); // Or set to null or empty string as appropriate
            }
            job.setStatus(TestGenerationJob.JobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            // Save the initial job state
            job = testGenerationRepository.saveAndFlush(job);
            final Long currentJobId = job.getId(); // Use final variable for lambda/inner class if needed

            jobLogService.addJobLog(job, "INFO", "Job created with ID: " + currentJobId + " for ticket: " + ticketId);
            jobLogService.addJobLog(job, "INFO", "Components: " + job.getComponents());

            // Trigger the simplified processing method
            processTestGeneration(currentJobId, ticketContent); // Pass potentially empty content

        } catch (Exception e) {
            String ticketId = (ticketDto != null && ticketDto.getTicketId() != null) ? ticketDto.getTicketId() : "UNKNOWN";
            logger.error("Error starting test generation for ticket: {}", ticketId, e);
            // If job was created before exception, update its status to FAILED
            if (job != null && job.getId() != null) {
                failJob(job.getId(), "Error during startup: " + e.getMessage());
                // Ensure job object is passed if failJob doesn't refetch it
                jobLogService.addJobLog(job, "ERROR", "Failed to start processing: " + e.getMessage()); 
            } else {
                // Log failure even if job wasn't saved
                logger.error("Failed to create or save job for ticket: {}", ticketId);
            }
        }
    }

    @Async("taskExecutor")
    protected void processTestGeneration(Long jobId, String ticketContent) {
        TestGenerationJob job = null;
        try {
            job = testGenerationRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

            jobLogService.addJobLog(job, "INFO", "Processing job ID: " + jobId);
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job); // Save IN_PROGRESS status
            jobLogService.addJobLog(job, "INFO", "Job status updated to IN_PROGRESS");

            jobLogService.addJobLog(job, "INFO", "Calling Test Generator Agent directly with ticket content.");
            String generatedTests = testGenerator.generateTests(ticketContent);
            jobLogService.addJobLog(job, "INFO", "Test Generator Agent finished.");

            if (generatedTests != null && generatedTests.startsWith("Error:")) {
                logger.error("Test Generation Agent returned an error for job {}: {}", jobId, generatedTests);
                jobLogService.addJobLog(job, "ERROR", "Test Generation failed: " + generatedTests);
                failJob(jobId, generatedTests); // Use failJob for error path
                return;
            }

            jobLogService.addJobLog(job, "DEBUG", "Generated tests:\n" + generatedTests);

            // Set result and complete the job directly here
            job.setTestResult(generatedTests);
            jobLogService.addJobLog(job, "INFO", "Successfully generated tests.");
            job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null); // Clear any previous error message
            testGenerationRepository.saveAndFlush(job); // Save final state including result
            jobLogService.addJobLog(job, "INFO", "Job completed and saved.");

        } catch (Exception e) {
            logger.error("Error during simplified test generation process for jobId: {}", jobId, e);
            if (jobId != null) {
                String errorMessage = (e.getMessage() != null) ? e.getMessage() : "Unknown error";
                failJob(jobId, "Process failed: " + errorMessage); // Use failJob for exception path
                if (job != null) {
                   jobLogService.addJobLog(job, "ERROR", "Process failed unexpectedly: " + errorMessage);
                }
            }
        }
    }

    // Helper method to fail a job
    private void failJob(Long jobId, String errorMessage) {
        TestGenerationJob jobToFail = null; // Use a different variable name
        try {
            // Refetch job inside try-catch to ensure we have the latest state 
            // or handle the case where it might have been deleted concurrently.
            jobToFail = testGenerationRepository.findById(jobId)
                    .orElse(null); // Don't throw, handle null case

            if (jobToFail == null) {
                logger.warn("Attempted to fail non-existent or already deleted job: {}", jobId);
                return; // Job doesn't exist, nothing to fail
            }
            
            // Check if already failed to avoid redundant updates/logs
            if (jobToFail.getStatus() == TestGenerationJob.JobStatus.FAILED) {
                logger.info("Job {} already marked as FAILED.", jobId);
                return;
            }

            jobToFail.setStatus(TestGenerationJob.JobStatus.FAILED);
            // Truncate error message if it's too long for the database field
            jobToFail.setErrorMessage(errorMessage != null && errorMessage.length() > 1024 ? errorMessage.substring(0, 1024) : errorMessage);
            jobToFail.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(jobToFail); // Save the failed state
            logger.warn("Job {} marked as FAILED. Reason: {}", jobId, errorMessage);
            
            // Add log entry for failure completion
            jobLogService.addJobLog(jobToFail, "INFO", "Job failed and saved.");

        } catch (Exception ex) {
            // Log critical error if we can't even mark the job as failed
            logger.error("Critical error: Failed to update job {} status to FAILED. Database issue? Reason: {}", jobId, ex.getMessage(), ex);
            // Avoid logging the job object here if it might be null or stale
        }
    }

    public Map<String, Object> getJobStatus(String jobId) {
        try {
            Long jobIdLong = Long.parseLong(jobId);
            TestGenerationJob job = testGenerationRepository.findById(jobIdLong).orElse(null);
            if (job == null) {
                logger.debug("Status requested for non-existent jobId: {}", jobId);
                return Map.of(
                    "status", "NOT_FOUND",
                    "message", "Job not found"
                );
            }
            
            return Map.of(
                "status", job.getStatus().name(),
                "error", job.getErrorMessage() != null ? job.getErrorMessage() : ""
            );
        } catch (NumberFormatException e) {
             logger.warn("Invalid jobId format received for status: {}", jobId);
            return Map.of(
                "status", "INVALID_ID",
                "message", "Invalid job ID format"
            );
        }
    }

    public List<TestGenerationJob> getActiveJobs() {
        return testGenerationRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.PENDING,
            TestGenerationJob.JobStatus.IN_PROGRESS
        ));
    }

    public List<TestGenerationJob> getCompletedJobs() {
        return testGenerationRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.COMPLETED,
            TestGenerationJob.JobStatus.FAILED
        ));
    }

    public TestGenerationJob getJob(Long id) {
        return testGenerationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));
    }

    @Transactional
    public void deleteJob(Long jobId) {
        // Consider adding logic to delete associated logs if JobLogService supports it
        // jobLogService.deleteLogsForJob(jobId);
        // Use existsById for efficiency before fetching
        if (!testGenerationRepository.existsById(jobId)) {
             logger.warn("Attempted to delete non-existent job: {}", jobId);
             // Optionally throw an exception or return a status
             // throw new RuntimeException("Job not found for deletion: " + jobId);
             return; 
        }
        testGenerationRepository.deleteById(jobId); // More efficient than fetching then deleting
        logger.info("Deleted job with ID: {}", jobId);
    }
} 