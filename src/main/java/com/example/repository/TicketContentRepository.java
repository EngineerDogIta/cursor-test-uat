package com.example.repository;

import com.example.model.TicketContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketContentRepository extends JpaRepository<TicketContent, Long> {
} 