package com.example.service;

import com.example.dto.JiraCredentialsDto;
import com.example.dto.TicketContentDto;
import com.example.model.JiraConnection;
import com.example.repository.JiraConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

@Service
public class JiraIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(JiraIntegrationService.class);
    private final JiraConnectionRepository jiraConnectionRepository;
    private final RestTemplate restTemplate;
    
    @Autowired
    public JiraIntegrationService(JiraConnectionRepository jiraConnectionRepository) {
        this.jiraConnectionRepository = jiraConnectionRepository;
        this.restTemplate = new RestTemplate();
    }
    
    public JiraConnection saveConnection(JiraCredentialsDto credentials) {
        testConnection(credentials);
        
        JiraConnection connection = new JiraConnection();
        connection.setServerUrl(credentials.getServerUrl());
        connection.setUsername(credentials.getUsername());
        connection.setApiToken(credentials.getApiToken());
        connection.setCreatedAt(LocalDateTime.now());
        return jiraConnectionRepository.save(connection);
    }
    
    public List<JiraConnection> getConnections() {
        return jiraConnectionRepository.findAll();
    }
    
    public void testConnection(JiraCredentialsDto credentials) {
        try {
            if (!credentials.getServerUrl().startsWith("http")) {
                throw new RuntimeException("L'URL del server deve iniziare con http:// o https://");
            }
            
            String url = credentials.getServerUrl().endsWith("/")
                ? credentials.getServerUrl() + "rest/api/2/myself"
                : credentials.getServerUrl() + "/rest/api/2/myself";
                
            HttpHeaders headers = createHeaders(credentials.getUsername(), credentials.getApiToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Errore nella risposta del server Jira");
            }
            
            logger.info("Connessione a Jira riuscita: {}", credentials.getServerUrl());
            
        } catch (HttpClientErrorException e) {
            logger.error("Errore client HTTP durante la connessione a Jira", e);
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("Credenziali non valide. Verifica username e token API.");
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("Server Jira non trovato. Verifica l'URL.");
            } else {
                throw new RuntimeException("Errore durante la connessione a Jira: " + e.getMessage());
            }
        } catch (ResourceAccessException e) {
            logger.error("Errore di accesso alle risorse durante la connessione a Jira", e);
            throw new RuntimeException("Impossibile raggiungere il server Jira. Verifica l'URL e la tua connessione.");
        } catch (Exception e) {
            logger.error("Errore generico durante la connessione a Jira", e);
            throw new RuntimeException("Errore durante la connessione a Jira: " + e.getMessage());
        }
    }
    
    public List<TicketContentDto> searchTickets(Long connectionId, String jql) {
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connessione Jira non trovata"));
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/search"
            : connection.getServerUrl() + "/rest/api/2/search";
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        Map<String, String> body = new HashMap<>();
        body.put("jql", jql);
        
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Errore nella ricerca dei ticket");
            }
            
            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.getBody().get("issues");
            return issues.stream()
                .map(this::convertToTicketDto)
                .collect(java.util.stream.Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Errore durante la ricerca dei ticket", e);
            throw new RuntimeException("Errore nella ricerca dei ticket: " + e.getMessage());
        }
    }
    
    public TicketContentDto getTicketDetails(Long connectionId, String ticketId) {
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connessione Jira non trovata"));
        
        // Aggiorna l'ultimo utilizzo
        connection.setLastUsedAt(LocalDateTime.now());
        jiraConnectionRepository.save(connection);
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/issue/" + ticketId
            : connection.getServerUrl() + "/rest/api/2/issue/" + ticketId;
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Errore nel recupero del ticket");
            }
            
            return convertToTicketDto(response.getBody());
            
        } catch (HttpClientErrorException e) {
            logger.error("Errore client HTTP durante il recupero del ticket: {}", ticketId, e);
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("Ticket non trovato: " + ticketId);
            }
            throw new RuntimeException("Errore nel recupero del ticket: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Errore durante il recupero del ticket: {}", ticketId, e);
            throw new RuntimeException("Errore nel recupero del ticket: " + e.getMessage());
        }
    }
    
    private TicketContentDto convertToTicketDto(Map<String, Object> issue) {
        TicketContentDto dto = new TicketContentDto();
        dto.setTicketId((String) issue.get("key"));
        
        Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
        StringBuilder content = new StringBuilder();
        content.append("Sommario: ").append(fields.get("summary")).append("\n\n");
        
        if (fields.get("description") != null) {
            content.append("Descrizione: ").append(fields.get("description")).append("\n\n");
        }
        
        Map<String, Object> status = (Map<String, Object>) fields.get("status");
        if (status != null) {
            content.append("Stato: ").append(status.get("name")).append("\n");
        }
        
        Map<String, Object> issueType = (Map<String, Object>) fields.get("issuetype");
        if (issueType != null) {
            content.append("Tipo: ").append(issueType.get("name")).append("\n");
        }
        
        dto.setContent(content.toString());
        
        List<String> components = new ArrayList<>();
        List<Map<String, Object>> componentsList = (List<Map<String, Object>>) fields.get("components");
        if (componentsList != null) {
            componentsList.forEach(component -> components.add((String) component.get("name")));
        }
        dto.setComponents(components);
        
        return dto;
    }
    
    private HttpHeaders createHeaders(String username, String apiToken) {
        String auth = username + ":" + apiToken;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
    
    public List<Map<String, Object>> getProjects(Long connectionId) {
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connessione Jira non trovata"));
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/project"
            : connection.getServerUrl() + "/rest/api/2/project";
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                List.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Errore nel recupero dei progetti");
            }
            
            return response.getBody();
            
        } catch (HttpClientErrorException e) {
            logger.error("Errore client HTTP durante il recupero dei progetti", e);
            throw new RuntimeException("Errore nel recupero dei progetti: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Errore durante il recupero dei progetti", e);
            throw new RuntimeException("Errore nel recupero dei progetti: " + e.getMessage());
        }
    }
} 