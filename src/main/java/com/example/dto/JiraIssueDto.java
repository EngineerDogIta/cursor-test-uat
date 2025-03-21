package com.example.dto;

import lombok.Data;

@Data
public class JiraIssueDto {
    private String id;
    private String key;
    private String self;
    private JiraIssueFieldsDto fields;
} 