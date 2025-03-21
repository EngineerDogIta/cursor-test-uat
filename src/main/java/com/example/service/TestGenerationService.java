package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.agent.JiraTicketAnalyzerAgent;
import com.example.config.TestGenerationProperties;
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
import java.util.ArrayList;

@Service
public class TestGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);
    
    private final TicketAnalyzerAgent ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    private final TestValidatorAgent testValidator;
    private final TestGenerationJobRepository testGenerationRepository;
    private final JobLogRepository jobLogRepository;
    private final TestGenerationProperties properties;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            TestGenerationJobRepository jobRepository,
            JobLogRepository jobLogRepository,
            JiraTicketAnalyzerAgent jiraTicketAnalyzerAgent,
            TestGenerationProperties properties) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.testGenerationRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.properties = properties;
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
            addJobLog(job, "INFO", "Starting test generation process for ticket: " + ticketDto.getTicketId());
            addJobLog(job, "INFO", "Job created with ID: " + job.getId());
            addJobLog(job, "INFO", "Components involved: " + job.getComponents());

            // Utilizziamo sempre lo stesso metodo per tutti i tipi di ticket
            addJobLog(job, "INFO", "Standard test generation process started");
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
            addJobLog(job, "INFO", "Starting ticket analysis");
            String ticketAnalysis;
            
            if (ticketDto != null) {
                addJobLog(job, "DEBUG", "Ticket content: " + ticketDto.getContent());
                ticketAnalysis = ticketAnalyzer.analyzeTicket(ticketDto);
            } else {
                // Se non abbiamo il ticketDto, lo creiamo dai dati del job
                List<String> components = job.getComponents() != null && !job.getComponents().isEmpty() 
                    ? List.of(job.getComponents().split("\\s+")) 
                    : List.of();
                TicketContentDto jobTicketDto = new TicketContentDto(job.getDescription(), job.getJiraTicket(), components);
                ticketAnalysis = ticketAnalyzer.analyzeTicket(jobTicketDto);
            }
            
            addJobLog(job, "INFO", "Ticket analysis completed");
            addJobLog(job, "DEBUG", "Analysis result: " + ticketAnalysis);
            
            // Aggiorna lo stato del job
            job.setStatus(TestGenerationJob.JobStatus.IN_PROGRESS);
            testGenerationRepository.save(job);
            addJobLog(job, "INFO", "Job status updated to IN_PROGRESS");
            
            // 2. Genera e valida i test
            String generatedTests = null;
            String validationResults = null;
            boolean testsValidated = false;
            int attempts = 0;
            
            // Tracciamento della qualità per ogni tentativo
            List<String> qualityHistory = new ArrayList<>();
            
            while (!testsValidated && attempts < properties.getMaxAttempts()) {
                attempts++;
                addJobLog(job, "INFO", "Attempt " + attempts + " of " + properties.getMaxAttempts());
                
                String enhancedPrompt = "";
                if (attempts > 1 && validationResults != null) {
                    // Analizza i risultati precedenti per migliorare il prompt
                    enhancedPrompt = analyzeValidationAndEnhancePrompt(validationResults);
                    addJobLog(job, "INFO", "Prompt improved based on previous validation");
                    addJobLog(job, "DEBUG", "Improved prompt: " + enhancedPrompt);
                }
                
                addJobLog(job, "INFO", "Starting test generation");
                generatedTests = testGenerator.generateTests(ticketAnalysis, enhancedPrompt);
                addJobLog(job, "INFO", "Test generation completed");
                addJobLog(job, "DEBUG", "Generated tests: " + generatedTests);
                
                // 3. Validazione dei test
                addJobLog(job, "INFO", "Starting test validation");
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                addJobLog(job, "INFO", "Test validation completed");
                addJobLog(job, "DEBUG", "Validation results: " + validationResults);
                
                // Estrai e salva la qualità complessiva per questo tentativo
                String overallQuality = extractOverallQuality(validationResults);
                qualityHistory.add(overallQuality);
                
                testsValidated = properties.getAcceptableQualityLevels().contains(overallQuality);
                
                if (!testsValidated && attempts < properties.getMaxAttempts()) {
                    addJobLog(job, "WARN", "Test quality is not sufficient, retrying...");
                    addJobLog(job, "INFO", "Current quality: " + overallQuality);
                }
            }
            
            // 4. Verifica della qualità finale
            if (testsValidated) {
                addJobLog(job, "INFO", "Tests validated successfully");
                addJobLog(job, "INFO", "Quality history: " + String.join(" → ", qualityHistory));
                
                // Crea il risultato finale
                String finalResult = generatedTests;
                
                // Salva il risultato nel job e completa
                job.setTestResult(finalResult);
                testGenerationRepository.saveAndFlush(job);
                
                completeJob(job.getId());
            } else {
                String errorMsg = "Test quality is not sufficient after " + properties.getMaxAttempts() + " attempts";
                addJobLog(job, "ERROR", errorMsg);
                addJobLog(job, "INFO", "Quality history: " + String.join(" → ", qualityHistory));
                
                // Salva comunque i dati utili per il debug
                testGenerationRepository.saveAndFlush(job);
                
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
            addJobLog(job, "ERROR", "Error during process: " + e.getMessage());
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
        addJobLog(job, "INFO", "Completing job");
        job.setStatus(TestGenerationJob.JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        testGenerationRepository.saveAndFlush(job);
        addJobLog(job, "INFO", "Job completed successfully");
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
            
            logger.debug("Log added successfully for job {}: {} - {}", job.getId(), level, log.getMessage());
        } catch (Exception e) {
            logger.error("Error during adding log for job {}: {}", job.getId(), e.getMessage());
            // Fallback: logga solo nel logger di sistema
            logger.info("[JOB_{}] {} - {}", job.getId(), level, message);
        }
    }

    /**
     * Classe per gestire le metriche di validazione in modo strutturato
     */
    private static class ValidationMetrics {
        private String coherence = "";
        private String completeness = "";
        private String clarity = "";
        private String testData = "";
        private List<String[]> issues = new ArrayList<>();
        
        public boolean isMetricLow(String metric) {
            return metric != null && !metric.isEmpty() && metric.equals("QUALITY_LOW");
        }
    }

    /**
     * Analizza i risultati della validazione e crea un prompt migliorato
     * per la generazione successiva
     */
    private String analyzeValidationAndEnhancePrompt(String validationResults) {
        try {
            // Estrazione e analisi delle metriche
            ValidationMetrics metrics = extractValidationMetrics(validationResults);
            StringBuilder enhancedInstructions = new StringBuilder();
            
            enhancedInstructions.append("\nImprovements required based on previous validation:\n");
            
            // Aggiunge suggerimenti in base alle metriche con qualità bassa
            if (metrics.isMetricLow(metrics.coherence)) {
                enhancedInstructions.append("- Improve test coherence: ensure tests are aligned with ticket requirements\n");
            }
            if (metrics.isMetricLow(metrics.completeness)) {
                enhancedInstructions.append("- Improve completeness: add tests for missing scenarios\n");
            }
            if (metrics.isMetricLow(metrics.clarity)) {
                enhancedInstructions.append("- Improve clarity: make tests more readable and understandable\n");
            }
            if (metrics.isMetricLow(metrics.testData)) {
                enhancedInstructions.append("- Improve test data: provide more specific and realistic test data\n");
            }
            
            // Aggiunge i problemi specifici individuati durante la validazione
            if (!metrics.issues.isEmpty()) {
                enhancedInstructions.append("\nSpecific issues to resolve:\n");
                for (String[] issue : metrics.issues) {
                    if (issue.length >= 3) {
                        String type = issue[0];
                        String severity = issue[1];
                        String fix = issue[2];
                        
                        enhancedInstructions.append(String.format(
                            "- Improve %s (Severity: %s): %s\n",
                            type.toLowerCase(),
                            severity,
                            fix
                        ));
                    }
                }
            }
            
            return enhancedInstructions.toString();
        } catch (Exception e) {
            logger.error("Error analyzing validation results", e);
            // In caso di errore, restituisce un prompt generico
            return "\nImprove test quality to meet validation requirements.\n";
        }
    }

    /**
     * Estrae le metriche di validazione dal testo dei risultati
     * gestendo in modo sicuro i possibili errori di formato
     */
    private ValidationMetrics extractValidationMetrics(String validationResults) {
        ValidationMetrics metrics = new ValidationMetrics();
        
        if (validationResults == null || validationResults.isEmpty()) {
            logger.warn("Empty or null validation results");
            return metrics;
        }
        
        try {
            // Estrazione delle metriche principali
            String[] lines = validationResults.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("Coherence:")) {
                    metrics.coherence = trimmedLine.substring("Coherence:".length()).trim();
                } else if (trimmedLine.startsWith("Completeness:")) {
                    metrics.completeness = trimmedLine.substring("Completeness:".length()).trim();
                } else if (trimmedLine.startsWith("Clarity:")) {
                    metrics.clarity = trimmedLine.substring("Clarity:".length()).trim();
                } else if (trimmedLine.startsWith("TestData:")) {
                    metrics.testData = trimmedLine.substring("TestData:".length()).trim();
                }
            }
            
            // Estrazione dei problemi specifici in modo più sicuro
            if (validationResults.contains("ISSUES:")) {
                try {
                    String[] parts = validationResults.split("ISSUES:");
                    if (parts.length > 1) {
                        String issuesPart = parts[1];
                        
                        // Se c'è una sezione successiva, limita la parte di issues
                        if (issuesPart.contains("DETAILED VALIDATION:")) {
                            issuesPart = issuesPart.split("DETAILED VALIDATION:")[0];
                        }
                        
                        // Analisi dei problemi riga per riga
                        String[] issueLines = issuesPart.split("\n");
                        String type = null;
                        String severity = null;
                        String fix = null;
                        
                        for (String line : issueLines) {
                            String trimmedLine = line.trim();
                            if (trimmedLine.startsWith("Type:")) {
                                // Se abbiamo già un problema completo, lo aggiungiamo alla lista
                                if (type != null && severity != null && fix != null) {
                                    metrics.issues.add(new String[]{type, severity, fix});
                                }
                                
                                // Iniziamo un nuovo problema
                                type = trimmedLine.substring("Type:".length()).trim();
                                severity = null;
                                fix = null;
                            } else if (trimmedLine.startsWith("Severity:")) {
                                severity = trimmedLine.substring("Severity:".length()).trim();
                            } else if (trimmedLine.startsWith("Fix:")) {
                                fix = trimmedLine.substring("Fix:".length()).trim();
                            }
                        }
                        
                        // Aggiungiamo l'ultimo problema se è completo
                        if (type != null && severity != null && fix != null) {
                            metrics.issues.add(new String[]{type, severity, fix});
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing ISSUES section", e);
                    // Continuiamo con le metriche che abbiamo già estratto
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting validation metrics", e);
            // Continuiamo con un oggetto metriche vuoto ma valido
        }
        
        return metrics;
    }

    /**
     * Estrae la qualità complessiva dai risultati della validazione
     */
    private String extractOverallQuality(String validationResults) {
        if (validationResults == null || validationResults.isEmpty()) {
            return "UNKNOWN";
        }
        
        if (validationResults.contains("OVERALL_QUALITY: QUALITY_HIGH")) {
            return "QUALITY_HIGH";
        } else if (validationResults.contains("OVERALL_QUALITY: QUALITY_MEDIUM")) {
            return "QUALITY_MEDIUM";
        } else if (validationResults.contains("OVERALL_QUALITY: QUALITY_LOW")) {
            return "QUALITY_LOW";
        } else {
            return "UNKNOWN";
        }
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