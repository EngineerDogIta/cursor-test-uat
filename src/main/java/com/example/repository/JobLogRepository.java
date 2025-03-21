package com.example.repository;

import com.example.model.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    List<JobLog> findByJobIdOrderByTimestampDesc(Long jobId);
    List<JobLog> findByJobIdAndLevelOrderByTimestampDesc(Long jobId, String level);
} 