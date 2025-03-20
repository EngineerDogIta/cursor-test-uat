package com.example.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestGeneratorAgent {
    private static final Logger logger = LoggerFactory.getLogger(TestGeneratorAgent.class);
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        Sei un agente specializzato nella generazione di casi di test UAT.
        Il tuo compito è generare scenari di test dettagliati basati sull'analisi del ticket.
        
        Per ogni scenario, genera:
        1. Scenari positivi
        2. Scenari limite
        3. Scenari negativi
        
        Ogni scenario deve includere:
        - Precondizioni
        - Steps dettagliati
        - Risultati attesi
        - Dati di test
        - Dipendenze
        - Validazione automatizzata (se possibile)
        
        Fornisci l'output in formato JSON con la seguente struttura:
        {
            "scenarios": [
                {
                    "id": "SCENARIO_ID",
                    "title": "Titolo dello scenario",
                    "type": "POSITIVE/NEGATIVE/BOUNDARY",
                    "preconditions": ["precondizione1", "precondizione2"],
                    "steps": [
                        {
                            "stepNumber": 1,
                            "description": "descrizione dello step",
                            "expectedResult": "risultato atteso"
                        }
                    ],
                    "expectedResults": ["risultato1", "risultato2"],
                    "testData": ["dato1", "dato2"],
                    "dependencies": ["dipendenza1", "dipendenza2"],
                    "automatedValidation": "descrizione della validazione"
                }
            ]
        }
        """;

    @Autowired
    public TestGeneratorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generateTests(String ticketAnalysis) {
        logger.info("Starting test generation");
        logger.debug("Using ticket analysis: {}", ticketAnalysis);
        
        try {
            Prompt prompt = new Prompt(SYSTEM_PROMPT + "\n\nAnalisi del ticket:\n" + ticketAnalysis);
            String result = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.info("Test generation completed successfully");
            logger.debug("Generated tests: {}", result);
            return result;
            
        } catch (Exception e) {
            logger.error("Error during test generation", e);
            throw e;
        }
    }
} 