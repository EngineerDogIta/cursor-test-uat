package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketContentDto {
    
    @NotBlank(message = "Il contenuto del ticket non può essere vuoto")
    private String content;
    
    @NotBlank(message = "L'ID del ticket non può essere vuoto")
    private String ticketId;
    
    @NotEmpty(message = "La lista dei componenti non può essere vuota")
    private List<String> components;

}