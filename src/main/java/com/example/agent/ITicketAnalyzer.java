package com.example.agent;

import com.example.dto.TicketContentDto;

public interface ITicketAnalyzer {
    String analyzeTicket(TicketContentDto ticketDto);
} 