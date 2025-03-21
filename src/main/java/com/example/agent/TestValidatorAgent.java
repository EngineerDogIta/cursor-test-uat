package com.example.agent;

import com.example.config.TestGenerationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestValidatorAgent {
    private static final Logger logger = LoggerFactory.getLogger(TestValidatorAgent.class);
    private final ChatClient chatClient;
    private final TestGenerationProperties properties;

    @Autowired
    public TestValidatorAgent(ChatClient chatClient, TestGenerationProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    public String validateTests(String ticketAnalysis, String generatedTests) {
        logger.info("Starting test validation");
        logger.debug("Validating tests - Input sizes: ticketAnalysis={} chars, generatedTests={} chars", 
            ticketAnalysis.length(), generatedTests.length());
        logger.debug("Ticket Analysis content preview: {}", 
            ticketAnalysis.substring(0, Math.min(200, ticketAnalysis.length())) + "...");
        logger.debug("Generated Tests content preview: {}", 
            generatedTests.substring(0, Math.min(200, generatedTests.length())) + "...");
        
        try {
            long startTime = System.currentTimeMillis();
            // Base validation first
            Prompt basePrompt = new Prompt(properties.getValidation().getBasePrompt() + 
                "\n\nTicket Analysis:\n" + ticketAnalysis + 
                "\n\nGenerated Tests:\n" + generatedTests);
            String baseValidation = chatClient.call(basePrompt).getResult().getOutput().getContent();
            logger.debug("Base validation completed in {} ms", System.currentTimeMillis() - startTime);
            
            // Detailed validation
            startTime = System.currentTimeMillis();
            String detailedValidation = performValidation(ticketAnalysis, generatedTests);
            logger.debug("Detailed validation completed in {} ms", System.currentTimeMillis() - startTime);
            
            // Combine the results in a simple text format
            String combinedValidation = String.format("""
                BASE VALIDATION:
                %s
                
                DETAILED VALIDATION:
                %s
                """, baseValidation, detailedValidation);
            
            logger.info("Test validation completed successfully");
            logger.debug("Combined validation results size: {} chars", combinedValidation.length());
            logger.debug("Validation results preview: {}", 
                combinedValidation.substring(0, Math.min(300, combinedValidation.length())) + "...");
            return combinedValidation;
            
        } catch (Exception e) {
            logger.error("Error during test validation: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private String performValidation(String ticketAnalysis, String generatedTests) {
        logger.debug("Starting detailed test validation");
        logger.debug("Validation parameters - ticketAnalysis length: {}, generatedTests length: {}", 
            ticketAnalysis.length(), generatedTests.length());
        
        try {
            long startTime = System.currentTimeMillis();
            String validationPrompt = properties.getValidation().getDetailedPrompt()
                + "\n\nTicket Analysis:\n" + ticketAnalysis
                + "\n\nGenerated Tests:\n" + generatedTests;
            
            Prompt prompt = new Prompt(validationPrompt);
            String validation = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.debug("Detailed validation completed in {} ms", System.currentTimeMillis() - startTime);
            logger.debug("Validation result size: {} chars", validation.length());
            return validation;
            
        } catch (Exception e) {
            logger.error("Error during detailed test validation: {} - Stack trace: {}", 
                e.getMessage(), e.getStackTrace());
            throw new RuntimeException("Failed to perform detailed test validation", e);
        }
    }
} 