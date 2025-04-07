package com.example.repository;

import com.example.model.TestGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestGenerationJobRepository extends JpaRepository<TestGenerationJob, Long> {
} 