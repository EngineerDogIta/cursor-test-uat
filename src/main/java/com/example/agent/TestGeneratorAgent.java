package com.example.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
public class TestGeneratorAgent {
    private static final Logger logger = LoggerFactory.getLogger(TestGeneratorAgent.class);
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        You must generate a complete UAT (User Acceptance Testing) document for the provided ticket with these sections.
        
        IMPORTANT: Use the ticket information provided in the Required Input section below to populate the document.
        If any information is missing in the Required Input, use the default values specified in the template.
        
        Ticket Information:
        Ticket ID: [insert ID or "Not provided"]
        Description: [insert concise description or "Not provided"]
        Type: [Bug/Feature/Enhancement or "Not provided"]
        Impacted Components: [list of components separated by commas or "Not provided"]
        
        Test Objectives:
        Provide a brief description (3-5 sentences) of the main testing objectives from an end-user perspective. Even if details are sparse, assume plausible objectives.
        
        Test Cases:
        Generate at least one detailed test case following the structure below. If ticket details are incomplete, use default values such as "Not provided" or "Standard setup".
        
        ID: [Unique test case identifier]
        Title: [Concise title describing the test objective]
        Prerequisites: [Prerequisites or environment setup; use "Standard setup" if not provided]
        Steps:
        [First step to perform]
        [Second step to perform]
        [Additional steps as needed]
        Expected Result: [Outcome expected after executing the steps]
        Test Data: [Input data required for the test, or "Not provided" if none]
        
        IMPORTANT: Do NOT include any additional instructions or generic advice. The final output must solely adhere to this structure.
        """;

    @Autowired
    public TestGeneratorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    public String generateTests(String ticketAnalysis, String enhancedPrompt) {
        String promptToUse = SYSTEM_PROMPT + (enhancedPrompt != null && !enhancedPrompt.isEmpty() ? "\n\n" + enhancedPrompt : "");
        logger.info("Starting test generation for ticket analysis");
        logger.debug("Input ticket analysis length: {} characters", ticketAnalysis.length());
        logger.debug("Enhanced prompt length: {} characters", enhancedPrompt != null ? enhancedPrompt.length() : 0);
        
        try {
            // Extract ticket information from analysis
            Map<String, String> ticketInfo = extractTicketInfo(ticketAnalysis);
            logger.debug("Extracted ticket information: {}", ticketInfo);
            
            String ticketId = ticketInfo.getOrDefault("id", "UNKNOWN");
            String ticketDescription = ticketInfo.getOrDefault("description", "No description available");
            String ticketType = ticketInfo.getOrDefault("type", "Feature");
            String components = ticketInfo.getOrDefault("components", "Unknown");
            
            logger.debug("Preparing prompt with ticket details - ID: {}, Type: {}, Components: {}", 
                ticketId, ticketType, components);
            
            String userPrompt = String.format("""
                Required Input:
                Ticket ID: %s
                Description: %s
                Type: %s
                Impacted Components: %s
                """, ticketId, ticketDescription, ticketType, components);
            
            Prompt prompt = new Prompt(promptToUse + "\n\n" + userPrompt);
            logger.debug("Generated complete prompt with length: {} characters", prompt.getContents().length());
            
            String result = chatClient.call(prompt).getResult().getOutput().getContent();
            
            logger.info("Test generation completed successfully");
            logger.debug("Generated test document length: {} characters", result.length());
            return result;
            
        } catch (Exception e) {
            logger.error("Error during test generation: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private Map<String, String> extractTicketInfo(String ticketAnalysis) {
        Map<String, String> info = new HashMap<>();
        
        // Default values
        info.put("id", "UNKNOWN");
        info.put("description", "No description available");
        info.put("type", "Feature");
        info.put("components", "Unknown");
        
        try {
            logger.debug("Starting ticket info extraction from analysis");
            // Analizziamo riga per riga per estrarre le informazioni
            String[] lines = ticketAnalysis.split("\n");
            logger.debug("Found {} lines to analyze", lines.length);
            
            for (String line : lines) {
                line = line.trim();
                logger.debug("Analyzing line: {}", line);
                
                if (line.startsWith("Ticket ID:") || line.startsWith("ID:")) {
                    info.put("id", line.substring(line.indexOf(":") + 1).trim());
                    logger.debug("Extracted ticket ID: {}", info.get("id"));
                } else if (line.startsWith("Description:") || line.startsWith("Summary:")) {
                    info.put("description", line.substring(line.indexOf(":") + 1).trim());
                    logger.debug("Extracted description: {}", info.get("description"));
                } else if (line.startsWith("Type:")) {
                    info.put("type", line.substring(line.indexOf(":") + 1).trim());
                    logger.debug("Extracted type: {}", info.get("type"));
                } else if (line.startsWith("Components:")) {
                    info.put("components", line.substring(line.indexOf(":") + 1).trim());
                    logger.debug("Extracted components: {}", info.get("components"));
                }
            }
            logger.debug("Completed ticket info extraction. Final map: {}", info);
        } catch (Exception e) {
            logger.warn("Error extracting ticket info from analysis: {}", e.getMessage(), e);
            // Continuiamo con i valori predefiniti
        }
        
        return info;
    }
} 