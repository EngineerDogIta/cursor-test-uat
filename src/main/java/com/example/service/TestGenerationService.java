package com.example.service;

import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.agent.ITicketAnalyzer;
import com.example.config.TestGenerationProperties;
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

@Service
public class TestGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);
    
    private final ITicketAnalyzer ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    private final TestValidatorAgent testValidator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final com.example.services.JobLogService jobLogService;
    private final com.example.services.MetricsAnalysisService metricsAnalysisService;
    private final TestGenerationProperties properties;

    @Autowired
    public TestGenerationService(
            ITicketAnalyzer ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            TestGenerationJobRepository testGenerationRepository,
            com.example.services.JobLogService jobLogService,
            com.example.services.MetricsAnalysisService metricsAnalysisService,
            TestGenerationProperties properties) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.testGenerationRepository = testGenerationRepository;
        this.jobLogService = jobLogService;
        this.metricsAnalysisService = metricsAnalysisService;
        this.properties = properties;
    }

    @Async("taskExecutor")
    public void startTestGeneration(TicketContentDto ticketDto) {
        try {
            TestGenerationJob job = new TestGenerationJob();
            job.setJiraTicket(ticketDto.getTicketId());
            job.setDescription(ticketDto.getContent());
            job.setComponents(String.join(", ", ticketDto.getComponents()));
            job.setStatus(TestGenerationJob.JobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            testGenerationRepository.saveAndFlush(job);
            
            jobLogService.addJobLog(job, "INFO", "Starting test generation process for ticket: " + ticketDto.getTicketId());
            jobLogService.addJobLog(job, "INFO", "Job created with ID: " + job.getId());
            jobLogService.addJobLog(job, "INFO", "Components involved: " + job.getComponents());
            
            jobLogService.addJobLog(job, "INFO", "Standard test generation process started");
            processTestGeneration(job.getId(), ticketDto);
        } catch (Exception e) {
            logger.error("Error starting test generation with ticketDto " + ticketDto, e);
        }
    }

    @Async("taskExecutor")
    protected void processTestGeneration(Long jobId, TicketContentDto ticketDto) {
        try {
            TestGenerationJob job = testGenerationRepository.findById(jobId).orElseThrow();
            
            jobLogService.addJobLog(job, "INFO", "Starting ticket analysis");
            String ticketAnalysis;
            
            if (ticketDto != null) {
                jobLogService.addJobLog(job, "DEBUG", "Ticket content: " + ticketDto.getContent());
                ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            } else {
                java.util.List<String> components = (job.getComponents() != null && !job.getComponents().isEmpty())
                    ? java.util.List.of(job.getComponents().split("\\s+"))
                    : java.util.List.of();
                TicketContentDto jobTicketDto = new TicketContentDto.Builder()
                        .setContent(job.getDescription())
                        .setTicketId(job.getJiraTicket())
                        .setComponents(components)
                        .build();
                ticketAnalysis = ticketAnalyzer.analyzeTicket(jobTicketDto);
            }
            
            jobLogService.addJobLog(job, "INFO", "Ticket analysis completed");
            jobLogService.addJobLog(job, "DEBUG", "Analysis result:\n" + ticketAnalysis);
            
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job);
            jobLogService.addJobLog(job, "INFO", "Job status updated to IN_PROGRESS");
            
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            java.util.List<String> qualityHistory = new java.util.ArrayList<>();
            
            while (!testsValidated && attempts < properties.getMaxAttempts()) {
                attempts++;
                jobLogService.addJobLog(job, "INFO", "Starting iteration " + attempts + " of " + properties.getMaxAttempts());
                
                String enhancedPrompt = "";
                if (attempts > 1 && validationResults != null) {
                    enhancedPrompt = metricsAnalysisService.analyzeValidationAndEnhancePrompt(validationResults);
                    jobLogService.addJobLog(job, "INFO", "Generated structured improvement analysis");
                    jobLogService.addJobLog(job, "DEBUG", "Improvement analysis:\n" + enhancedPrompt);
                    
                    // Log specific sections for better traceability
                    String[] sections = enhancedPrompt.split("\n## ");
                    for (String section : sections) {
                        if (section.startsWith("Quality Metrics") || 
                            section.startsWith("Required Improvements") || 
                            section.startsWith("Specific Issues") ||
                            section.startsWith("Technical Recommendations")) {
                            jobLogService.addJobLog(job, "INFO", "Section: " + section.split("\n")[0]);
                        }
                    }
                }
                
                jobLogService.addJobLog(job, "INFO", "Starting test generation with enhanced context");
                generatedTests = testGenerator.generateTests(ticketAnalysis, enhancedPrompt);
                jobLogService.addJobLog(job, "INFO", "Test generation completed");
                jobLogService.addJobLog(job, "DEBUG", "Generated tests:\n" + generatedTests);
                
                jobLogService.addJobLog(job, "INFO", "Starting test validation");
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                jobLogService.addJobLog(job, "INFO", "Test validation completed");
                jobLogService.addJobLog(job, "DEBUG", "Validation results:\n" + validationResults);
                
                String overallQuality = metricsAnalysisService.extractOverallQuality(validationResults);
                qualityHistory.add(overallQuality);
                
                testsValidated = properties.getAcceptableQualityLevels().contains(overallQuality);
                
                if (!testsValidated && attempts < properties.getMaxAttempts()) {
                    jobLogService.addJobLog(job, "WARN", "Test quality needs improvement (Quality: " + overallQuality + ")");
                    jobLogService.addJobLog(job, "INFO", "Preparing for next iteration with structured improvements");
                }
            }
            
            if (testsValidated) {
                jobLogService.addJobLog(job, "INFO", "Tests validated successfully");
                jobLogService.addJobLog(job, "INFO", "Quality progression: " + String.join(" → ", qualityHistory));
                String finalResult = generatedTests;
                job.setTestResult(finalResult);
                testGenerationRepository.saveAndFlush(job);
                completeJob(job.getId());
            } else {
                String errorMsg = "Test quality did not meet requirements after " + properties.getMaxAttempts() + " iterations";
                jobLogService.addJobLog(job, "ERROR", errorMsg);
                jobLogService.addJobLog(job, "INFO", "Quality progression: " + String.join(" → ", qualityHistory));
                testGenerationRepository.saveAndFlush(job);
                throw new RuntimeException(errorMsg);
            }
        } catch (Exception e) {
            logger.error("Error during test generation process for jobId: " + jobId, e);
            TestGenerationJob job = testGenerationRepository.findById(jobId).orElseThrow();
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
            jobLogService.addJobLog(job, "ERROR", "Process failed: " + e.getMessage());
        }
    }

    
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
    public TestGenerationJob createJob(TestGenerationJob job) {        
        // Salva il job e assicurati che la transazione sia completata
        job = testGenerationRepository.save(job);
        testGenerationRepository.flush(); // Forza il flush della transazione
        
        logger.info("Job created with ID: {} and saved in the database", job.getId());
        
        // Avvia l'elaborazione in modo asincrono solo dopo che il job è stato salvato
        try {
            processTestGeneration(job.getId(), null);
        } catch (Exception e) {
            logger.error("Error starting asynchronous process for job {}: {}", job.getId(), e.getMessage());
            // Aggiorna lo stato del job in caso di errore
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage("Error starting process: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
        }
        
        return job;
    }

    private void completeJob(Long jobId) {
        TestGenerationJob job = getJob(jobId);
        jobLogService.addJobLog(job, "INFO", "Completing job");
        job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        testGenerationRepository.saveAndFlush(job);
        jobLogService.addJobLog(job, "INFO", "Job completed successfully");
    }

    @Transactional
    public void deleteJob(Long jobId) {
        TestGenerationJob job = testGenerationRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job non trovato: " + jobId));
        
        // Verifica solo che il job non sia in esecuzione
        if (job.getStatus() == TestGenerationJob.JobStatus.IN_PROGRESS) {
            throw new RuntimeException("Impossibile eliminare un job in esecuzione");
        }
        
        // Elimina il job e i suoi log (cascade delete)
        testGenerationRepository.delete(job);
        logger.info("Job {} eliminato con successo", jobId);
    }
} 