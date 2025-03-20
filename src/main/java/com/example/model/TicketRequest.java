package com.example.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketRequest {
    @NotBlank(message = "Il ticket Jira è obbligatorio")
    private String jiraTicket;

    @NotBlank(message = "La descrizione è obbligatoria")
    private String description;

    private String components;
} 