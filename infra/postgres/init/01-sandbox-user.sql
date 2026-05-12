DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'analytics_sandbox') THEN
    CREATE ROLE analytics_sandbox LOGIN PASSWORD 'sandbox';
  END IF;
END
$$;

GRANT CONNECT ON DATABASE analytics_trainer TO analytics_sandbox;

SELECT 'CREATE DATABASE analytics_sandbox OWNER analytics_sandbox'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_sandbox')\gexec
