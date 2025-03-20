package com.example.controller;

import com.example.dto.TicketContentDto;
import com.example.service.TestGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class TestGenerationControllerTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private TestGenerationService testGenerationService;

    private TestGenerationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TestGenerationController(testGenerationService);
    }

    @Test
    void startTestGeneration_ShouldReturnJobId() {
        // Arrange
        TicketContentDto ticketDto = new TicketContentDto(
            "Test ticket content",
            "TICKET-123",
            "TestProject"
        );

        // Act
        var response = controller.startTestGeneration(ticketDto);

        // Assert
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("jobId"));
        assertTrue(response.getBody().containsKey("status"));
        assertEquals("STARTED", response.getBody().get("status"));
        
        verify(testGenerationService).startTestGeneration(anyString(), eq(ticketDto));
    }

    @Test
    void getTestGenerationStatus_ShouldReturnStatus() {
        // Arrange
        String jobId = "test-job-id";
        Map<String, Object> expectedStatus = Map.of(
            "status", "COMPLETED",
            "result", "Test result",
            "error", ""
        );
        
        when(testGenerationService.getJobStatus(jobId)).thenReturn(expectedStatus);

        // Act
        var response = controller.getTestGenerationStatus(jobId);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(expectedStatus, response.getBody());
        verify(testGenerationService).getJobStatus(jobId);
    }
} 