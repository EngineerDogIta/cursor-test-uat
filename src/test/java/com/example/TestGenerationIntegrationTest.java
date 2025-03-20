package com.example;

import com.example.dto.TicketContentDto;
import com.example.model.TestGenerationJob;
import com.example.service.TestGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TestGenerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestGenerationService testGenerationService;

    @Test
    public void testCompleteTestGenerationFlow() throws Exception {
        // 1. Crea un nuovo job di test
        String jsonRequest = """
            {
                "content": "Implementare la funzionalità di login",
                "ticketId": "PROJ-123",
                "components": ["Authentication"]
            }
            """;

        MvcResult result = mockMvc.perform(post("/generate-tests/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn();

        String jobId = result.getResponse().getContentAsString()
                .split("\"jobId\":\"")[1]
                .split("\"")[0];

        // 2. Verifica lo stato iniziale
        mockMvc.perform(get("/generate-tests/status/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        // 3. Attendi il completamento del job (con timeout)
        int maxAttempts = 10;
        int attempts = 0;
        String status = "STARTED";

        while (attempts < maxAttempts && (status.equals("STARTED") || status.equals("ANALYZING") || status.equals("GENERATING"))) {
            Thread.sleep(2000); // Attendi 2 secondi tra i tentativi
            attempts++;

            MvcResult statusResult = mockMvc.perform(get("/generate-tests/status/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn();

            status = statusResult.getResponse().getContentAsString()
                    .split("\"status\":\"")[1]
                    .split("\"")[0];
        }

        // 4. Verifica il risultato finale
        assertTrue(status.equals("COMPLETED") || status.equals("FAILED"),
                "Il job dovrebbe essere completato o fallito dopo " + maxAttempts + " tentativi");

        if (status.equals("COMPLETED")) {
            mockMvc.perform(get("/generate-tests/status/" + jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").exists())
                    .andExpect(jsonPath("$.error").isEmpty());
        } else {
            mockMvc.perform(get("/generate-tests/status/" + jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Test
    public void testInvalidTicketRequest() throws Exception {
        String invalidJsonRequest = """
            {
                "content": "",
                "ticketId": "",
                "components": []
            }
            """;

        mockMvc.perform(post("/generate-tests/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    public void testNonExistentJobStatus() throws Exception {
        mockMvc.perform(get("/generate-tests/status/non-existent-job-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Job not found"));
    }
} 