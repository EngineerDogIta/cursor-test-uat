package com.example.services;

import com.example.model.JobLog;
import com.example.model.TestGenerationJob;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLogService {
    private static final Logger logger = LoggerFactory.getLogger(JobLogService.class);
    private final TestGenerationJobRepository testGenerationJobRepository;
    private final JobLogRepository jobLogRepository;

    public JobLogService(TestGenerationJobRepository testGenerationJobRepository, JobLogRepository jobLogRepository) {
        this.testGenerationJobRepository = testGenerationJobRepository;
        this.jobLogRepository = jobLogRepository;
    }

    @Transactional
    public void addJobLog(TestGenerationJob job, String level, String message) {
        if (job == null) {
            logger.info("[NO_JOB] {} - {}", level, message);
            return;
        }
        try {
            job = testGenerationJobRepository.findById(job.getId()).orElseThrow();
            JobLog log = new JobLog();
            log.setJob(job);
            log.setLevel(level);
            log.setMessage(message.length() > 150 ? message.substring(0, 150) + "..." : message);
            log.setTimestamp(java.time.LocalDateTime.now());
            jobLogRepository.save(log);
            logger.debug("Log added successfully for job {}: {} - {}", job.getId(), level, log.getMessage());
        } catch (Exception e) {
            logger.error("Error during adding log for job {}: {}", job.getId(), e.getMessage());
            logger.info("[JOB_{}] {} - {}", job.getId(), level, message);
        }
    }
} 