package com.example.controller;

import com.example.model.TestGenerationJob;
import com.example.dto.TicketContentDto;
import com.example.service.TestGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;

@Controller
@RequestMapping("/")
public class TestGenerationViewController {

    private final TestGenerationService testGenerationService;

    @Autowired
    public TestGenerationViewController(TestGenerationService testGenerationService) {
        this.testGenerationService = testGenerationService;
    }

    @GetMapping
    public String dashboard(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("activeJobs", testGenerationService.getActiveJobs());
        model.addAttribute("completedJobs", testGenerationService.getCompletedJobs());
        model.addAttribute("isCompactView", request.getSession().getAttribute("isCompactView") != null ? 
                                      request.getSession().getAttribute("isCompactView") : false);
        return "views/dashboard";
    }

    @GetMapping("/ticket-form")
    public String ticketForm(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("ticketRequest", new TicketContentDto.Builder()
            .setContent("")
            .setTicketId("")
            .setComponents(new ArrayList<>())
            .build());
        return "views/ticket-form";
    }

    @PostMapping("/ticket/submit")
    public String submitTicket(@ModelAttribute TicketContentDto ticketRequest) {
        testGenerationService.startTestGeneration(ticketRequest);
        return "redirect:/";
    }

    @GetMapping("/job/{id}")
    public String jobDetail(@PathVariable Long id, Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("job", testGenerationService.getJob(id));
        return "views/job-detail";
    }

    @GetMapping("/api/jobs/{id}/status")
    @ResponseBody
    public TestGenerationJob getJobStatus(@PathVariable Long id) {
        return testGenerationService.getJob(id);
    }

    @GetMapping("/toggle-view")
    @ResponseBody
    public String toggleView(HttpServletRequest request) {
        Boolean currentView = (Boolean) request.getSession().getAttribute("isCompactView");
        boolean newView = currentView == null || !currentView;
        request.getSession().setAttribute("isCompactView", newView);
        return "{\"isCompactView\": " + newView + "}";
    }
} 