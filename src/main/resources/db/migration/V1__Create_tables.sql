CREATE TABLE ticket_content (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL UNIQUE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE generated_test (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL UNIQUE,
    test_content TEXT NOT NULL,
    attempts INTEGER NOT NULL,
    validated BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE test_generation_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jira_ticket TEXT NOT NULL,
    description TEXT NOT NULL,
    components TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE job_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    FOREIGN KEY (job_id) REFERENCES test_generation_jobs(id)
);

CREATE INDEX idx_ticket_content_uid ON ticket_content(uid);
CREATE INDEX idx_generated_test_uid ON generated_test(uid);
CREATE INDEX idx_job_logs_job_id ON job_logs(job_id); 