package com.example.dto;

import lombok.Data;

@Data
public class JiraUserDto {
    private String accountId;
    private String displayName;
    private String emailAddress;
    private String timeZone;
    private String locale;
} 