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
            JobLogRepository jobLogRepository,
            JiraTicketAnalyzerAgent jiraTicketAnalyzerAgent) {
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

            // Utilizziamo sempre lo stesso metodo per tutti i tipi di ticket
            addJobLog(job, "INFO", "Processo di generazione test standard avviato");
            processTestGeneration(job.getId(), ticketDto);
        } catch (Exception e) {
            logger.error("Error starting test generation with ticketDto {}", ticketDto, e);
        }
    }

    @Async("taskExecutor")
    protected void processTestGeneration(Long jobId, TicketContentDto ticketDto) {
        try {
            TestGenerationJob job = testGenerationRepository.findById(jobId).orElseThrow();
            
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
                testGenerationRepository.saveAndFlush(job);
                
                completeJob(job.getId());
            } else {
                String errorMsg = "La qualità dei test generati non è sufficiente dopo " + MAX_ATTEMPTS + " tentativi";
                addJobLog(job, "ERROR", errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
            logger.error("Error during test generation process for jobId: " + jobId, e);
            
            // Aggiorna lo stato del job in caso di errore
            TestGenerationJob job = testGenerationRepository.findById(jobId).orElseThrow();
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
            processTestGeneration(job.getId(), null);
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
        testGenerationRepository.saveAndFlush(job);
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
            // Limita il messaggio a 150 caratteri
            log.setMessage(message.length() > 150 ? message.substring(0, 150) + "..." : message);
            log.setTimestamp(LocalDateTime.now());
            
            // Salva solo il log, non è necessario aggiornare il job
            jobLogRepository.save(log);
            
            logger.debug("Log aggiunto con successo per il job {}: {} - {}", job.getId(), level, log.getMessage());
        } catch (Exception e) {
            logger.error("Errore durante l'aggiunta del log per il job {}: {}", job.getId(), e.getMessage());
            // Fallback: logga solo nel logger di sistema
            logger.info("[JOB_{}] {} - {}", job.getId(), level, message);
        }
    }

    private String analyzeValidationAndEnhancePrompt(String validationResults) {
        try {
            StringBuilder enhancedInstructions = new StringBuilder();
            enhancedInstructions.append("\nMiglioramenti richiesti basati sulla validazione precedente:\n");
            
            // Estrai le metriche
            String coherence = "";
            String completeness = "";
            String clarity = "";
            String testData = "";
            
            if (validationResults.contains("Coherence:")) {
                coherence = validationResults.split("Coherence:")[1].split("\n")[0].trim();
            }
            if (validationResults.contains("Completeness:")) {
                completeness = validationResults.split("Completeness:")[1].split("\n")[0].trim();
            }
            if (validationResults.contains("Clarity:")) {
                clarity = validationResults.split("Clarity:")[1].split("\n")[0].trim();
            }
            if (validationResults.contains("TestData:")) {
                testData = validationResults.split("TestData:")[1].split("\n")[0].trim();
            }
            
            // Aggiungi suggerimenti basati sulle metriche
            if (coherence.equals("QUALITY_LOW")) {
                enhancedInstructions.append("- Migliora la coerenza dei test: assicurati che i test siano allineati con i requisiti del ticket\n");
            }
            if (completeness.equals("QUALITY_LOW")) {
                enhancedInstructions.append("- Migliora la completezza: aggiungi test per scenari mancanti\n");
            }
            if (clarity.equals("QUALITY_LOW")) {
                enhancedInstructions.append("- Migliora la chiarezza: rendi i test più leggibili e comprensibili\n");
            }
            if (testData.equals("QUALITY_LOW")) {
                enhancedInstructions.append("- Migliora i dati di test: fornisci dati di test più specifici e realistici\n");
            }
            
            // Estrai e aggiungi i problemi specifici
            if (validationResults.contains("ISSUES:")) {
                String issuesSection = validationResults.split("ISSUES:")[1].split("VALIDAZIONE DETTAGLIATA:")[0];
                String[] issues = issuesSection.split("\n");
                for (String issue : issues) {
                    if (issue.trim().startsWith("Type:")) {
                        String type = issue.split("Type:")[1].trim();
                        String severity = "";
                        String fix = "";
                        
                        for (String line : issues) {
                            if (line.contains("Severity:")) {
                                severity = line.split("Severity:")[1].trim();
                            }
                            if (line.contains("Fix:")) {
                                fix = line.split("Fix:")[1].trim();
                            }
                        }
                        
                        enhancedInstructions.append(String.format(
                            "- Migliora %s (Severità: %s): %s\n",
                            type.toLowerCase(),
                            severity,
                            fix
                        ));
                    }
                }
            }
                        
            return enhancedInstructions.toString();
        } catch (Exception e) {
            logger.error("Errore nell'analisi dei risultati della validazione", e);
            return "";
        }
    }
} 