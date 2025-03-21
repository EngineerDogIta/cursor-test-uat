package com.example.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class JiraErrorResponseDto {
    private List<String> errorMessages;
    private Map<String, String> errors;
} 