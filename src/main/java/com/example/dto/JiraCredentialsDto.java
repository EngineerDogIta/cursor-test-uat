package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JiraCredentialsDto {
    @NotBlank(message = "L'URL del server Jira è obbligatorio")
    private String serverUrl;
    
    @NotBlank(message = "Il nome utente è obbligatorio")
    private String username;
    
    @NotBlank(message = "Il token API è obbligatorio")
    private String apiToken;
} 