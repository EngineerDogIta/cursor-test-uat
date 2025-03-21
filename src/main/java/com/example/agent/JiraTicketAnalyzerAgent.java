package com.example.agent;

import com.example.dto.TicketContentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JiraTicketAnalyzerAgent {
    private static final Logger logger = LoggerFactory.getLogger(JiraTicketAnalyzerAgent.class);
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        You are a Jira ticket analyzer specialized in UAT test generation.
        
        Your task is to analyze the provided Jira ticket and provide a structured analysis following this format:
        
        # Ticket Analysis [ID]
        
        ## Summary
        Provide a clear, concise summary of the ticket's purpose and main requirements.
        
        ## Classification
        Type: [CR/BUGFIX]
        Completeness: [HIGH/MEDIUM/LOW]
        Priority: [HIGH/MEDIUM/LOW]
        
        ## Components
        List all affected components and their roles:
        * [Component 1]: [Role/Purpose]
        * [Component 2]: [Role/Purpose]
        
        ## Technical Impacts
        Describe the technical implications:
        * [Impact 1]: [Description]
        * [Impact 2]: [Description]
        
        ## Technical Analysis
        Provide a brief technical evaluation including:
        * Architecture considerations
        * Potential risks
        * Dependencies
        * Performance implications
        
        ## Test Considerations
        Highlight key aspects to consider for UAT testing:
        * Critical functionalities
        * Edge cases
        * Integration points
        * Performance requirements
        """;

    @Autowired
    public JiraTicketAnalyzerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    public String analyzeTicket(TicketContentDto ticketDto) {
        logger.info("Iniziando analisi del ticket Jira: {}", ticketDto.getTicketId());
        logger.debug("Dettagli del ticket - ID: {}, Componenti: {}, Lunghezza contenuto: {} caratteri", 
            ticketDto.getTicketId(), 
            String.join(", ", ticketDto.getComponents()),
            ticketDto.getContent().length());
        
        try {
            String userPrompt = """
                Analyze this Jira ticket in detail.
                
                Ticket ID: %s
                Components: %s
                Content: %s
                """.formatted(
                    ticketDto.getTicketId(),
                    String.join(", ", ticketDto.getComponents()),
                    ticketDto.getContent()
                );
            
            logger.debug("Prompt generato per l'analisi: {}", userPrompt);
            Prompt prompt = new Prompt(SYSTEM_PROMPT + "\n\n" + userPrompt);
            
            logger.debug("Inviando richiesta al modello di analisi...");
            String analysis = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.info("Analisi completata per il ticket: {}", ticketDto.getTicketId());
            logger.debug("Risultato dell'analisi - Lunghezza: {} caratteri, Prima riga: {}", 
                analysis.length(),
                analysis.split("\n")[0]);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Errore durante l'analisi del ticket Jira: {} - {}", 
                ticketDto.getTicketId(), 
                e.getMessage(), 
                e);
            throw new RuntimeException("Impossibile analizzare il ticket Jira: " + e.getMessage(), e);
        }
    }
} 