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
        Analizza in modo dettagliato un ticket Jira per la generazione di test UAT.
        
        Fornisci:
        1. Una sintesi chiara e concisa del ticket
        2. La classificazione del ticket (CR o bugfix)
        3. Valutazione della completezza delle informazioni
        4. I componenti coinvolti
        5. Gli impatti tecnici
        6. Una breve valutazione tecnica
        
        Formato:
        
        # Analisi Ticket [ID]
        
        ## Sintesi
        [sintesi breve e chiara del ticket]
        
        ## Classificazione
        Tipo: [CR/BUGFIX]
        Completezza: [ALTA/MEDIA/BASSA]
        
        ## Componenti
        * [elenco componenti chiave]
        
        ## Impatti
        * [elenco impatti chiave]
        
        ## Analisi Tecnica
        [breve valutazione tecnica]
        """;

    @Autowired
    public JiraTicketAnalyzerAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    public String analyzeTicket(TicketContentDto ticketDto) {
        logger.info("Analisi del ticket Jira: {}", ticketDto.getTicketId());
        
        try {
            String userPrompt = """
                Analizza questo ticket Jira in modo dettagliato.
                
                ID Ticket: %s
                Componenti: %s
                Contenuto: %s
                """.formatted(
                    ticketDto.getTicketId(),
                    String.join(", ", ticketDto.getComponents()),
                    ticketDto.getContent()
                );
            
            Prompt prompt = new Prompt(userPrompt);
            String analysis = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.info("Analisi completata per il ticket: {}", ticketDto.getTicketId());
            logger.debug("Risultato analisi: {}", analysis);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Errore durante l'analisi del ticket Jira", e);
            throw new RuntimeException("Impossibile analizzare il ticket Jira: " + e.getMessage(), e);
        }
    }
} 