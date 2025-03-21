package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.agent.JiraTicketAnalyzerAgent;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TestGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);
    
    private final TicketAnalyzerAgent ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    private final TestValidatorAgent testValidator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final JobLogRepository jobLogRepository;
    private final JiraTicketAnalyzerAgent jiraTicketAnalyzerAgent;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            TestGenerationJobRepository jobRepository,
            JobLogRepository jobLogRepository,
            JiraTicketAnalyzerAgent jiraTicketAnalyzerAgent) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.testGenerationRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.jiraTicketAnalyzerAgent = jiraTicketAnalyzerAgent;
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

            // Se il ticket proviene da Jira, utilizziamo l'agente dedicato
            if (ticketDto.getTicketId() != null && ticketDto.getTicketId().matches("^[A-Z]+-\\d+$")) {
                addJobLog(job, "INFO", "Rilevato ticket Jira valido, utilizzo analizzatore dedicato");
                processJiraTicketGeneration(job.getId(), ticketDto);
            } else {
                processTestGeneration(job.getId(), ticketDto, job.getId());
            }
        } catch (Exception e) {
            logger.error("Error starting test generation with ticketDto {}", ticketDto, e);
        }
    }

    @Async("taskExecutor")
    protected void processTestGeneration(Long jobId, TicketContentDto ticketDto, Long jobUid) {
        try {
            TestGenerationJob job = testGenerationRepository.findById(jobUid).orElseThrow();
            
            // 1. Analizza il ticket
            addJobLog(job, "INFO", "Inizio analisi del ticket");
            String ticketAnalysis;
            
            if (ticketDto != null) {
                addJobLog(job, "DEBUG", "Contenuto del ticket: " + ticketDto.getContent());
                ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            } else {
                // Se non abbiamo il ticketDto, lo creiamo dai dati del job
                List<String> components = job.getComponents() != null && !job.getComponents().isEmpty() 
                    ? List.of(job.getComponents().split("\\s+")) 
                    : List.of();
                TicketContentDto jobTicketDto = new TicketContentDto(job.getDescription(), job.getJiraTicket(), components);
                ticketAnalysis = ticketAnalyzer.analyzeTicket(jobTicketDto);
            }
            
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
                
                String enhancedPrompt = "";
                if (attempts > 1 && validationResults != null) {
                    // Analizza i risultati precedenti per migliorare il prompt
                    enhancedPrompt = analyzeValidationAndEnhancePrompt(validationResults);
                    addJobLog(job, "INFO", "Prompt migliorato in base alla validazione precedente");
                    addJobLog(job, "DEBUG", "Prompt migliorato: " + enhancedPrompt);
                }
                
                addJobLog(job, "INFO", "Inizio generazione dei test");
                generatedTests = testGenerator.generateTests(ticketAnalysis, enhancedPrompt);
                addJobLog(job, "INFO", "Generazione dei test completata");
                addJobLog(job, "DEBUG", "Test generati: " + generatedTests);
                
                // 3. Validazione dei test
                addJobLog(job, "INFO", "Inizio validazione dei test");
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                addJobLog(job, "INFO", "Validazione dei test completata");
                addJobLog(job, "DEBUG", "Risultati validazione: " + validationResults);
                
                testsValidated = validationResults.contains("OVERALL_QUALITY: QUALITY_HIGH") || 
                                validationResults.contains("OVERALL_QUALITY: QUALITY_MEDIUM");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
                    addJobLog(job, "WARN", "La qualità dei test non è sufficiente, nuovo tentativo...");
                }
            }
            
            // 4. Verifica della qualità finale
            if (testsValidated) {
                addJobLog(job, "INFO", "Test validati con successo");
                
                // Crea il risultato finale
                String finalResult = "# Test generati per " + job.getJiraTicket() + "\n\n"
                    + "## Analisi del ticket\n" + ticketAnalysis + "\n\n"
                    + "## Test UAT\n" + generatedTests + "\n\n"
                    + "## Validazione\n" + validationResults;
                
                // Salva il risultato nel job e completa
                job.setTestResult(finalResult);
                testGenerationRepository.save(job);
                
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

    private void processJiraTicketGeneration(Long jobId, TicketContentDto ticketDto) {
        TestGenerationJob job = testGenerationRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job non trovato con ID: " + jobId));

        try {
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.saveAndFlush(job);
            addJobLog(job, "INFO", "Avvio analisi del ticket Jira");

            // Analisi del ticket con l'agente dedicato
            String ticketAnalysis = jiraTicketAnalyzerAgent.analyzeTicket(ticketDto);
            addJobLog(job, "INFO", "Analisi del ticket completata");
            addJobLog(job, "DEBUG", "Risultato analisi: " + ticketAnalysis);

            // Generazione dei test basati sull'analisi
            addJobLog(job, "INFO", "Inizio generazione dei test");
            String enhancedPrompt = "Genera test UAT per il seguente ticket Jira analizzato";
            String generatedTests = testGenerator.generateTests(ticketAnalysis, enhancedPrompt);
            addJobLog(job, "INFO", "Generazione dei test completata");

            // Validazione dei test generati
            addJobLog(job, "INFO", "Inizio validazione dei test");
            String validationResult = testValidator.validateTests(ticketAnalysis, generatedTests);
            addJobLog(job, "INFO", "Validazione dei test completata");
            addJobLog(job, "DEBUG", "Risultato validazione: " + validationResult);

            // Salvataggio dei risultati
            String finalResult = "# Test generati per " + ticketDto.getTicketId() + "\n\n"
                    + "## Analisi del ticket\n" + ticketAnalysis + "\n\n"
                    + "## Test UAT\n" + generatedTests + "\n\n"
                    + "## Validazione\n" + validationResult;

            // Salva il risultato nel campo testResult
            job.setTestResult(finalResult);
            job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            testGenerationRepository.saveAndFlush(job);
            addJobLog(job, "INFO", "Processo completato con successo");

        } catch (Exception e) {
            job.setStatus(TestGenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            testGenerationRepository.saveAndFlush(job);
            addJobLog(job, "ERROR", "Errore durante la generazione dei test: " + e.getMessage());
            logger.error("Error processing test generation for job {}", jobId, e);
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
            processTestGeneration(job.getId(), null, job.getId());
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

    private String analyzeValidationAndEnhancePrompt(String validationResults) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode validation = mapper.readTree(validationResults);
            
            StringBuilder enhancedInstructions = new StringBuilder();
            enhancedInstructions.append("\nMiglioramenti richiesti basati sulla validazione precedente:\n");
            
            // Analizza i problemi identificati
            JsonNode issues = validation.path("issues");
            for (JsonNode issue : issues) {
                String type = issue.path("type").asText();
                String description = issue.path("description").asText();
                String suggestion = issue.path("suggestion").asText();
                
                enhancedInstructions.append(String.format(
                    "- Migliora %s: %s. Suggerimento: %s\n",
                    type.toLowerCase(),
                    description,
                    suggestion
                ));
            }
            
            // Incorpora le raccomandazioni
            JsonNode recommendations = validation.path("recommendations");
            if (recommendations.isArray() && recommendations.size() > 0) {
                enhancedInstructions.append("\nRaccomandazioni specifiche:\n");
                for (JsonNode rec : recommendations) {
                    enhancedInstructions.append("- ").append(rec.asText()).append("\n");
                }
            }
            
            return enhancedInstructions.toString();
        } catch (Exception e) {
            logger.error("Errore nell'analisi dei risultati della validazione", e);
            return "";
        }
    }
} 