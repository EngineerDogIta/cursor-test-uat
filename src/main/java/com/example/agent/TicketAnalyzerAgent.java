package com.example.agent;

import com.example.dto.TicketContentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TicketAnalyzerAgent {
    private static final Logger logger = LoggerFactory.getLogger(TicketAnalyzerAgent.class);
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        Sei un agente specializzato nell'analisi di ticket Jira per la generazione di test UAT.
        Il tuo compito è:
        1. Classificare il ticket (CR o bugfix)
        2. Valutare completezza e chiarezza delle informazioni
        3. Identificare i componenti coinvolti (GUI Front-End e API Back-End)
        4. Determinare origine anomalie e impatti tecnici
        5. Fornire una valutazione tecnica dettagliata
        
        Fornisci l'output in formato JSON con la seguente struttura:
        {
            "ticketType": "CR/BUGFIX",
            "completeness": "HIGH/MEDIUM/LOW",
            "components": ["component1", "component2"],
            "technicalImpacts": ["impact1", "impact2"],
            "analysis": "descrizione dettagliata"
        }
        """;

    @Autowired
    public TicketAnalyzerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String analyzeTicket(TicketContentDto ticketDto) {
        logger.info("Starting ticket analysis for ticket: {} in project: {}", ticketDto.getTicketId(), ticketDto.getProject());
        logger.debug("Analyzing ticket content: {}", ticketDto.getContent());
        
        try {
            // Prima analisi base
            String ticketContext = String.format("""
                Ticket ID: %s
                Project: %s
                Content: %s
                """, ticketDto.getTicketId(), ticketDto.getProject(), ticketDto.getContent());
                
            Prompt basePrompt = new Prompt(SYSTEM_PROMPT + "\n\nTicket da analizzare:\n" + ticketContext);
            String baseAnalysis = chatClient.call(basePrompt).getResult().getOutput().getContent();
            
            // Analisi dettagliata
            String detailedAnalysis = performAnalysis(ticketDto);
            
            // Combiniamo i risultati
            String combinedAnalysis = String.format("""
                {
                    "ticketId": "%s",
                    "project": "%s",
                    "baseAnalysis": %s,
                    "detailedAnalysis": %s
                }
                """, ticketDto.getTicketId(), ticketDto.getProject(), baseAnalysis, detailedAnalysis);
            
            logger.info("Ticket analysis completed successfully");
            logger.debug("Combined analysis result: {}", combinedAnalysis);
            return combinedAnalysis;
            
        } catch (Exception e) {
            logger.error("Error during ticket analysis", e);
            throw e;
        }
    }
    
    private String performAnalysis(TicketContentDto ticketDto) {
        logger.debug("Performing detailed ticket analysis");
        
        try {
            String analysisPrompt = """
                Analizza in dettaglio il seguente ticket Jira e fornisci una valutazione tecnica approfondita.
                Considera:
                1. Complessità tecnica
                2. Dipendenze da altri componenti
                3. Rischi potenziali
                4. Impatto sulle performance
                5. Considerazioni sulla sicurezza
                
                Ticket ID: %s
                Project: %s
                Content: %s
                
                Fornisci l'output in formato JSON con la seguente struttura:
                {
                    "technicalComplexity": "HIGH/MEDIUM/LOW",
                    "dependencies": ["dep1", "dep2"],
                    "risks": ["risk1", "risk2"],
                    "performanceImpact": "HIGH/MEDIUM/LOW",
                    "securityConsiderations": ["consideration1", "consideration2"],
                    "detailedAnalysis": "descrizione dettagliata"
                }
                """.formatted(ticketDto.getTicketId(), ticketDto.getProject(), ticketDto.getContent());
            
            Prompt prompt = new Prompt(analysisPrompt);
            String analysis = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.debug("Detailed analysis completed successfully");
            return analysis;
            
        } catch (Exception e) {
            logger.error("Error during detailed ticket analysis", e);
            throw new RuntimeException("Failed to perform detailed ticket analysis", e);
        }
    }
} 