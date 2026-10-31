-- V2: append-only, hash-chained audit trail (SQL Server)

CREATE TABLE audit_chain (
    seq             BIGINT IDENTITY(1,1) PRIMARY KEY,
    event_type      NVARCHAR(64)   NOT NULL,
    entity_type     NVARCHAR(32)   NOT NULL,
    entity_id       NVARCHAR(64)   NOT NULL,
    actor           NVARCHAR(200)  NOT NULL,
    payload_json    NVARCHAR(MAX)  NOT NULL,
    event_time      NVARCHAR(40)   NOT NULL,   -- ISO-8601 string, hashed exactly as stored
    previous_hash   CHAR(64)       NOT NULL,
    content_hash    CHAR(64)       NOT NULL,
    CONSTRAINT uk_audit_chain_hash UNIQUE (content_hash)
);
CREATE INDEX ix_audit_chain_entity ON audit_chain (entity_type, entity_id);
GO

-- Block UPDATE at engine level, for every login including dbo.
CREATE TRIGGER trg_audit_chain_no_update ON audit_chain
INSTEAD OF UPDATE
AS
BEGIN
    THROW 51000, 'audit_chain is append-only: UPDATE is not allowed', 1;
END;
GO

-- Block DELETE at engine level.
CREATE TRIGGER trg_audit_chain_no_delete ON audit_chain
INSTEAD OF DELETE
AS
BEGIN
    THROW 51001, 'audit_chain is append-only: DELETE is not allowed', 1;
END;
GO

-- Defence in depth: the application login has no UPDATE/DELETE permission either.
IF DATABASE_PRINCIPAL_ID('codehealth_app') IS NOT NULL
BEGIN
    DENY UPDATE, DELETE, ALTER ON dbo.audit_chain TO codehealth_app;
END;
GO
