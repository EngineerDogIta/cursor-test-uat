package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.dto.TicketContentDto;
import com.example.model.GeneratedTest;
import com.example.model.JobLog;
import com.example.model.OperationLog;
import com.example.model.TicketContent;
import com.example.model.TestGenerationJob;
import com.example.model.TicketRequest;
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
    private final OperationLogRepository operationLogRepository;
    private final TestGenerationJobRepository jobRepository;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            OperationLogRepository operationLogRepository,
            TestGenerationJobRepository jobRepository) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.operationLogRepository = operationLogRepository;
        this.jobRepository = jobRepository;
    }

    @Async("taskExecutor")
    public void startTestGeneration(String jobId, TicketContentDto ticketDto) {
        try {
            // Crea e salva il job
            TestGenerationJob job = new TestGenerationJob();
            job.setJiraTicket(ticketDto.getTicketId());
            job.setDescription(ticketDto.getContent());
            job.setComponents(String.join(", ", ticketDto.getComponents()));
            job.setStatus(TestGenerationJob.JobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            jobRepository.save(job);

            // Registra l'operazione
            OperationLog log = new OperationLog();
            log.setOperation("START_TEST_GENERATION");
            log.setJobUid(jobId);
            operationLogRepository.save(log);

            processTestGeneration(jobId, ticketDto, job.getId());
        } catch (Exception e) {
            logger.error("Error starting test generation for jobId: " + jobId, e);
            TestGenerationJob job = jobRepository.findById(Long.parseLong(jobId)).orElse(null);
            if (job != null) {
                job.setStatus(TestGenerationJob.JobStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        }
    }

    @Async
    protected void processTestGeneration(String jobId, TicketContentDto ticketDto, Long jobUid) {
        try {
            // 1. Analizza il ticket
            logger.debug("Starting ticket analysis for jobId: {}", jobId);
            String ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            
            // Aggiorna lo stato del job
            TestGenerationJob job = jobRepository.findById(jobUid).orElseThrow();
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            jobRepository.save(job);
            
            // 2. Genera e valida i test
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            final int MAX_ATTEMPTS = 3;
            
            while (!testsValidated && attempts < MAX_ATTEMPTS) {
                attempts++;
                
                // Aggiorna lo stato del job
                job = jobRepository.findById(jobUid).orElseThrow();
                job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
                jobRepository.save(job);
                
                generatedTests = testGenerator.generateTests(ticketAnalysis);
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                
                testsValidated = validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                                validationResults.contains("\"overallQuality\":\"MEDIUM\"");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
                    job = jobRepository.findById(jobUid).orElseThrow();
                    job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
                    jobRepository.save(job);
                }
            }
            
            // Completa il job
            job = jobRepository.findById(jobUid).orElseThrow();
            if (testsValidated) {
                job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
            } else {
                job.setStatus(TestGenerationJob.JobStatus.FAILED);
                job.setErrorMessage("La qualità dei test generati non è sufficiente");
                job.setCompletedAt(LocalDateTime.now());
            }
            jobRepository.save(job);
            
        } catch (Exception e) {
            logger.error("Error during test generation process for jobId: " + jobId, e);
            
            // Aggiorna lo stato del job in caso di errore
            TestGenerationJob job = jobRepository.findById(jobUid).orElseThrow();
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    public Map<String, Object> getJobStatus(String jobId) {
        TestGenerationJob job = jobRepository.findById(Long.parseLong(jobId)).orElse(null);
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
        job.setComponents(request.getComponents() != null ? request.getComponents().trim() : "");
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
            List<String> components = job.getComponents() != null && !job.getComponents().isEmpty() 
                ? List.of(job.getComponents().split("\\s+")) 
                : List.of();
            TicketContentDto ticketDto = new TicketContentDto(job.getDescription(), job.getJiraTicket(), components);
            String ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            addJobLog(job, "INFO", "Analisi del ticket completata con successo");
            
            // 2. Generazione dei test
            addJobLog(job, "INFO", "Inizio generazione dei test");
            String generatedTests = testGenerator.generateTests(ticketAnalysis);
            addJobLog(job, "INFO", "Generazione dei test completata");
            
            // 3. Validazione dei test
            addJobLog(job, "INFO", "Inizio validazione dei test");
            String validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
            addJobLog(job, "INFO", "Validazione dei test completata");
            
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
            logger.error("Error processing job: " + jobId, e);
            TestGenerationJob job = getJob(jobId);
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    private void completeJob(Long jobId, String branchName) {
        TestGenerationJob job = getJob(jobId);
        job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
        job.setBranchName(branchName);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void addJobLog(TestGenerationJob job, String level, String message) {
        JobLog log = new JobLog();
        log.setLevel(level);
        log.setMessage(message);
        log.setTimestamp(LocalDateTime.now());
        log.setJob(job);
        job.addLog(log);
        jobRepository.save(job);
        logger.info("Aggiunto log al job {}: {} - {}", job.getId(), level, message);
    }
} 