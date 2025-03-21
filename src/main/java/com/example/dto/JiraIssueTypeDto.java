package com.example.dto;

import lombok.Data;

@Data
public class JiraIssueTypeDto {
    private String id;
    private String name;
    private String description;
    private String iconUrl;
    private boolean subtask;
} 