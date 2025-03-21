package com.example.controller;

import com.example.dto.TicketContentDto;
import com.example.dto.TestGenerationResponseDto;
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

    @Test
    void startTestGeneration_WithEmptyContent_ShouldReturnBadRequest() {
        // Arrange
        TicketContentDto ticketDto = new TicketContentDto();
        ticketDto.setTicketId("TEST-123");

        // Act
        var response = controller.startTestGeneration(ticketDto);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        TestGenerationResponseDto responseDto = response.getBody();
        assertNotNull(responseDto);
        assertEquals("Il contenuto del ticket non può essere vuoto", responseDto.getError());
        assertNull(responseDto.getJobId());
        assertNull(responseDto.getMessage());
        verify(testGenerationService, never()).startTestGeneration(any());
    }

    @Test
    void startTestGeneration_WithEmptyTicketId_ShouldReturnBadRequest() {
        // Arrange
        TicketContentDto ticketDto = new TicketContentDto();
        ticketDto.setContent("Test ticket content");

        // Act
        var response = controller.startTestGeneration(ticketDto);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        TestGenerationResponseDto responseDto = response.getBody();
        assertNotNull(responseDto);
        assertEquals("L'ID del ticket non può essere vuoto", responseDto.getError());
        assertNull(responseDto.getJobId());
        assertNull(responseDto.getMessage());
        verify(testGenerationService, never()).startTestGeneration(any());
    }

    @Test
    void getTestGenerationStatus_WithInvalidJobId_ShouldReturnNotFound() {
        // Arrange
        String jobId = "invalid-job-id";
        when(testGenerationService.getJobStatus(jobId)).thenReturn(null);

        // Act
        var response = controller.getTestGenerationStatus(jobId);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Job non trovato", response.getBody().get("error"));
        verify(testGenerationService).getJobStatus(jobId);
    }
} 