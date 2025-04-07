package com.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.util.StringUtils;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyInterceptor.class);

    @Value("${api.security.key:dummy-key}") // Read key from properties, provide a default
    private String expectedApiKey;

    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String providedApiKey = request.getHeader(API_KEY_HEADER);

        if (!StringUtils.hasText(providedApiKey)) {
            logger.warn("Missing API Key header for request: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API Key");
            return false;
        }

        if (!expectedApiKey.equals(providedApiKey)) {
            logger.warn("Invalid API Key provided for request: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API Key");
            return false;
        }

        // If key is present and valid, allow request to proceed
        logger.debug("Valid API Key received for request: {}", request.getRequestURI());
        return true;
    }
} 