package com.example.controller;

import com.example.agent.TicketAnalyzerAgent;
import com.example.agent.TestGeneratorAgent;
import com.example.agent.TestValidatorAgent;
import com.example.dto.TicketContentDto;
import com.example.service.TestGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class TestGenerationController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationController.class);
    
    private final TestGenerationService testGenerationService;

    @Autowired
    public TestGenerationController(TestGenerationService testGenerationService) {
        this.testGenerationService = testGenerationService;
        logger.info("TestGenerationController initialized with TestGenerationService");
    }

    @PostMapping("/generate-tests/async")
    public ResponseEntity<Map<String, String>> startTestGeneration(@RequestBody TicketContentDto ticketDto) {
        String jobId = UUID.randomUUID().toString();
        logger.info("Starting asynchronous test generation for ticket: {} with jobId: {}", 
                   ticketDto.getTicketId(), jobId);
        
        // Avvia il processo in modo asincrono
        testGenerationService.startTestGeneration(jobId, ticketDto);
        
        return ResponseEntity.ok(Map.of(
            "jobId", jobId,
            "status", "STARTED",
            "message", "Test generation process started"
        ));
    }

    @GetMapping("/generate-tests/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getTestGenerationStatus(@PathVariable String jobId) {
        logger.info("Checking status for jobId: {}", jobId);
        
        Map<String, Object> status = testGenerationService.getJobStatus(jobId);
        return ResponseEntity.ok(status);
    }
} 