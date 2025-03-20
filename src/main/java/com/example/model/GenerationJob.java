package com.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "generation_job", indexes = {
    @Index(name = "idx_generation_job_uid", columnList = "uid")
})
public class GenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uid = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "ticket_content_id")
    private TicketContent ticketContent;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "generated_test_id")
    private GeneratedTest generatedTest;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}