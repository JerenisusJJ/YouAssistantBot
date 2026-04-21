-- AI Assistant Database Schema

-- Skills registry
CREATE TABLE IF NOT EXISTS skills (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Context history
CREATE TABLE IF NOT EXISTS context (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    skill_name VARCHAR(100),
    message TEXT NOT NULL,
    response TEXT,
    context_type VARCHAR(20) DEFAULT 'work' CHECK (context_type IN ('work', 'personal', 'common')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_context_session ON context(user_id, session_id);
CREATE INDEX idx_context_created ON context(created_at);

-- Config tokens (encrypted)
CREATE TABLE IF NOT EXISTS config (
    id SERIAL PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    encrypted_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Skill configurations
CREATE TABLE IF NOT EXISTS skill_config (
    id SERIAL PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    UNIQUE(skill_name, config_key)
);

-- Insert default skills
INSERT INTO skills (name, description) VALUES
    ('admin', 'Router skill - determines work/personal and selects subskill'),
    ('db', 'Database CRUD operations'),
    ('context', 'Dialog history management'),
    ('jira', 'Jira integration'),
    ('gitlab', 'GitLab integration'),
    ('confluence', 'Confluence integration'),
    ('mattermost', 'Mattermost integration')
ON CONFLICT (name) DO NOTHING;