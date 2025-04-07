# Spring AI Application with Test Generation

This project demonstrates using Spring AI to interact with LLMs via Ollama for various tasks, including chat and automated UAT test generation via a secured API.

## Features

*   **AI Chat:** Basic chat functionality via a REST endpoint (`/chat`).
*   **UAT Test Generation API:** Asynchronously generates UAT tests from provided content (e.g., Jira ticket descriptions) via secured REST endpoints under `/api/uat/`.
*   **Persistence:** Uses Hibernate/JPA for persistence, configured via Spring Profiles:
    *   **`dev` profile (default):** Uses a SQLite database (`data/test_generation.db`).
    *   **`prod` profile:** Uses an Oracle database (connection details configured via environment variables).

## Prerequisites

*   Java 17 or higher
*   Maven
*   Ollama installed and running locally (See [Ollama Website](https://ollama.com/))
*   An Ollama model downloaded (defaults to `mistral`). Pull it using:
    ```bash
    ollama pull mistral
    ```
    *(Note: The model can be changed in `src/main/resources/application.yml`)*
*   (For `prod` profile) Access to an Oracle database.

## Configuration

1.  **Ollama:** Ensure Ollama is running. By default, the application connects to `http://localhost:11434`. This can be overridden using the `OLLAMA_HOST` environment variable or by modifying `spring.ai.ollama.base-url` in `src/main/resources/application.yml`.
2.  **Model:** The default model is `mistral`. You can configure the model name and its parameters (`temperature`, `top_p`, etc.) under `spring.ai.ollama` in `src/main/resources/application.yml`.
3.  **Database (via Profiles):**
    *   Configuration is managed using Spring Profiles. Common settings are in `src/main/resources/application.yml`.
    *   **Development (`dev` profile):** Uses SQLite. Configuration is in `src/main/resources/application-dev.yml`. The database file is `data/test_generation.db` relative to the application run location.
    *   **Production (`prod` profile):** Uses Oracle. Configuration is in `src/main/resources/application-prod.yml`. Connection details (URL, username, password) should be provided via environment variables:
        *   `SPRING_DATASOURCE_URL` (e.g., `jdbc:oracle:thin:@//your-oracle-host:1521/YOUR_SERVICE`)
        *   `DB_USERNAME`
        *   `DB_PASSWORD`
4.  **API Key:** The UAT Generation API requires an API key. Set this securely in `src/main/resources/application.yml` under `api.security.key` or via the `API_SECURITY_KEY` environment variable (recommended for production).
5.  **Application Settings:** Other settings, like test generation parameters and validation prompts, are also configurable in `src/main/resources/application.yml`.

## Compiling and Running

1.  **Compile:**
    ```bash
    ./mvnw clean package
    # or mvn clean package if you have Maven installed globally
    ```

2.  **Run (Select Profile):**
    *   **Development (SQLite - Default if not specified):**
        ```bash
        # Using Maven plugin (activates 'dev' profile by default implicitly if no other active)
        ./mvnw spring-boot:run 
        # Or explicitly:
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
        # Or using the JAR:
        java -Dspring.profiles.active=dev -jar target/spring-ai-ollama-demo-*.jar
        ```
    *   **Production (Oracle):**
        ```bash
        # Set environment variables first (example)
        export SPRING_DATASOURCE_URL=jdbc:oracle:thin:@//your-oracle-host:1521/YOUR_SERVICE
        export DB_USERNAME=your_user
        export DB_PASSWORD=your_secret_password
        export API_SECURITY_KEY=your_prod_api_key 

        # Using Maven plugin:
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
        # Or using the JAR:
        java -Dspring.profiles.active=prod -jar target/spring-ai-ollama-demo-*.jar
        ```
    *(Replace `*` with the actual version in JAR commands)*

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
*   Spring Profiles
*   Ollama
*   Maven
*   SQLite (for `dev` profile)
*   Oracle (for `prod` profile)
*   Hibernate/JPA
*   Docker (optional, for containerization - see `Dockerfile` and `docker-compose.yml`) 