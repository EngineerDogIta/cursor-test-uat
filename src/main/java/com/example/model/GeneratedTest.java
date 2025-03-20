package com.example.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "generated_test", indexes = {
    @Index(name = "idx_generated_test_uid", columnList = "uid")
})
public class GeneratedTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uid = UUID.randomUUID().toString();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String testContent;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean validated;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 