CREATE TABLE jira_connections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    server_url TEXT NOT NULL,
    username TEXT NOT NULL,
    api_token TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

CREATE INDEX idx_jira_connections_created_at ON jira_connections(created_at); 