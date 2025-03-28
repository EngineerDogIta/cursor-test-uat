package com.example.controller;

import com.example.dto.TicketContentDto;
import com.example.dto.TestGenerationResponseDto;
import com.example.service.TestGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
public class TestGenerationController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestGenerationController.class);
    
    private final TestGenerationService testGenerationService;

    @Autowired
    public TestGenerationController(TestGenerationService testGenerationService) {
        this.testGenerationService = testGenerationService;
        logger.info("TestGenerationController initialized with TestGenerationService");
    }

    @PostMapping("/generate-tests/async")
    public ResponseEntity<TestGenerationResponseDto> startTestGeneration(@Valid @RequestBody TicketContentDto ticketDto) {
        if (ticketDto.getContent() == null || ticketDto.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new TestGenerationResponseDto(
                null,
                null,
                "Il contenuto del ticket non può essere vuoto"
            ));
        }

        if (ticketDto.getTicketId() == null || ticketDto.getTicketId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new TestGenerationResponseDto(
                null,
                null,
                "L'ID del ticket non può essere vuoto"
            ));
        }

        String jobId = UUID.randomUUID().toString();
        logger.info("Starting asynchronous test generation for ticket: {} with jobId: {}", 
                   ticketDto.getTicketId(), jobId);
        
        testGenerationService.startTestGeneration(ticketDto);
        
        return ResponseEntity.ok(new TestGenerationResponseDto(
            jobId,
            "Generazione dei test avviata con successo",
            null
        ));
    }

    @GetMapping("/generate-tests/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getTestGenerationStatus(@PathVariable String jobId) {
        logger.info("Checking status for jobId: {}", jobId);
        Map<String, Object> status = testGenerationService.getJobStatus(jobId);
        
        if (status == null || status.get("status").equals("NOT_FOUND")) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Job non trovato"
            ));
        }
        
        return ResponseEntity.ok(status);
    }
} 