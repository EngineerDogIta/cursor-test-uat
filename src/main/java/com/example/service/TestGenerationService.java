package com.example.service;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.dto.TicketContentDto;
import com.example.model.GeneratedTest;
import com.example.model.GenerationJob;
import com.example.model.OperationLog;
import com.example.model.TicketContent;
import com.example.repository.GenerationJobRepository;
import com.example.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TestGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);
    
    private final TicketAnalyzerAgent ticketAnalyzer;
    private final TestGeneratorAgent testGenerator;
    private final TestValidatorAgent testValidator;
    private final GenerationJobRepository generationJobRepository;
    private final OperationLogRepository operationLogRepository;

    @Autowired
    public TestGenerationService(
            TicketAnalyzerAgent ticketAnalyzer,
            TestGeneratorAgent testGenerator,
            TestValidatorAgent testValidator,
            GenerationJobRepository generationJobRepository,
            OperationLogRepository operationLogRepository) {
        this.ticketAnalyzer = ticketAnalyzer;
        this.testGenerator = testGenerator;
        this.testValidator = testValidator;
        this.generationJobRepository = generationJobRepository;
        this.operationLogRepository = operationLogRepository;
    }

    @Async("taskExecutor")
    public void startTestGeneration(String jobId, TicketContentDto ticketDto) {
        // Crea e salva il job
        GenerationJob job = new GenerationJob();
        job.setStatus("STARTED");
        job.setTimestamp(LocalDateTime.now());
        generationJobRepository.save(job);

        // Salva il contenuto del ticket
        TicketContent ticketContent = new TicketContent();
        ticketContent.setContent(ticketDto.getContent());
        job.setTicketContent(ticketContent);
        generationJobRepository.save(job);

        // Registra l'operazione
        OperationLog log = new OperationLog();
        log.setOperation("START_TEST_GENERATION");
        log.setJobUid(job.getUid());
        operationLogRepository.save(log);

        processTestGeneration(jobId, ticketDto, job.getUid());
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
                job.setStatus("GENERATING");
                job.setTimestamp(LocalDateTime.now());
                generationJobRepository.save(job);
                
                generatedTests = testGenerator.generateTests(ticketAnalysis);
                validationResults = testValidator.validateTests(ticketAnalysis, generatedTests);
                
                testsValidated = validationResults.contains("\"overallQuality\":\"HIGH\"") || 
                                validationResults.contains("\"overallQuality\":\"MEDIUM\"");
                
                if (!testsValidated && attempts < MAX_ATTEMPTS) {
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
} 