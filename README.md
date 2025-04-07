# Spring AI Application with Test Generation

This project demonstrates using Spring AI to interact with LLMs via Ollama for various tasks, including chat and automated UAT test generation via a secured API.

## Features

*   **AI Chat:** Basic chat functionality via a REST endpoint (`/chat`).
*   **UAT Test Generation API:** Asynchronously generates UAT tests from provided content (e.g., Jira ticket descriptions) via secured REST endpoints under `/api/uat/`.
*   **Persistence:** Uses a SQLite database (`data/test_generation.db`) to store job data.

## Prerequisites

*   Java 17 or higher
*   Maven
*   Ollama installed and running locally (See [Ollama Website](https://ollama.com/))
*   An Ollama model downloaded (defaults to `mistral`). Pull it using:
    ```bash
    ollama pull mistral
    ```
    *(Note: The model can be changed in `application.yml`)*

## Configuration

1.  **Ollama:** Ensure Ollama is running. By default, the application connects to `http://localhost:11434`. This can be overridden using the `OLLAMA_HOST` environment variable or by modifying `spring.ai.ollama.base-url` in `src/main/resources/application.yml`.
2.  **Model:** The default model is `mistral`. You can configure the model name and its parameters (`temperature`, `top_p`, etc.) under `spring.ai.ollama` in `src/main/resources/application.yml`.
3.  **Database:** The application uses a SQLite database located at `data/test_generation.db` by default. The path can be changed in `application.yml` under `spring.datasource.url`.
4.  **Application Settings:** Other settings, like test generation parameters and validation prompts, are also configurable in `application.yml`.

## Compiling and Running

1.  **Compile:**
    ```bash
    ./mvnw clean package
    # or mvn clean package if you have Maven installed globally
    ```

2.  **Run:**
    ```bash
    java -jar target/spring-ai-ollama-demo-*.jar
    ```
    *(Replace `*` with the actual version)*

## Usage

*   **Chat Endpoint:**
    ```bash
    curl -X POST -H "Content-Type: text/plain" -d "Hello AI!" http://localhost:8080/chat
    ```
*   **UAT Test Generation API (Requires `X-API-Key` Header):**
    *   Start Asynchronous Test Generation:
        ```bash
        # Replace YOUR_API_KEY with the actual key
        curl -X POST -H "Content-Type: application/json" -H "X-API-Key: YOUR_API_KEY" -d '{ 
          "content": "User should be able to login with valid credentials.", 
          "ticketId": "PROJ-123", 
          "components": ["Authentication"] 
        }' http://localhost:8080/api/uat/generate
        ```
        (This returns a `jobId`)
    *   Check Job Status:
        ```bash
        # Replace {jobId} and YOUR_API_KEY
        curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/{jobId}/status 
        ```
    *   Get Test Result:
        ```bash
        # Replace {jobId} and YOUR_API_KEY
        curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/{jobId}/test-result
        ```
    *   Get Job Logs:
        ```bash
        # Replace {jobId} and YOUR_API_KEY
        curl -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/{jobId}/logs 
        ```
    *   Delete Job:
        ```bash
        # Replace {jobId} and YOUR_API_KEY
        curl -X DELETE -H "X-API-Key: YOUR_API_KEY" http://localhost:8080/api/uat/jobs/{jobId}
        ```

## Key Technologies

*   Java 17
*   Spring Boot 3.x
*   Spring AI
*   Ollama
*   Maven
*   SQLite
*   Hibernate/JPA
*   Docker (optional, for containerization - see `Dockerfile` and `docker-compose.yml`) 