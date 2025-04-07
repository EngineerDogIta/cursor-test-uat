package com.example.dto;

import lombok.Data;
import java.util.List;

@Data
public class JiraSearchResponseDto {
    private String expand;
    private int startAt;
    private int maxResults;
    private int total;
    private List<JiraIssueDto> issues;
} 