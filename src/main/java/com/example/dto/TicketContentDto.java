package com.example.dto;

public class TicketContentDto {
    private String content;
    private String ticketId;
    private String project;

    // Costruttore vuoto
    public TicketContentDto() {
    }

    // Costruttore con parametri
    public TicketContentDto(String content, String ticketId, String project) {
        this.content = content;
        this.ticketId = ticketId;
        this.project = project;
    }

    // Getter e Setter
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

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }
} 