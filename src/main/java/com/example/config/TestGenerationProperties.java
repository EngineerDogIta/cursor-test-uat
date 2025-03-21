package com.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "test.generation")
@Getter
@Setter
public class TestGenerationProperties {
    private int maxAttempts = 3;
    private List<String> acceptableQualityLevels = List.of("QUALITY_HIGH", "QUALITY_MEDIUM");
    private Validation validation = new Validation();
    private Prompts prompts = new Prompts();

    @Getter
    @Setter
    public static class Validation {
        private String basePrompt;
        private String detailedPrompt;
    }

    @Getter
    @Setter
    public static class Prompts {
        private String system;
        private String validation;
    }
} 