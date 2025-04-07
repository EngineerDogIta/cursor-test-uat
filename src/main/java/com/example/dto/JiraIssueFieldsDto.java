package com.example.dto;

import lombok.Data;
import java.util.List;

@Data
public class JiraIssueFieldsDto {
    private String summary;
    private String description;
    private JiraStatusDto status;
    private JiraIssueTypeDto issuetype;
    private List<JiraComponentDto> components;
} 