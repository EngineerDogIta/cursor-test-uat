package com.example.controller;

import com.example.model.TestGenerationJob;
import com.example.model.TicketRequest;
import com.example.service.TestGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

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
        return "views/dashboard";
    }

    @GetMapping("/ticket-form")
    public String ticketForm(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("ticketRequest", new TicketRequest());
        return "views/ticket-form";
    }

    @PostMapping("/ticket/submit")
    public String submitTicket(@ModelAttribute TicketRequest ticketRequest) {
        TestGenerationJob job = testGenerationService.createJob(ticketRequest);
        return "redirect:/job/" + job.getId();
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
} 