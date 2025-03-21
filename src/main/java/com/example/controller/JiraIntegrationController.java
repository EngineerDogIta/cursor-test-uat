package com.example.controller;

import com.example.dto.*;
import com.example.model.JiraConnection;
import com.example.service.JiraIntegrationService;
import com.example.service.TestGenerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/jira")
public class JiraIntegrationController {
    
    private static final Logger logger = LoggerFactory.getLogger(JiraIntegrationController.class);
    private final JiraIntegrationService jiraService;
    private final TestGenerationService testGenerationService;
    
    @Autowired
    public JiraIntegrationController(
            JiraIntegrationService jiraService,
            TestGenerationService testGenerationService) {
        this.jiraService = jiraService;
        this.testGenerationService = testGenerationService;
    }
    
    @GetMapping("/connect")
    public String showConnectionForm(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("credentials", new JiraCredentialsDto());
        return "views/jira/connect";
    }
    
    @PostMapping("/connect")
    public String saveConnection(@Valid @ModelAttribute("credentials") JiraCredentialsDto credentials, Model model) {
        try {
            JiraConnection connection = jiraService.saveConnection(credentials);
            return "redirect:/jira/search?connectionId=" + connection.getId();
        } catch (Exception e) {
            logger.error("Errore durante la connessione a Jira", e);
            model.addAttribute("error", "Errore: " + e.getMessage());
            return "views/jira/connect";
        }
    }
    
    @GetMapping("/search")
    public String showSearchPage(@RequestParam Long connectionId, Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("connectionId", connectionId);
        
        try {
            List<JiraProjectDto> projects = jiraService.getProjects(connectionId);
            model.addAttribute("projects", projects);
        } catch (Exception e) {
            logger.error("Errore durante il caricamento dei progetti Jira", e);
            model.addAttribute("error", "Impossibile caricare i progetti: " + e.getMessage());
            model.addAttribute("projects", Collections.emptyList());
        }
        
        return "views/jira/search";
    }
    
    @PostMapping("/search")
    public String searchTickets(
            @RequestParam Long connectionId,
            @RequestParam String jql,
            Model model,
            HttpServletRequest request) {
        try {
            model.addAttribute("currentUri", request.getRequestURI());
            List<TicketContentDto> tickets = jiraService.searchTickets(connectionId, jql);
            model.addAttribute("tickets", tickets);
            model.addAttribute("connectionId", connectionId);
            model.addAttribute("jql", jql);
            return "views/jira/search-results";
        } catch (Exception e) {
            logger.error("Errore durante la ricerca dei ticket", e);
            model.addAttribute("error", e.getMessage().replace("\"", "&quot;"));
            model.addAttribute("connectionId", connectionId);
            model.addAttribute("jql", jql);
            return "views/jira/search";
        }
    }
    
    @GetMapping("/ticket/{ticketId}")
    public String showTicketDetails(
            @RequestParam Long connectionId,
            @PathVariable String ticketId,
            Model model,
            HttpServletRequest request) {
        try {
            model.addAttribute("currentUri", request.getRequestURI());
            TicketContentDto ticket = jiraService.getTicketDetails(connectionId, ticketId);
            model.addAttribute("ticket", ticket);
            model.addAttribute("connectionId", connectionId);
            return "views/jira/ticket-details";
        } catch (Exception e) {
            logger.error("Errore durante il recupero del ticket", e);
            model.addAttribute("error", "Errore: " + e.getMessage());
            return "redirect:/jira/search?connectionId=" + connectionId;
        }
    }
    
    @PostMapping("/import/{ticketId}")
    public String importTicket(
            @RequestParam Long connectionId,
            @PathVariable String ticketId) {
        try {
            TicketContentDto ticket = jiraService.getTicketDetails(connectionId, ticketId);
            testGenerationService.startTestGeneration(ticket);
            return "redirect:/?message=Importazione avviata con successo";
        } catch (Exception e) {
            logger.error("Errore durante l'importazione del ticket", e);
            return "redirect:/jira/ticket/" + ticketId + "?connectionId=" + connectionId + "&error=" + e.getMessage();
        }
    }
    
    @GetMapping("/connections")
    public String listConnections(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("connections", jiraService.getConnections());
        return "views/jira/connections";
    }
    
    @ResponseBody
    @PostMapping("/api/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@Valid @RequestBody JiraCredentialsDto credentials) {
        try {
            jiraService.testConnection(credentials);
            return ResponseEntity.ok(Map.of("success", true, "message", "Connessione riuscita"));
        } catch (Exception e) {
            logger.error("Errore durante il test della connessione", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    @GetMapping("/connections/{connectionId}/projects")
    public ResponseEntity<List<JiraProjectDto>> getProjects(@PathVariable Long connectionId) {
        try {
            List<JiraProjectDto> projects = jiraService.getProjects(connectionId);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            logger.error("Errore durante il recupero dei progetti", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.emptyList());
        }
    }
} 