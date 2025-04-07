package com.example.dto;

import lombok.Data;

@Data
public class JiraProjectDto {
    private String id;
    private String key;
    private String name;
    private String projectTypeKey;
    private boolean simplified;
    private String style;
    private boolean isPrivate;
} 