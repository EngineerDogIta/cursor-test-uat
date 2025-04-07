#!/bin/bash

# Base URL of the running application
BASE_URL="http://localhost:8080/api/v1/test-generation"

echo "--- Testing Test Generation API Endpoints ---"
echo "Base URL: $BASE_URL"
echo

# --- 1. Start Test Generation ---
echo "1. Attempting to start a new test generation job..."
TICKET_PAYLOAD='{
  "ticketId": "JIRA-123",
  "title": "Implement new user login feature",
  "description": "As a user, I want to be able to log in using my email and password.",
  "components": ["Backend"],
  "content": "Please generate integration tests for the login flow.",
  "acceptanceCriteria": [
    "Given I am on the login page",
    "When I enter valid credentials",
    "Then I should be redirected to my dashboard",
    "---",
    "Given I am on the login page",
    "When I enter invalid credentials",
    "Then I should see an error message"
  ],
  "additionalContext": "The backend service for authentication is already deployed at auth.example.com"
}'

# Send POST request
# Use jq to pretty-print the JSON response if available, otherwise just print raw response
START_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$TICKET_PAYLOAD" \
  "$BASE_URL/start")

echo "Response from /start:"
# Check if the response starts with { (simple JSON check) before piping to jq
if [[ "$START_RESPONSE" == {* ]]; then
  echo "$START_RESPONSE" | jq '.' || echo "$START_RESPONSE" # Fallback if jq fails
else
  echo "$START_RESPONSE"
fi
echo "-----------------------------------------"
echo

# Extract Job ID if the response was JSON and contained jobId
ACTUAL_JOB_ID=""
if [[ "$START_RESPONSE" == {* ]]; then
  # Use jq to extract .jobId, handle potential null or missing key gracefully
  TEMP_ID=$(echo "$START_RESPONSE" | jq -e '.jobId // empty')
  if [ $? -eq 0 ] && [ -n "$TEMP_ID" ]; then
      ACTUAL_JOB_ID=$TEMP_ID
      echo "Extracted Job ID: $ACTUAL_JOB_ID"
  else
      echo "Warning: Could not extract Job ID from /start response. Using placeholder."
      ACTUAL_JOB_ID=1 # Fallback to placeholder if extraction fails
  fi
else
    echo "Warning: /start response was not JSON. Using placeholder Job ID."
    ACTUAL_JOB_ID=1 # Fallback to placeholder if not JSON
fi
echo

# --- 2. Get Job Status --- Using ACTUAL_JOB_ID
echo "2. Attempting to get status for job ID: $ACTUAL_JOB_ID ..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/status/$ACTUAL_JOB_ID")

echo "Response from /status/$ACTUAL_JOB_ID:"
if command -v jq &> /dev/null; then
    echo "$STATUS_RESPONSE" | jq '.' || echo "$STATUS_RESPONSE"
else
    echo "$STATUS_RESPONSE"
fi
echo "-----------------------------------------"
echo

# --- 3. Get Job Logs --- Using ACTUAL_JOB_ID
echo "3. Attempting to get logs for job ID: $ACTUAL_JOB_ID ..."
LOGS_RESPONSE=$(curl -s -X GET "$BASE_URL/jobs/$ACTUAL_JOB_ID/logs")

echo "Response from /jobs/$ACTUAL_JOB_ID/logs:"
if command -v jq &> /dev/null; then
    echo "$LOGS_RESPONSE" | jq '.' || echo "$LOGS_RESPONSE"
else
    echo "$LOGS_RESPONSE"
fi
echo "-----------------------------------------"
echo

# --- 4. Get Job Test Result --- Using ACTUAL_JOB_ID
echo "4. Attempting to get test result for job ID: $ACTUAL_JOB_ID ..."
RESULT_RESPONSE=$(curl -s -X GET "$BASE_URL/jobs/$ACTUAL_JOB_ID/test-result")

echo "Response from /jobs/$ACTUAL_JOB_ID/test-result:"
if command -v jq &> /dev/null; then
    echo "$RESULT_RESPONSE" | jq '.' || echo "$RESULT_RESPONSE"
else
    echo "$RESULT_RESPONSE"
fi
echo "-----------------------------------------"
echo

# --- 5. Delete Job ---
PLACEHOLDER_DELETE_JOB_ID=999 # Using a different ID to avoid deleting the one we might be checking
echo "5. Attempting to delete job ID: $PLACEHOLDER_DELETE_JOB_ID ..."
# Use -v for verbose output to see headers, including the HTTP status code (expect 204 or 404/409)
curl -v -X DELETE "$BASE_URL/$PLACEHOLDER_DELETE_JOB_ID"
echo
echo "-----------------------------------------"

echo "--- Test Script Finished ---"