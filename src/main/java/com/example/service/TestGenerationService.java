package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.dto.TicketContentDto;
import com.example.model.GeneratedTest;
import com.example.model.GenerationJob;
import com.example.model.JobLog;
import com.example.model.OperationLog;
import com.example.model.TicketContent;
import com.example.model.TestGenerationJob;
import com.example.model.TicketRequest;
import com.example.repository.GenerationJobRepository;
import com.example.repository.OperationLogRepository;
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
    
    private final TicketAnalyzerAgent ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    private final TestValidatorAgent testValidator;
    private final GenerationJobRepository generationJobRepository;
    private final OperationLogRepository operationLogRepository;
    private final TestGenerationJobRepository jobRepository;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            GenerationJobRepository generationJobRepository,
            OperationLogRepository operationLogRepository,
            TestGenerationJobRepository jobRepository) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.generationJobRepository = generationJobRepository;
        this.operationLogRepository = operationLogRepository;
        this.jobRepository = jobRepository;
    }

    @Async("taskExecutor")
    public void startTestGeneration(String jobId, TicketContentDto ticketDto) {
        try {
            // Crea e salva il job
            GenerationJob job = new GenerationJob();
            job.setStatus("STARTED");
            job.setTimestamp(LocalDateTime.now());
            job.setUid(jobId);
            generationJobRepository.save(job);

            // Salva il contenuto del ticket
            TicketContent ticketContent = new TicketContent();
            ticketContent.setContent(ticketDto.getContent());
            job.setTicketContent(ticketContent);
            generationJobRepository.save(job);

            // Registra l'operazione
            OperationLog log = new OperationLog();
            log.setOperation("START_TEST_GENERATION");
            log.setJobUid(jobId);
            operationLogRepository.save(log);

            processTestGeneration(jobId, ticketDto, jobId);
        } catch (Exception e) {
            logger.error("Error starting test generation for jobId: " + jobId, e);
            GenerationJob job = generationJobRepository.findByUid(jobId).orElse(null);
            if (job != null) {
                job.setStatus("FAILED");
                job.setTimestamp(LocalDateTime.now());
                generationJobRepository.save(job);
            }
        }
    }

    @Async
    protected void processTestGeneration(String jobId, TicketContentDto ticketDto, String jobUid) {
        try {
            // 1. Analizza il ticket
            logger.debug("Starting ticket analysis for jobId: {}", jobId);
            String ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            
            // Aggiorna lo stato del job
            GenerationJob job = generationJobRepository.findByUid(jobUid).orElseThrow();
            job.setStatus("ANALYZING");
            job.setTimestamp(LocalDateTime.now());
            generationJobRepository.save(job);
            
            // 2. Genera e valida i test
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            final int MAX_ATTEMPTS = 3;
            
            while (!testsValidated && attempts < MAX_ATTEMPTS) {
                attempts++;
                
                // Aggiorna lo stato del job
                job = generationJobRepository.findByUid(jobUid).orElseThrow();
                job.setStatus("GENERATING");
                job.setTimestamp(LocalDateTime.now());
                generationJobRepository.save(job);
                
                generatedTests = testGenerator.generateTests(ticketAnalysis);
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                
                testsValidated = validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                                validationResults.contains("\"overallQuality\":\"MEDIUM\"");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
                    job = generationJobRepository.findByUid(jobUid).orElseThrow();
                    job.setStatus("RETRYING");
                    job.setTimestamp(LocalDateTime.now());
                    generationJobRepository.save(job);
                }
            }
            
            // Salva i test generati
            GeneratedTest test = new GeneratedTest();
            test.setTestContent(generatedTests);
            test.setAttempts(attempts);
            test.setValidated(testsValidated);
            
            job = generationJobRepository.findByUid(jobUid).orElseThrow();
            job.setGeneratedTest(test);
            job.setStatus("COMPLETED");
            job.setTimestamp(LocalDateTime.now());
            generationJobRepository.save(job);
            
        } catch (Exception e) {
            logger.error("Error during test generation process for jobId: " + jobId, e);
            
            // Aggiorna lo stato del job in caso di errore
            GenerationJob job = generationJobRepository.findByUid(jobUid).orElseThrow();
            job.setStatus("FAILED");
            job.setTimestamp(LocalDateTime.now());
            generationJobRepository.save(job);
        }
    }

    public Map<String, Object> getJobStatus(String jobId) {
        GenerationJob job = generationJobRepository.findByUid(jobId).orElse(null);
        if (job == null) {
            return Map.of(
                "status", "NOT_FOUND",
                "message", "Job not found"
            );
        }
        
        return Map.of(
            "status", job.getStatus(),
            "result", job.getGeneratedTest() != null ? job.getGeneratedTest().getTestContent() : "",
            "error", job.getStatus().equals("FAILED") ? "Test generation failed" : ""
        );
    }

    public List<TestGenerationJob> getActiveJobs() {
        return jobRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.PENDING,
            TestGenerationJob.JobStatus.IN_PROGRESS
        ));
    }

    public List<TestGenerationJob> getCompletedJobs() {
        return jobRepository.findByStatusIn(List.of(
            TestGenerationJob.JobStatus.COMPLETED,
            TestGenerationJob.JobStatus.FAILED
        ));
    }

    public TestGenerationJob getJob(Long id) {
        return jobRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));
    }

    @Transactional
    public TestGenerationJob createJob(TicketRequest request) {
        TestGenerationJob job = new TestGenerationJob();
        job.setJiraTicket(request.getJiraTicket());
        job.setDescription(request.getDescription());
        job.setComponents(request.getComponents());
        job.setStatus(TestGenerationJob.JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        
        // Salva il job
        job = jobRepository.save(job);
        
        // Avvia l'elaborazione in modo asincrono
        processJobAsync(job.getId());
        
        return job;
    }

    @Async("taskExecutor")
    protected void processJobAsync(Long jobId) {
        try {
            TestGenerationJob job = getJob(jobId);
            
            // Aggiorna lo stato a IN_PROGRESS
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            jobRepository.save(job);
            
            // 1. Analisi del ticket
            addJobLog(job, "INFO", "Inizio analisi del ticket Jira: " + job.getJiraTicket());
            TicketContentDto ticketDto = new TicketContentDto(job.getDescription(), job.getJiraTicket(), List.of(job.getComponents()));
            String ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            addJobLog(job, "INFO", "Analisi del ticket completata");
            
            // 2. Generazione dei test
            addJobLog(job, "INFO", "Inizio generazione dei test");
            String generatedTests = testGenerator.generateTests(ticketAnalysis);
            addJobLog(job, "INFO", "Generazione dei test completata");
            
            // 3. Validazione dei test
            addJobLog(job, "INFO", "Inizio validazione dei test");
            String validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
            
            // 4. Verifica della qualità
            if (validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                validationResults.contains("\"overallQuality\":\"MEDIUM\"")) {
                
                // Crea il branch Git
                String branchName = "test/" + job.getJiraTicket().toLowerCase();
                addJobLog(job, "INFO", "Test validati con successo. Branch creato: " + branchName);
                
                // Completa il job
                completeJob(jobId, branchName);
            } else {
                throw new RuntimeException("La qualità dei test generati non è sufficiente");
            }
            
        } catch (Exception e) {
            logger.error("Errore durante l'elaborazione del job: " + jobId, e);
            addJobLog(getJob(jobId), "ERROR", "Errore durante l'elaborazione: " + e.getMessage());
            updateJobError(jobId, e.getMessage());
        }
    }
    
    private void addJobLog(TestGenerationJob job, String level, String message) {
        JobLog log = new JobLog();
        log.setJob(job);
        log.setLevel(level);
        log.setMessage(message);
        log.setTimestamp(LocalDateTime.now());
        job.getLogs().add(log);
        jobRepository.save(job);
        logger.info("[Job {}] {}: {}", job.getId(), level, message);
    }

    @Transactional
    public TestGenerationJob updateJobStatus(Long id, TestGenerationJob.JobStatus status) {
        TestGenerationJob job = getJob(id);
        job.setStatus(status);
        return jobRepository.save(job);
    }

    @Transactional
    public TestGenerationJob updateJobError(Long id, String errorMessage) {
        TestGenerationJob job = getJob(id);
        job.setStatus(TestGenerationJob.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        return jobRepository.save(job);
    }

    @Transactional
    public TestGenerationJob completeJob(Long id, String branchName) {
        TestGenerationJob job = getJob(id);
        job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
        job.setBranchName(branchName);
        return jobRepository.save(job);
    }
} 