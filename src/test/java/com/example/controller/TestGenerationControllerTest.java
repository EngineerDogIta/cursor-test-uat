package com.example.controller;

import com.example.dto.*;
import com.example.model.TestGenerationJob;
import com.example.model.JobLog;
import com.example.repository.TestGenerationJobRepository;
import com.example.repository.JobLogRepository;
import com.example.service.TestGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    private TestGenerationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TestGenerationController(testGenerationService, testGenerationRepository, jobLogRepository);
    }

    @Test
    void startTestGeneration_ShouldReturnJobId() {
        // Arrange
        TicketContentDto ticketDto = new TicketContentDto();
        ticketDto.setContent("Test ticket content");
        ticketDto.setTicketId("TEST-123");

        // Act
        var response = controller.startTestGeneration(ticketDto);

        // Assert
        assertNotNull(response.getBody());
        TestGenerationResponseDto responseDto = response.getBody();
        assertNotNull(responseDto.getJobId());
        assertEquals("Generazione dei test avviata con successo", responseDto.getMessage());
        assertNull(responseDto.getError());
        
        verify(testGenerationService).startTestGeneration(eq(ticketDto));
    }

    @Test
    void getJobStatus_ShouldReturnStatus() {
        // Arrange
        String jobId = "123";
        Map<String, Object> expectedStatus = Map.of(
            "status", "COMPLETED",
            "error", ""
        );
        
        when(testGenerationService.getJobStatus(jobId)).thenReturn(expectedStatus);

        // Act
        var response = controller.getJobStatus(jobId);

        // Assert
        assertNotNull(response.getBody());
        JobStatusDto statusDto = response.getBody();
        assertEquals("COMPLETED", statusDto.getStatus());
        assertEquals("", statusDto.getError());
        verify(testGenerationService).getJobStatus(jobId);
    }

    @Test
    void getJobStatus_WithInvalidJobId_ShouldReturnNotFound() {
        // Arrange
        String jobId = "invalid-job-id";
        when(testGenerationService.getJobStatus(jobId)).thenReturn(null);

        // Act
        var response = controller.getJobStatus(jobId);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        JobStatusDto statusDto = response.getBody();
        assertEquals("NOT_FOUND", statusDto.getStatus());
        assertEquals("Job non trovato", statusDto.getError());
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