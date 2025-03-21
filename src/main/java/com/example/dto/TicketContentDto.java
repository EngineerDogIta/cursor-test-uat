package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class TicketContentDto {

    @NotBlank(message = "Il contenuto del ticket non può essere vuoto")
    private String content;

    @NotBlank(message = "L'ID del ticket non può essere vuoto")
    private String ticketId;

    @NotEmpty(message = "La lista dei componenti non può essere vuota")
    private List<String> components = new ArrayList<>();

    // Costruttore senza argomenti per Spring
    public TicketContentDto() {
    }

    // Costruttore privato per il Builder
    private TicketContentDto(Builder builder) {
        this.content = builder.content;
        this.ticketId = builder.ticketId;
        this.components = builder.components;
    }

    // Getter e setter per il data binding
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

    public static class Builder {
        private String content;
        private String ticketId;
        private List<String> components = new ArrayList<>();

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public Builder setTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public Builder setComponents(List<String> components) {
            this.components = components;
            return this;
        }

        public TicketContentDto build() {
            // Possibili validazioni possono essere inserite qui se necessario
            return new TicketContentDto(this);
        }
    }
}