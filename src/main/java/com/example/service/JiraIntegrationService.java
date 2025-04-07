package com.example.service;

import com.example.dto.*;
import com.example.exception.TicketAnalysisException;
import com.example.model.JiraConnection;
import com.example.repository.JiraConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.commons.validator.routines.EmailValidator;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

@Service
public class JiraIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(JiraIntegrationService.class);
    private final JiraConnectionRepository jiraConnectionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public JiraIntegrationService(JiraConnectionRepository jiraConnectionRepository) {
        this.jiraConnectionRepository = jiraConnectionRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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
            // Validazione dell'URL utilizzando Apache Commons Validator
            UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
            if (!urlValidator.isValid(credentials.getServerUrl())) {
                throw new RuntimeException("URL non valido. Deve iniziare con http:// o https:// ed essere corretta");
            }

            // Validazione dell'username come email utilizzando Apache Commons Validator
            EmailValidator emailValidator = EmailValidator.getInstance();
            if (!emailValidator.isValid(credentials.getUsername())) {
                throw new RuntimeException("Username non è un indirizzo email valido");
            }

            String url = credentials.getServerUrl().endsWith("/")
                ? credentials.getServerUrl() + "rest/api/2/myself"
                : credentials.getServerUrl() + "/rest/api/2/myself";
                
            HttpHeaders headers = createHeaders(credentials.getUsername(), credentials.getApiToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<JiraUserDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                JiraUserDto.class
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
        MDC.put("connectionId", connectionId.toString());
        MDC.put("operation", "searchTickets");
        
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new TicketAnalysisException("Connessione Jira non trovata"));
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/search"
            : connection.getServerUrl() + "/rest/api/2/search";
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        Map<String, String> body = new HashMap<>();
        body.put("jql", jql);
        
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        
        try {
            logger.info("Iniziando ricerca ticket con JQL");
            ResponseEntity<JiraSearchResponseDto> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                JiraSearchResponseDto.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TicketAnalysisException("Errore nella ricerca dei ticket");
            }
            
            JiraSearchResponseDto searchResponse = response.getBody();
            List<TicketContentDto> results = searchResponse != null && searchResponse.getIssues() != null ? 
                searchResponse.getIssues().stream()
                    .map(this::convertToTicketDto)
                    .collect(java.util.stream.Collectors.toList()) : 
                Collections.emptyList();
                
            logger.info("Ricerca completata con successo - {} ticket trovati", results.size());
            return results;
                
        } catch (HttpClientErrorException e) {
            logger.error("Errore client HTTP durante la ricerca dei ticket", e);
            
            String errorMessage = "Errore nella ricerca dei ticket";
            try {
                String responseBody = e.getResponseBodyAsString();
                JiraErrorResponseDto errorData = objectMapper.readValue(responseBody, JiraErrorResponseDto.class);
                
                if (errorData.getErrorMessages() != null && !errorData.getErrorMessages().isEmpty()) {
                    errorMessage = "Errore JQL: " + String.join(", ", errorData.getErrorMessages());
                } else if (errorData.getErrors() != null && !errorData.getErrors().isEmpty()) {
                    errorMessage = "Errore JQL: " + String.join(", ", errorData.getErrors().values());
                }
            } catch (Exception ex) {
                errorMessage = "Errore durante la ricerca dei ticket: " + e.getStatusText();
            }
            
            throw new TicketAnalysisException(errorMessage, e);
        } catch (ResourceAccessException e) {
            logger.error("Errore di connessione durante la ricerca dei ticket", e);
            throw new TicketAnalysisException("Impossibile raggiungere il server Jira. Verifica l'URL e la tua connessione.", e);
        } catch (Exception e) {
            logger.error("Errore durante la ricerca dei ticket", e);
            throw new TicketAnalysisException("Errore nella ricerca dei ticket: " + e.getMessage(), e);
        } finally {
            MDC.remove("connectionId");
            MDC.remove("operation");
        }
    }
    
    public TicketContentDto getTicketDetails(Long connectionId, String ticketId) {
        MDC.put("connectionId", connectionId.toString());
        MDC.put("ticketId", ticketId);
        MDC.put("operation", "getTicketDetails");
        
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new TicketAnalysisException("Connessione Jira non trovata"));
        
        connection.setLastUsedAt(LocalDateTime.now());
        jiraConnectionRepository.save(connection);
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/issue/" + ticketId
            : connection.getServerUrl() + "/rest/api/2/issue/" + ticketId;
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            logger.info("Iniziando recupero dettagli ticket");
            ResponseEntity<JiraIssueDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                JiraIssueDto.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TicketAnalysisException("Errore nel recupero del ticket");
            }
            
            logger.info("Recupero dettagli ticket completato con successo");
            return convertToTicketDto(response.getBody());
            
        } catch (HttpClientErrorException e) {
            logger.error("Errore client HTTP durante il recupero del ticket", e);
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new TicketAnalysisException("Ticket non trovato: " + ticketId, e);
            }
            throw new TicketAnalysisException("Errore nel recupero del ticket: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Errore durante il recupero del ticket", e);
            throw new TicketAnalysisException("Errore nel recupero del ticket: " + e.getMessage(), e);
        } finally {
            MDC.remove("connectionId");
            MDC.remove("ticketId");
            MDC.remove("operation");
        }
    }
    
    private TicketContentDto convertToTicketDto(JiraIssueDto issue) {
        JiraIssueFieldsDto fields = issue.getFields();
        StringBuilder content = new StringBuilder();
        content.append("Sommario: ").append(fields.getSummary()).append("\n\n");
        if (fields.getDescription() != null) {
            content.append("Descrizione: ").append(fields.getDescription()).append("\n\n");
        }
        JiraStatusDto status = fields.getStatus();
        if (status != null) {
            content.append("Stato: ").append(status.getName()).append("\n");
        }
        JiraIssueTypeDto issueType = fields.getIssuetype();
        if (issueType != null) {
            content.append("Tipo: ").append(issueType.getName()).append("\n");
        }
        
        List<String> components = new ArrayList<>();
        List<JiraComponentDto> componentsList = fields.getComponents();
        if (componentsList != null) {
            componentsList.forEach(component -> components.add(component.getName()));
        }
        
        return new TicketContentDto.Builder()
                .setContent(content.toString())
                .setTicketId(issue.getKey())
                .setComponents(components)
                .build();
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
    
    public List<JiraProjectDto> getProjects(Long connectionId) {
        JiraConnection connection = jiraConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connessione Jira non trovata"));
        
        String url = connection.getServerUrl().endsWith("/")
            ? connection.getServerUrl() + "rest/api/2/project"
            : connection.getServerUrl() + "/rest/api/2/project";
            
        HttpHeaders headers = createHeaders(connection.getUsername(), connection.getApiToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<List<JiraProjectDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<JiraProjectDto>>() {}
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