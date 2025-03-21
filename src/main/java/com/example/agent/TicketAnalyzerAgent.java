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
        Analyze Jira tickets for UAT test generation.
        
        Provide:
        1. Ticket classification (CR or bugfix)
        2. Information completeness assessment
        3. Involved components
        4. Technical impacts
        5. Brief technical assessment
        
        Format:
        
        # Ticket Analysis [ID]
        
        ## Classification
        Type: [CR/BUGFIX]
        Completeness: [HIGH/MEDIUM/LOW]
        
        ## Components
        * [list key components]
        
        ## Impacts
        * [list key impacts]
        
        ## Analysis
        [brief technical assessment]
        """;

    @Autowired
    public TicketAnalyzerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String analyzeTicket(TicketContentDto ticketDto) {
        logger.info("Starting ticket analysis for ticket: {} with components: {}", ticketDto.getTicketId(), String.join(", ", ticketDto.getComponents()));
        logger.debug("Analyzing ticket content: {}", ticketDto.getContent());
        
        try {
            // Prima analisi base
            String ticketContext = String.format("""
                Ticket ID: %s
                Components: %s
                Content: %s
                """, ticketDto.getTicketId(), String.join(", ", ticketDto.getComponents()), ticketDto.getContent());
                
            Prompt basePrompt = new Prompt(SYSTEM_PROMPT + "\n\nTicket da analizzare:\n" + ticketContext);
            String baseAnalysis = chatClient.call(basePrompt).getResult().getOutput().getContent();
            
            // Analisi dettagliata
            String detailedAnalysis = performAnalysis(ticketDto);
            
            // Combiniamo i risultati
            String combinedAnalysis = String.format("""
                {
                    "ticketId": "%s",
                    "components": ["%s"],
                    "baseAnalysis": %s,
                    "detailedAnalysis": %s
                }
                """, ticketDto.getTicketId(), String.join("\", \"", ticketDto.getComponents()), baseAnalysis, detailedAnalysis);
            
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
                Analyze Jira ticket technically.
                
                Assess: complexity, dependencies, risks, performance, security.
                
                Ticket ID: %s
                Components: %s
                Content: %s
                
                Format:
                
                # Technical Assessment
                
                ## Complexity: [HIGH/MEDIUM/LOW]
                
                ## Dependencies
                * [list key dependencies]
                
                ## Risks
                * [list key risks]
                
                ## Performance: [HIGH/MEDIUM/LOW]
                
                ## Security
                * [list key considerations]
                
                ## Analysis
                [brief technical details]
                """.formatted(ticketDto.getTicketId(), String.join(", ", ticketDto.getComponents()), ticketDto.getContent());
            
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