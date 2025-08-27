-- Initialize the database and grant permissions
-- This script runs when the PostgreSQL container starts for the first time

-- Create the database if it doesn't exist (this should already be created by POSTGRES_DB)
-- But we'll ensure the user has proper permissions

-- Grant all privileges on the database to the user
GRANT ALL PRIVILEGES ON DATABASE icon_pack_generator TO iconpack;

-- Connect to the icon_pack_generator database and set up schema permissions
\c icon_pack_generator;

-- Grant schema permissions
GRANT ALL ON SCHEMA public TO iconpack;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO iconpack;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO iconpack;

-- Grant permissions for future tables and sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO iconpack;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO iconpack;
