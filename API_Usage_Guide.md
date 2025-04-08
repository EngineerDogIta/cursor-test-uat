# UAT Test Generation API Usage Guide

This guide explains how to interact with the asynchronous UAT Test Generation API.

## Prerequisites

*   The application must be running.
*   You need the `API_SECURITY_KEY` configured for the application. This key must be sent in the `X-API-Key` header with every request.

## API Base URL

All endpoints described below are relative to: `http://localhost:8080/api/uat/`

## Authentication

All API calls require an authentication token passed via the `X-API-Key` HTTP header.

Example Header:
```
X-API-Key: YOUR_API_KEY
```
Replace `YOUR_API_KEY` with the actual security key configured in the application.

## Typical Workflow

The API is asynchronous. You start a job, receive a job ID, and then poll for the status and results using that ID.

```mermaid
graph TD
    A[Client] -->|1. POST /generate (with payload & X-API-Key)| B(API);
    B -->|2. Returns {jobId}| A;
    A -->|3. GET /jobs/{jobId}/status (with X-API-Key)| B;
    B -->|4. Returns Status (PENDING/IN_PROGRESS)| A;
    subgraph Polling Loop
        direction TB
        C{Status == COMPLETED?};
        A -->|Check| C;
        C -- No -->|Wait & Retry| A;
    end
    C -- Yes --> D[Get Results/Logs];
    A -->|5. GET /jobs/{jobId}/test-result (with X-API-Key)| B;
    B -->|6. Returns Test Result| A;
    A -->|7. GET /jobs/{jobId}/logs (with X-API-Key)| B;
    B -->|8. Returns Logs| A;
    A -->|9. DELETE /jobs/{jobId} (with X-API-Key)| B;
    B -->|10. Returns 204 No Content| A;

    style Polling Loop fill:#f9f,stroke:#333,stroke-width:2px
```

*Diagram generated using Mermaid.* [[Source: Mermaid Flowchart Syntax](https://mermaid.js.org/syntax/flowchart.html)] [[Source: GitHub Mermaid Integration](https://github.blog/developer-skills/github/include-diagrams-markdown-files-mermaid/)]

## API Endpoints

Replace `{jobId}` with the actual ID received from the `/generate` endpoint and `YOUR_API_KEY` with your key in the examples below.

### 1. Start Test Generation

*   **Endpoint:** `POST /generate`
*   **Description:** Submits content (e.g., from a ticket) to start the asynchronous test generation process.
*   **Headers:** `Content-Type: application/json`, `X-API-Key: YOUR_API_KEY`
*   **Request Body (JSON):**
    ```json
    {
      "ticketId": "PROJ-123",
      "title": "Feature Title (Optional)",
      "description": "Detailed feature description...",
      "components": ["ComponentA", "ComponentB"],
      "content": "The core text/requirements to generate tests from.",
      "acceptanceCriteria": ["Criteria 1", "Criteria 2"],
      "additionalContext": "Any other relevant context..."
    }
    ```
    *(Note: `content` is often the most critical field, but others provide valuable context).*
*   **Success Response (202 Accepted):**
    ```json
    {
      "jobId": 123
    }
    ```
*   **Example (`curl`):**
    ```bash
    curl -X POST \
      -H "Content-Type: application/json" \
      -H "X-API-Key: YOUR_API_KEY" \
      -d '{ "content": "User login flow requires username and password.", "ticketId": "FEAT-456", "components": ["Auth"] }' \
      http://localhost:8080/api/uat/generate
    ```

### 2. Check Job Status

*   **Endpoint:** `GET /jobs/{jobId}/status`
*   **Description:** Checks the current status of a specific generation job.
*   **Headers:** `X-API-Key: YOUR_API_KEY`
*   **Success Response (200 OK):**
    ```json
    {
      "jobId": 123,
      "status": "IN_PROGRESS" // Possible values: PENDING, IN_PROGRESS, COMPLETED, FAILED
    }
    ```
*   **Example (`curl`):**
    ```bash
    curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/123/status
    ```

### 3. Get Test Result

*   **Endpoint:** `GET /jobs/{jobId}/test-result`
*   **Description:** Retrieves the generated test result for a completed job. Only available if the status is `COMPLETED`.
*   **Headers:** `X-API-Key: YOUR_API_KEY`
*   **Success Response (200 OK):**
    ```json
    {
      "jobId": 123,
      "status": "COMPLETED",
      "result": "Generated UAT test content...",
      "generatedAt": "2023-10-27T10:30:00Z" // Example timestamp
    }
    ```
    *(Note: Returns 404 if the job is not found or not yet completed/failed).*
*   **Example (`curl`):**
    ```bash
    curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/123/test-result
    ```

### 4. Get Job Logs

*   **Endpoint:** `GET /jobs/{jobId}/logs`
*   **Description:** Retrieves the logs recorded during the execution of a specific job. Useful for debugging, especially if a job fails.
*   **Headers:** `X-API-Key: YOUR_API_KEY`
*   **Success Response (200 OK):**
    ```json
    [
      {
        "timestamp": "2023-10-27T10:25:05Z",
        "level": "INFO",
        "message": "Job processing started."
      },
      {
        "timestamp": "2023-10-27T10:28:15Z",
        "level": "DEBUG",
        "message": "Calling LLM..."
      }
      // ... more logs
    ]
    ```
*   **Example (`curl`):**
    ```bash
    curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/123/logs
    ```

### 5. Delete Job

*   **Endpoint:** `DELETE /jobs/{jobId}`
*   **Description:** Deletes a job and its associated data (logs, results).
*   **Headers:** `X-API-Key: YOUR_API_KEY`
*   **Success Response:** `204 No Content`
*   **Example (`curl`):**
    ```bash
    curl -X DELETE -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/123
    ``` 