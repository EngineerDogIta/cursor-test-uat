# Docker Troubleshooting Guide

This guide provides steps to troubleshoot issues with the application running in Docker using `docker-compose`.

## 1. Check Container Status

Verify which containers are running, their status, and port mappings.

```bash
docker-compose ps
```

Look for the `mia-app-container` and `ollama-container`. Ensure they are `Up` and ports are correctly mapped (e.g., `8080->8080/tcp`).

## 2. Check Container Logs

Inspect the recent logs from the application container for errors or startup issues.

```bash
# Check the last 100 lines of the application container logs
docker-compose logs --tail=100 app

# Check logs for the Ollama container if issues seem related to the LLM
docker-compose logs --tail=100 ollama
```

## 3. Check Container Resources

Ensure containers aren't hitting resource limits (CPU, Memory).

```bash
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
```
Check the resource usage for `mia-app-container` and `ollama-container`. High CPU or memory usage might indicate problems.

## 4. Check Container Health

If healthchecks are configured, check their status.

```bash
# Check health of the application container
docker inspect --format='{{json .State.Health}}' mia-app-container

# Check health of the Ollama container
docker inspect --format='{{json .State.Health}}' ollama-container
```
Look for `"Status": "healthy"`. An `"unhealthy"` status indicates the healthcheck command is failing.

## 5. Check Application Health Endpoint

Directly query the application's health endpoint.

```bash
curl -s http://localhost:8080/actuator/health | jq '.'
# Or without jq:
# curl -s http://localhost:8080/actuator/health ; echo
```
Look for `"status": "UP"`. If the command fails or shows `"DOWN"`, check the application logs (Step 2).

## 6. Check Database Connection (Production Profile)

If using the `prod` profile (Oracle database) and suspect database issues:

*   **Verify Environment Variables:** Ensure the required environment variables are correctly set **when starting `docker-compose`**. These are typically passed to the `app` service in `docker-compose.yml` or sourced from an `.env` file.
    *   `SPRING_DATASOURCE_URL`: The full JDBC URL (e.g., `jdbc:oracle:thin:@//your-oracle-host:1521/YOUR_SERVICE`)
    *   `DB_USERNAME`: Database username
    *   `DB_PASSWORD`: Database password
*   **Check Network:** Ensure the Docker container can reach the Oracle database host and port specified in `SPRING_DATASOURCE_URL`. Network policies or firewalls might block connectivity. You can test connectivity from within the container:
    ```bash
    docker-compose exec app bash
    # Inside the container, try to ping or connect to the host/port
    # Example: apt-get update && apt-get install -y netcat && nc -zv your-oracle-host 1521
    # (Install tools like netcat or telnet if needed for testing)
    exit 
    ```
*   **Check Application Logs:** Look for specific database connection errors (e.g., `ORA-xxxxx` codes, timeout errors) in the application logs (Step 2).

## 7. Check Ollama Connection

If the application seems unable to connect to Ollama:

*   **Verify Ollama Container:** Ensure the `ollama-container` is running and healthy (Steps 1, 4).
*   **Check Ollama Logs:** Look for errors in the Ollama container logs (Step 2).
*   **Check Network:** The `app` container connects to Ollama via its service name (`ollama`) on port `11434` within the Docker network. This should generally work out-of-the-box with `docker-compose`. Verify the `OLLAMA_HOST` environment variable in the `app` service configuration matches (`http://ollama:11434`).
*   **Check Application Logs:** Look for connection refused or timeout errors related to `ollama:11434` in the application logs (Step 2).

## 8. Check Volume Mounts

Verify data persistence volumes exist.

```bash
docker volume ls
```
Look for volumes like `yourprojectname_app_data`, `yourprojectname_app_logs`, `yourprojectname_ollama_data` (the prefix might vary based on your project directory name).

## 9. Check Network Configuration

Inspect the Docker network created by `docker-compose`.

```bash
docker network inspect <your_project_directory_name>_default
# Example: docker network inspect cursor-test-uat_default 
```
Verify both `mia-app-container` and `ollama-container` are listed under `Containers`.

## Further Steps

*   **Restart:** Try restarting the containers: `docker-compose down && docker-compose up -d --build`
*   **Clean Up:** Prune unused Docker resources: `docker system prune -a --volumes` (Use with caution - this removes unused images, containers, networks, and **volumes**). 