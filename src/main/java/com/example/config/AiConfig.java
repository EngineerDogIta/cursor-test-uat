package com.example.config;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Spring AI components.
 */
@Configuration
public class AiConfig {

    private final String ollamaBaseUrl;

    @Autowired
    public AiConfig(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    /**
     * Creates the OllamaApi bean.
     * @return OllamaApi instance.
     */
    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi(ollamaBaseUrl);
    }

    /**
     * Creates the ChatClient bean using the OllamaApi.
     * @param ollamaApi The OllamaApi bean.
     * @return OllamaChatClient instance.
     */
    @Bean
    public ChatClient chatClient(OllamaApi ollamaApi) {
        return new OllamaChatClient(ollamaApi);
    }
} 