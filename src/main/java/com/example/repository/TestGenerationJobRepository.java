package com.example.repository;

import com.example.model.TestGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestGenerationJobRepository extends JpaRepository<TestGenerationJob, Long> {
    List<TestGenerationJob> findByStatusIn(List<TestGenerationJob.JobStatus> statuses);
    List<TestGenerationJob> findByJiraTicket(String jiraTicket);
} 