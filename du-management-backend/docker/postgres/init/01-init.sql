-- This script runs only on first container initialization.
-- It prepares common database extensions and a dedicated application role.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER DATABASE du_management SET timezone TO 'UTC';

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = 'du_app'
    ) THEN
        CREATE ROLE du_app LOGIN PASSWORD 'du_app_password';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE du_management TO du_app;
GRANT USAGE, CREATE ON SCHEMA public TO du_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO du_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO du_app;
