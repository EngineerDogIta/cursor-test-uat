package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class TicketContentDto {
    
    @NotBlank(message = "Il contenuto del ticket non può essere vuoto")
    private String content;
    
    @NotBlank(message = "L'ID del ticket non può essere vuoto")
    private String ticketId;
    
    @NotEmpty(message = "La lista dei componenti non può essere vuota")
    private List<String> components;

    public TicketContentDto() {
    }

    public TicketContentDto(String content, String ticketId, List<String> components) {
        this.content = content;
        this.ticketId = ticketId;
        this.components = components;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }
} 