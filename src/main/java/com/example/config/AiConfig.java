package com.example.config;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient() {
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
        return new OllamaChatClient(ollamaApi);
    }
} 