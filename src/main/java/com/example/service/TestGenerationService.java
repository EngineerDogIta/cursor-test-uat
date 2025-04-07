package com.example.service;

import com.example.agent.TestGeneratorAgent;
// Remove unused imports for ValidatorAgent, TicketAnalyzer, MetricsAnalysisService
// import com.example.agent.TestValidatorAgent;
// import com.example.agent.ITicketAnalyzer;
// import com.example.services.MetricsAnalysisService;
// import com.example.config.TestGenerationProperties;
import com.example.dto.TicketContentDto;
import com.example.model.TestGenerationJob;
import com.example.repository.TestGenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
// Remove unused import for List and Map if not used elsewhere after refactor

@Service
public class TestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

    // Remove unused dependencies
    // private final ITicketAnalyzer ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    // private final TestValidatorAgent testValidator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final com.example.services.JobLogService jobLogService;
    // private final com.example.services.MetricsAnalysisService metricsAnalysisService;
    // private final TestGenerationProperties properties;

    @Autowired
    public TestGenerationService(
            // Remove ITicketAnalyzer from constructor
            TestGeneratorAgent testGenerator,
            // Remove TestValidatorAgent from constructor
            TestGenerationJobRepository testGenerationRepository,
            com.example.services.JobLogService jobLogService
            // Remove MetricsAnalysisService from constructor
            // Remove TestGenerationProperties from constructor
            ) {
        // this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        // this.testValidator = testValidator;
        this.testGenerationRepository = testGenerationRepository;
        this.jobLogService = jobLogService;
        // this.metricsAnalysisService = metricsAnalysisService;
        // this.properties = properties;
    }

    @Async("taskExecutor")
    public void startTestGeneration(TicketContentDto ticketDto) {
        TestGenerationJob job = null; // Initialize job to null
        try {
            job = new TestGenerationJob();
            job.setJiraTicket(ticketDto.getTicketId()); // Assuming getTicketId() exists
            job.setDescription(ticketDto.getContent()); // Assuming getContent() exists
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

            jobLogService.addJobLog(job, "INFO", "Job created with ID: " + currentJobId + " for ticket: " + ticketDto.getTicketId());
            jobLogService.addJobLog(job, "INFO", "Components: " + job.getComponents());

            // Trigger the simplified processing method
            processTestGeneration(currentJobId, ticketDto.getContent());

        } catch (Exception e) {
            logger.error("Error starting test generation for ticket: {}", ticketDto.getTicketId(), e);
            // If job was created before exception, update its status to FAILED
            if (job != null && job.getId() != null) {
                failJob(job.getId(), "Error during startup: " + e.getMessage());
                jobLogService.addJobLog(job, "ERROR", "Failed to start processing: " + e.getMessage());
            } else {
                // Log failure even if job wasn't saved
                logger.error("Failed to create or save job for ticket: {}", ticketDto.getTicketId());
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
        try {
            TestGenerationJob job = testGenerationRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Attempted to fail non-existent job: " + jobId));
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
            logger.warn("Job {} marked as FAILED. Reason: {}", jobId, errorMessage);
            // Add log entry for failure completion
             if (job != null) { // Log only if job object exists
                jobLogService.addJobLog(job, "INFO", "Job failed and saved.");
             }
        } catch (Exception ex) {
            logger.error("Critical error: Failed to update job {} status to FAILED. Reason: {}", jobId, ex.getMessage(), ex);
        }
    }

    // Keep other methods like getJobStatus, getActiveJobs, getCompletedJobs, getJob, createJob (review if still needed), deleteJob

    // Review createJob: It seems to call the old processTestGeneration with null DTO.
    // This might need adjustment or removal depending on how jobs are now initiated.
    // If startTestGeneration is the sole entry point, createJob might be redundant or need refactoring.
    @Transactional
    public TestGenerationJob createJob(TestGenerationJob job) {
        // This method seems designed for manual job creation and immediate processing.
        // With the new flow relying on startTestGeneration(TicketContentDto), its purpose is unclear.
        // It calls processTestGeneration(job.getId(), null), which now expects a String ticketContent.
        // This will likely fail or produce incorrect results.
        // Recommendation: Deprecate or remove this method unless there's a specific use case
        // for creating jobs without initial TicketContentDto and processing them later (which the current code doesn't support well).
        logger.warn("createJob method is potentially deprecated due to refactoring. Review its usage.");

        // For now, let's save the job but log a warning and avoid triggering processing.
        job.setStatus(TestGenerationJob.JobStatus.PENDING); // Start as pending
        job.setCreatedAt(LocalDateTime.now());
        job = testGenerationRepository.saveAndFlush(job);
        logger.info("Job created manually with ID: {}. Note: Automatic processing from this method is disabled.", job.getId());
        jobLogService.addJobLog(job, "WARN", "Job created via createJob method. Automatic processing not triggered. Use startTestGeneration instead.");

        // // Original async processing call (commented out due to incompatibility)
        // try {
        //     processTestGeneration(job.getId(), null); // This needs ticketContent String now
        // } catch (Exception e) {
        //     logger.error("Error trying to start processing for manually created job {}: {}", job.getId(), e.getMessage());
        //     failJob(job.getId(), "Error starting process: " + e.getMessage());
        // }

        return job;
    }

    // getJobStatus, getActiveJobs, getCompletedJobs, getJob, deleteJob remain largely unchanged
    // ... (keep existing methods from getJobStatus downwards) ...
    
    public Map<String, Object> getJobStatus(String jobId) {
        try {
            Long jobIdLong = Long.parseLong(jobId);
            TestGenerationJob job = testGenerationRepository.findById(jobIdLong).orElse(null);
            if (job == null) {
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
        TestGenerationJob job = getJob(jobId);
        // Consider adding logic to delete associated logs if JobLogService supports it
        // jobLogService.deleteLogsForJob(jobId);
        testGenerationRepository.delete(job);
        logger.info("Deleted job with ID: {}", jobId);
    }
} 