package com.example.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricsAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsAnalysisService.class);

    public String analyzeValidationAndEnhancePrompt(String validationResults) {
        try {
            ValidationMetrics metrics = extractValidationMetrics(validationResults);
            StringBuilder enhancedInstructions = new StringBuilder();
            enhancedInstructions.append("\nImprovements required based on previous validation:\n");
            if (metrics.isMetricLow(metrics.getCoherence())) {
                enhancedInstructions.append("- Improve test coherence: ensure tests are aligned with ticket requirements\n");
            }
            if (metrics.isMetricLow(metrics.getCompleteness())) {
                enhancedInstructions.append("- Improve completeness: add tests for missing scenarios\n");
            }
            if (metrics.isMetricLow(metrics.getClarity())) {
                enhancedInstructions.append("- Improve clarity: make tests more readable and understandable\n");
            }
            if (metrics.isMetricLow(metrics.getTestData())) {
                enhancedInstructions.append("- Improve test data: provide more specific and realistic test data\n");
            }
            if (!metrics.getIssues().isEmpty()) {
                enhancedInstructions.append("\nSpecific issues to resolve:\n");
                for (String[] issue : metrics.getIssues()) {
                    if (issue.length >= 3) {
                        String type = issue[0];
                        String severity = issue[1];
                        String fix = issue[2];
                        enhancedInstructions.append(String.format("- Improve %s (Severity: %s): %s\n", type.toLowerCase(), severity, fix));
                    }
                }
            }
            return enhancedInstructions.toString();
        } catch (Exception e) {
            logger.error("Error analyzing validation results", e);
            return "\nImprove test quality to meet validation requirements.\n";
        }
    }

    public ValidationMetrics extractValidationMetrics(String validationResults) {
        ValidationMetrics metrics = new ValidationMetrics();
        if (validationResults == null || validationResults.isEmpty()) {
            logger.warn("Empty or null validation results");
            return metrics;
        }
        try {
            String[] lines = validationResults.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("Coherence:")) {
                    metrics.setCoherence(trimmedLine.substring("Coherence:".length()).trim());
                } else if (trimmedLine.startsWith("Completeness:")) {
                    metrics.setCompleteness(trimmedLine.substring("Completeness:".length()).trim());
                } else if (trimmedLine.startsWith("Clarity:")) {
                    metrics.setClarity(trimmedLine.substring("Clarity:".length()).trim());
                } else if (trimmedLine.startsWith("TestData:")) {
                    metrics.setTestData(trimmedLine.substring("TestData:".length()).trim());
                }
            }
            if (validationResults.contains("ISSUES:")) {
                try {
                    String[] parts = validationResults.split("ISSUES:");
                    if (parts.length > 1) {
                        String issuesPart = parts[1];
                        if (issuesPart.contains("DETAILED VALIDATION:")) {
                            issuesPart = issuesPart.split("DETAILED VALIDATION:")[0];
                        }
                        String[] issueLines = issuesPart.split("\n");
                        String type = null;
                        String severity = null;
                        String fix = null;
                        for (String issueLine : issueLines) {
                            String trimmedIssueLine = issueLine.trim();
                            if (trimmedIssueLine.startsWith("Type:")) {
                                if (type != null && severity != null && fix != null) {
                                    metrics.addIssue(new String[]{type, severity, fix});
                                }
                                type = trimmedIssueLine.substring("Type:".length()).trim();
                                severity = null;
                                fix = null;
                            } else if (trimmedIssueLine.startsWith("Severity:")) {
                                severity = trimmedIssueLine.substring("Severity:".length()).trim();
                            } else if (trimmedIssueLine.startsWith("Fix:")) {
                                fix = trimmedIssueLine.substring("Fix:".length()).trim();
                            }
                        }
                        if (type != null && severity != null && fix != null) {
                            metrics.addIssue(new String[]{type, severity, fix});
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing ISSUES section", e);
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting validation metrics", e);
        }
        return metrics;
    }

    public String extractOverallQuality(String validationResults) {
        if (validationResults == null || validationResults.isEmpty()) {
            return "UNKNOWN";
        }
        if (validationResults.contains("OVERALL_QUALITY: QUALITY_HIGH")) {
            return "QUALITY_HIGH";
        } else if (validationResults.contains("OVERALL_QUALITY: QUALITY_MEDIUM")) {
            return "QUALITY_MEDIUM";
        } else if (validationResults.contains("OVERALL_QUALITY: QUALITY_LOW")) {
            return "QUALITY_LOW";
        } else {
            return "UNKNOWN";
        }
    }

    public static class ValidationMetrics {
        private String coherence = "";
        private String completeness = "";
        private String clarity = "";
        private String testData = "";
        private final java.util.List<String[]> issues = new java.util.ArrayList<>();

        public boolean isMetricLow(String metric) {
            return metric != null && !metric.isEmpty() && metric.equals("QUALITY_LOW");
        }

        public String getCoherence() { return coherence; }
        public void setCoherence(String coherence) { this.coherence = coherence; }
        public String getCompleteness() { return completeness; }
        public void setCompleteness(String completeness) { this.completeness = completeness; }
        public String getClarity() { return clarity; }
        public void setClarity(String clarity) { this.clarity = clarity; }
        public String getTestData() { return testData; }
        public void setTestData(String testData) { this.testData = testData; }
        public java.util.List<String[]> getIssues() { return issues; }
        public void addIssue(String[] issue) { this.issues.add(issue); }
    }
} 