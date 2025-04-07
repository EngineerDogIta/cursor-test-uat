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

    private static final String UAT_SYSTEM_PROMPT = """
    You are a UAT test case generator.
    Analyze the provided Ticket Description below.
    Generate a numbered list of UAT test cases based ONLY on the information present in the description.
    Each test case MUST strictly follow this format:

    ID: [Test ID, e.g., UAT-001]
    Title: [Concise test title reflecting a requirement in the description]
    Steps:
    1. [Simple step 1, from an end-user perspective, e.g., "Navigate to the login page."]
    2. [Simple step 2, from an end-user perspective, e.g., "Enter valid username and password."]
    ...
    Result: [Expected outcome based on the description, e.g., "User is successfully logged in and redirected to the dashboard."]

    Constraints:
    - DO NOT invent scenarios or test functionality not explicitly mentioned in the description.
    - Keep the steps simple and focused on user actions.
    - Adhere strictly to the output format provided above for each test case.
    - If the description is unclear or lacks detail for a specific test, state that clearly in the test result or omit the test case if it cannot be reasonably derived.
    - Provide only the formatted test cases, nothing else.
    """;

    @Autowired
    public TestGeneratorAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Generates UAT test cases based directly on the provided ticket content.
     *
     * @param ticketContent The raw description or content of the ticket.
     * @return A string containing the formatted UAT test cases, or an error message.
     */
    public String generateTests(String ticketContent) {
        logger.info("Starting UAT test generation for ticket content.");
        logger.debug("Input ticket content length: {} characters", ticketContent != null ? ticketContent.length() : 0);

        if (ticketContent == null || ticketContent.trim().isEmpty()) {
            logger.warn("Ticket content is null or empty. Cannot generate tests.");
            return "Error: Ticket content provided is empty or null.";
        }

        try {
            String userPrompt = "Ticket Description:\n" + ticketContent;
            Prompt prompt = new Prompt(UAT_SYSTEM_PROMPT + "\n\n" + userPrompt);
            logger.debug("Constructed prompt for LLM. System prompt length: {}, User prompt length: {}",
                         UAT_SYSTEM_PROMPT.length(), userPrompt.length());

            String result = chatClient.call(prompt).getResult().getOutput().getContent();

            logger.info("UAT test generation completed successfully.");
            logger.debug("Generated UAT tests length: {} characters", result != null ? result.length() : 0);
            return result;

        } catch (Exception e) {
            logger.error("Error during UAT test generation: {}", e.getMessage(), e);
            return "Error: Failed to generate tests due to an internal error. Check logs for details.";
        }
    }
} 