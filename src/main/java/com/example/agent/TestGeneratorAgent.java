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
        Generate UAT test cases from ticket analysis.
        
        For each important scenario create:
        - Positive test
        - Boundary test
        - Negative test
        
        Format:
        
        ## SCENARIO: [ID] - [Title]
        **Type:** [POSITIVE/NEGATIVE/BOUNDARY]
        
        **Preconditions:**
        * [list key preconditions]
        
        **Steps:**
        1. [step] - Expected: [result]
        2. [step] - Expected: [result]
        
        **Test Data:** [key test data]
        **Dependencies:** [if any]
        **Automation:** [validation approach if possible]
        """;

    @Autowired
    public TestGeneratorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generateTests(String ticketAnalysis) {
        return generateTests(ticketAnalysis, SYSTEM_PROMPT);
    }

    public String generateTests(String ticketAnalysis, String enhancedPrompt) {
        logger.info("Starting test generation");
        logger.debug("Using ticket analysis: {}", ticketAnalysis);
        
        try {
            Prompt prompt = new Prompt(enhancedPrompt + "\n\nAnalisi del ticket:\n" + ticketAnalysis);
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