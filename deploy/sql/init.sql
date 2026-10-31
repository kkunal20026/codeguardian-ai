-- Creates the database and two logins with least privilege:
--   codehealth_owner : runs Flyway migrations (db_owner)
--   codehealth_app   : runtime login, read/write data, no DDL, no UPDATE/DELETE on audit_chain (denied in V2)
-- Passwords are passed in by sqlcmd variables, never stored in this file.

IF DB_ID('codehealth') IS NULL
    CREATE DATABASE codehealth;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'codehealth_owner')
    CREATE LOGIN codehealth_owner WITH PASSWORD = '$(DB_MIGRATION_PASSWORD)', CHECK_POLICY = ON;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'codehealth_app')
    CREATE LOGIN codehealth_app WITH PASSWORD = '$(DB_PASSWORD)', CHECK_POLICY = ON;
GO

USE codehealth;
GO

IF DATABASE_PRINCIPAL_ID('codehealth_owner') IS NULL
    CREATE USER codehealth_owner FOR LOGIN codehealth_owner;
IF DATABASE_PRINCIPAL_ID('codehealth_app') IS NULL
    CREATE USER codehealth_app FOR LOGIN codehealth_app;
GO

ALTER ROLE db_owner ADD MEMBER codehealth_owner;
ALTER ROLE db_datareader ADD MEMBER codehealth_app;
ALTER ROLE db_datawriter ADD MEMBER codehealth_app;
GO

-- Optional, stronger tamper evidence: SQL Server ledger. See docs/architecture.md (Audit trail).
