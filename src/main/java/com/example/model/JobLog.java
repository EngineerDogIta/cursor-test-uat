package com.example.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class JobLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private TestGenerationJob job;
    
    @Column(nullable = false)
    private String level;
    
    @Column(nullable = false, length = 1000)
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
} 