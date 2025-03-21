package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.dto.TicketContentDto;
import com.example.model.JobLog;
import com.example.model.TestGenerationJob;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
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
    private final TestGenerationJobRepository testGenerationRepository;
    private final JobLogRepository jobLogRepository;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            TestGenerationJobRepository jobRepository,
            JobLogRepository jobLogRepository) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.testGenerationRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
    }

    @Async("taskExecutor")
    public void startTestGeneration(TicketContentDto ticketDto) {
        try {
            // Crea e salva il job
            TestGenerationJob job = new TestGenerationJob();
            job.setJiraTicket(ticketDto.getTicketId());
            job.setDescription(ticketDto.getContent());
            job.setComponents(String.join(", ", ticketDto.getComponents()));
            job.setStatus(TestGenerationJob.JobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            testGenerationRepository.saveAndFlush(job);
            
            // Ora possiamo loggare con il job creato
            addJobLog(job, "INFO", "Inizio processo di generazione test per il ticket: " + ticketDto.getTicketId());
            addJobLog(job, "INFO", "Job creato con ID: " + job.getId());
            addJobLog(job, "INFO", "Componenti coinvolti: " + job.getComponents());

            processTestGeneration(job.getId(), ticketDto, job.getId());
        } catch (Exception e) {
            logger.error("Error starting test generation with ticketDto {}", ticketDto, e);
        }
    }

    @Async
    protected void processTestGeneration(Long jobId, TicketContentDto ticketDto, Long jobUid) {
        try {
            TestGenerationJob job = testGenerationRepository.findById(jobUid).orElseThrow();
            
            // 1. Analizza il ticket
            addJobLog(job, "INFO", "Inizio analisi del ticket");
            addJobLog(job, "DEBUG", "Contenuto del ticket: " + ticketDto.getContent());
            String ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            addJobLog(job, "INFO", "Analisi del ticket completata");
            addJobLog(job, "DEBUG", "Risultato analisi: " + ticketAnalysis);
            
            // Aggiorna lo stato del job
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job);
            addJobLog(job, "INFO", "Stato del job aggiornato a IN_PROGRESS");
            
            // 2. Genera e valida i test
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            final int MAX_ATTEMPTS = 3;
            
            while (!testsValidated && attempts < MAX_ATTEMPTS) {
                attempts++;
                addJobLog(job, "INFO", "Tentativo " + attempts + " di " + MAX_ATTEMPTS);
                
                addJobLog(job, "INFO", "Inizio generazione dei test");
                generatedTests = testGenerator.generateTests(ticketAnalysis);
                addJobLog(job, "INFO", "Generazione dei test completata");
                addJobLog(job, "DEBUG", "Test generati: " + generatedTests);
                
                // 3. Validazione dei test
                addJobLog(job, "INFO", "Inizio validazione dei test");
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                addJobLog(job, "INFO", "Validazione dei test completata");
                addJobLog(job, "DEBUG", "Risultati validazione: " + validationResults);
                
                testsValidated = validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                                validationResults.contains("\"overallQuality\":\"MEDIUM\"");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
                    addJobLog(job, "WARN", "La qualità dei test non è sufficiente, nuovo tentativo...");
                }
            }
            
            // 4. Verifica della qualità finale
            if (testsValidated) {
                addJobLog(job, "INFO", "Test validati con successo");
                completeJob(job.getId());
            } else {
                String errorMsg = "La qualità dei test generati non è sufficiente dopo " + MAX_ATTEMPTS + " tentativi";
                addJobLog(job, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
            logger.error("Error during test generation process for jobId: " + jobId, e);
            
            // Aggiorna lo stato del job in caso di errore
            TestGenerationJob job = testGenerationRepository.findById(jobUid).orElseThrow();
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
            addJobLog(job, "ERROR", "Errore durante il processo: " + e.getMessage());
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
        
        logger.info("Job creato con ID: {} e salvato nel database", job.getId());
        
        // Avvia l'elaborazione in modo asincrono solo dopo che il job è stato salvato
        try {
            processJobAsync(job.getId());
        } catch (Exception e) {
            logger.error("Errore nell'avvio del processo asincrono per il job {}: {}", job.getId(), e.getMessage());
            // Aggiorna lo stato del job in caso di errore
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage("Errore nell'avvio del processo: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
        }
        
        return job;
    }

    @Async("taskExecutor")
    protected void processJobAsync(Long jobId) {
        try {
            TestGenerationJob job = getJob(jobId);
            
            // Aggiorna lo stato a IN_PROGRESS
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job);
            
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
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            final int MAX_ATTEMPTS = 3;
            
            while (!testsValidated && attempts < MAX_ATTEMPTS) {
                attempts++;
                addJobLog(job, "INFO", "Tentativo " + attempts + " di " + MAX_ATTEMPTS);
                
                generatedTests = testGenerator.generateTests(ticketAnalysis);
                addJobLog(job, "INFO", "Generazione dei test completata");
                
                // 3. Validazione dei test
                addJobLog(job, "INFO", "Inizio validazione dei test");
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                addJobLog(job, "INFO", "Validazione dei test completata");
                
                testsValidated = validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                                validationResults.contains("\"overallQuality\":\"MEDIUM\"");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
                    addJobLog(job, "INFO", "La qualità dei test non è sufficiente, nuovo tentativo...");
                }
            }
            
            // 4. Verifica della qualità finale
            if (testsValidated) {
                addJobLog(job, "INFO", "Test validati con successo");
                completeJob(job.getId());
            } else {
                throw new RuntimeException("La qualità dei test generati non è sufficiente dopo " + MAX_ATTEMPTS + " tentativi");
            }
            
        } catch (Exception e) {
            logger.error("Error processing job: " + jobId, e);
            TestGenerationJob job = getJob(jobId);
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.save(job);
        }
    }

    private void completeJob(Long jobId) {
        TestGenerationJob job = getJob(jobId);
        addJobLog(job, "INFO", "Completamento del job");
        job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        testGenerationRepository.save(job);
        addJobLog(job, "INFO", "Job completato con successo");
    }

    @Transactional
    private void addJobLog(TestGenerationJob job, String level, String message) {
        if (job == null) {
            // Se il job è null, logga solo nel logger di sistema
            logger.info("[NO_JOB] {} - {}", level, message);
            return;
        }
        
        try {
            // Ricarica il job per assicurarsi che sia nella sessione corrente
            job = testGenerationRepository.findById(job.getId()).orElseThrow();
            
            JobLog log = new JobLog();
            log.setJob(job);
            log.setLevel(level);
            log.setMessage(message);
            log.setTimestamp(LocalDateTime.now());
            
            // Salva solo il log, non è necessario aggiornare il job
            jobLogRepository.save(log);
            
            logger.debug("Log aggiunto con successo per il job {}: {} - {}", job.getId(), level, message);
        } catch (Exception e) {
            logger.error("Errore durante l'aggiunta del log per il job {}: {}", job.getId(), e.getMessage());
            // Fallback: logga solo nel logger di sistema
            logger.info("[JOB_{}] {} - {}", job.getId(), level, message);
        }
    }
} 