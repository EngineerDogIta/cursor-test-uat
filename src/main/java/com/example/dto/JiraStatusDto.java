package com.example.dto;

import lombok.Data;

@Data
public class JiraStatusDto {
    private String id;
    private String name;
    private String description;
    private String iconUrl;
    private JiraStatusCategoryDto statusCategory;
} 