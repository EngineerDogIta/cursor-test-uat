package com.example.controller;

import com.example.dto.JobLogDto;
import com.example.dto.JobStatusDto;
import com.example.dto.JobTestResultDto;
import com.example.dto.TicketContentDto;
import com.example.exception.JobNotFoundException;
import com.example.model.TestGenerationJob;
import com.example.model.JobLog;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
import com.example.service.TestGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TestGenerationControllerTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private TestGenerationService testGenerationService;

    @Mock
    private TestGenerationJobRepository testGenerationRepository;

    @Mock
    private JobLogRepository jobLogRepository;

    @InjectMocks
    private TestGenerationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void startTestGeneration_ShouldReturnAccepted() {
        // Arrange
        TicketContentDto ticketDto = new TicketContentDto();
        ticketDto.setTicketId("TICKET-001");
        ticketDto.setContent("Test ticket content");
        ticketDto.setComponents(new ArrayList<>());

        doNothing().when(testGenerationService).startTestGeneration(any(TicketContentDto.class));

        // Act
        ResponseEntity<String> response = controller.startTestGeneration(ticketDto);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(ticketDto.getTicketId()));

        verify(testGenerationService).startTestGeneration(eq(ticketDto));
    }

    @Test
    void getJobStatus_ShouldReturnStatusMap() {
        // Arrange
        String jobId = "123";
        Map<String, Object> serviceResponseMap = Map.of(
            "status", "COMPLETED",
            "error", ""
        );
        when(testGenerationService.getJobStatus(jobId)).thenReturn(serviceResponseMap);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getJobStatus(jobId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<String, Object> responseMap = response.getBody();
        assertEquals("COMPLETED", responseMap.get("status"));
        assertEquals("", responseMap.get("error"));
        verify(testGenerationService).getJobStatus(jobId);
    }

    @Test
    void getJobStatus_WithInvalidJobId_ShouldThrowJobNotFoundException() {
        // Arrange
        String jobId = "invalid-job-id";
        String errorMessage = "Job non trovato";
        when(testGenerationService.getJobStatus(jobId)).thenThrow(new JobNotFoundException(errorMessage));

        JobNotFoundException thrown = assertThrows(
            JobNotFoundException.class,
            () -> controller.getJobStatus(jobId),
            "Expected getJobStatus to throw, but it didn't"
        );

        assertTrue(thrown.getMessage().contains(errorMessage));
        verify(testGenerationService).getJobStatus(jobId);
    }

    @Test
    void getJobLogs_ShouldReturnLogs() {
        // Arrange
        String jobId = "123";
        Long jobIdLong = 123L;
        List<JobLog> logs = Arrays.asList(
            createJobLog("INFO", "Test log 1"),
            createJobLog("ERROR", "Test log 2")
        );
        
        when(jobLogRepository.findByJobIdOrderByTimestampDesc(jobIdLong)).thenReturn(logs);

        // Act
        var response = controller.getJobLogs(jobId);

        // Assert
        assertNotNull(response.getBody());
        List<JobLogDto> logDtos = response.getBody();
        assertEquals(2, logDtos.size());
        assertEquals("INFO", logDtos.get(0).getLevel());
        assertEquals("Test log 1", logDtos.get(0).getMessage());
        assertEquals("ERROR", logDtos.get(1).getLevel());
        assertEquals("Test log 2", logDtos.get(1).getMessage());
        verify(jobLogRepository).findByJobIdOrderByTimestampDesc(jobIdLong);
    }

    @Test
    void getJobLogs_WithInvalidJobId_ShouldReturnBadRequest() {
        // Arrange
        String jobId = "invalid-id";

        // Act
        var response = controller.getJobLogs(jobId);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verify(jobLogRepository, never()).findByJobIdOrderByTimestampDesc(any());
    }

    @Test
    void getJobTestResult_ShouldReturnTestResult() {
        // Arrange
        String jobId = "123";
        Long jobIdLong = 123L;
        TestGenerationJob job = new TestGenerationJob();
        job.setTestResult("Test result content");
        
        when(testGenerationRepository.findById(jobIdLong)).thenReturn(java.util.Optional.of(job));

        // Act
        var response = controller.getJobTestResult(jobId);

        // Assert
        assertNotNull(response.getBody());
        JobTestResultDto resultDto = response.getBody();
        assertEquals("Test result content", resultDto.getTestResult());
        verify(testGenerationRepository).findById(jobIdLong);
    }

    @Test
    void getJobTestResult_WithInvalidJobId_ShouldReturnBadRequest() {
        // Arrange
        String jobId = "invalid-id";

        // Act
        var response = controller.getJobTestResult(jobId);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verify(testGenerationRepository, never()).findById(any());
    }

    @Test
    void getJobTestResult_WithNonExistentJob_ShouldReturnNotFound() {
        // Arrange
        String jobId = "123";
        Long jobIdLong = 123L;
        when(testGenerationRepository.findById(jobIdLong)).thenReturn(java.util.Optional.empty());

        // Act
        var response = controller.getJobTestResult(jobId);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        verify(testGenerationRepository).findById(jobIdLong);
    }

    private JobLog createJobLog(String level, String message) {
        JobLog log = new JobLog();
        log.setLevel(level);
        log.setMessage(message);
        log.setTimestamp(LocalDateTime.now());
        return log;
    }
} 