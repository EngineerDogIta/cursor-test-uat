package com.example.agent;

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
    private static final String SYSTEM_PROMPT = """
        Sei un agente specializzato nella validazione della qualità dei casi di test UAT.
        Il tuo compito è verificare:
        1. Coerenza tra analisi del ticket e scenari generati
        2. Completezza degli scenari (positivi, negativi, limite)
        3. Chiarezza e dettaglio degli steps
        4. Validità dei dati di test
        5. Copertura delle dipendenze
        6. Possibilità di automazione
        
        Fornisci l'output in formato JSON con la seguente struttura:
        {
            "validationResults": {
                "coherence": "HIGH/MEDIUM/LOW",
                "completeness": "HIGH/MEDIUM/LOW",
                "clarity": "HIGH/MEDIUM/LOW",
                "testDataQuality": "HIGH/MEDIUM/LOW",
                "dependenciesCoverage": "HIGH/MEDIUM/LOW",
                "automationPotential": "HIGH/MEDIUM/LOW"
            },
            "issues": [
                {
                    "type": "COHERENCE/COMPLETENESS/CLARITY/DATA/DEPENDENCIES/AUTOMATION",
                    "description": "descrizione del problema",
                    "severity": "HIGH/MEDIUM/LOW",
                    "suggestion": "suggerimento di miglioramento"
                }
            ],
            "overallQuality": "HIGH/MEDIUM/LOW",
            "recommendations": ["raccomandazione1", "raccomandazione2"]
        }
        """;

    @Autowired
    public TestValidatorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String validateTests(String ticketAnalysis, String generatedTests) {
        logger.info("Starting test validation");
        logger.debug("Validating tests against ticket analysis: {}", ticketAnalysis);
        logger.debug("Generated tests to validate: {}", generatedTests);
        
        try {
            // Prima validazione base
            Prompt basePrompt = new Prompt(SYSTEM_PROMPT + 
                "\n\nAnalisi del ticket:\n" + ticketAnalysis + 
                "\n\nTest generati:\n" + generatedTests);
            String baseValidation = chatClient.call(basePrompt).getResult().getOutput().getContent();
            
            // Validazione dettagliata
            String detailedValidation = performValidation(ticketAnalysis, generatedTests);
            
            // Combiniamo i risultati
            String combinedValidation = String.format("""
                {
                    "baseValidation": %s,
                    "detailedValidation": %s
                }
                """, baseValidation, detailedValidation);
            
            logger.info("Test validation completed successfully");
            logger.debug("Combined validation results: {}", combinedValidation);
            return combinedValidation;
            
        } catch (Exception e) {
            logger.error("Error during test validation", e);
            throw e;
        }
    }
    
    private String performValidation(String ticketAnalysis, String generatedTests) {
        logger.debug("Performing detailed test validation");
        
        try {
            String validationPrompt = """
                Esegui una validazione tecnica approfondita dei test generati rispetto all'analisi del ticket.
                Considera:
                1. Copertura degli scenari critici
                2. Validità dei dati di test
                3. Gestione degli errori
                4. Performance e scalabilità
                5. Sicurezza e privacy
                6. Manutenibilità dei test
                
                Analisi del ticket:
                %s
                
                Test da validare:
                %s
                
                Fornisci l'output in formato JSON con la seguente struttura:
                {
                    "technicalValidation": {
                        "criticalScenariosCoverage": "HIGH/MEDIUM/LOW",
                        "testDataValidity": "HIGH/MEDIUM/LOW",
                        "errorHandling": "HIGH/MEDIUM/LOW",
                        "performanceConsiderations": "HIGH/MEDIUM/LOW",
                        "securityCompliance": "HIGH/MEDIUM/LOW",
                        "maintainability": "HIGH/MEDIUM/LOW"
                    },
                    "technicalIssues": [
                        {
                            "category": "SCENARIOS/DATA/ERRORS/PERFORMANCE/SECURITY/MAINTENANCE",
                            "description": "descrizione del problema tecnico",
                            "severity": "HIGH/MEDIUM/LOW",
                            "technicalSuggestion": "suggerimento tecnico di miglioramento"
                        }
                    ],
                    "technicalRecommendations": ["raccomandazione tecnica 1", "raccomandazione tecnica 2"]
                }
                """.formatted(ticketAnalysis, generatedTests);
            
            Prompt prompt = new Prompt(validationPrompt);
            String validation = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.debug("Detailed validation completed successfully");
            return validation;
            
        } catch (Exception e) {
            logger.error("Error during detailed test validation", e);
            throw new RuntimeException("Failed to perform detailed test validation", e);
        }
    }
} 