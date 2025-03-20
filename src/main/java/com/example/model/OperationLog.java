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
@Table(name = "operation_log", indexes = {
    @Index(name = "idx_operation_log_uid", columnList = "uid"),
    @Index(name = "idx_operation_log_job_uid", columnList = "job_uid")
})
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uid = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "job_uid", nullable = false)
    private String jobUid;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
} 