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

CREATE TABLE generation_job (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    ticket_content_id INTEGER,
    generated_test_id INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (ticket_content_id) REFERENCES ticket_content(id),
    FOREIGN KEY (generated_test_id) REFERENCES generated_test(id)
);

CREATE TABLE operation_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL UNIQUE,
    operation TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    job_uid TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ticket_content_uid ON ticket_content(uid);
CREATE INDEX idx_generated_test_uid ON generated_test(uid);
CREATE INDEX idx_generation_job_uid ON generation_job(uid);
CREATE INDEX idx_operation_log_uid ON operation_log(uid);
CREATE INDEX idx_operation_log_job_uid ON operation_log(job_uid); 