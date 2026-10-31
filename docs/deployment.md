# Deployment

## Local (Windows 11)

```powershell
.\scripts\setup.ps1                 # docker compose: SQL Server, Keycloak, Mailpit, backend, frontend
.\scripts\setup.ps1 -WithOllama     # plus a local LLM
```

| Service | URL |
|---|---|
| UI | http://localhost:5173 |
| API docs | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:9090 (users `viewer`, `approver`, `admin`; password must be changed at first login) |
| Mailpit | http://localhost:8025 |

To use Keycloak login: set `SECURITY_MODE=keycloak` and `SPRING_PROFILES_ACTIVE=demo,keycloak` in `.env`,
create `frontend/.env.local` with `VITE_AUTH_MODE=keycloak`, rebuild.

## Azure

Resources (deploy/azure/main.bicep): Container Apps environment with Log Analytics, backend (internal ingress)
and frontend (external) container apps, user-assigned managed identity, Azure SQL serverless database,
role assignments for ACR pull and Key Vault secrets.

One-time preparation:

1. Create a resource group, an ACR and a Key Vault (RBAC mode).
2. Add Key Vault secrets: `db-password`, `db-migration-password`, `ai-api-key`, `ci-ingest-token`, `github-token`.
3. Create the two SQL logins once with `deploy/sql/init.sql` (run it against Azure SQL as the Entra admin,
   skipping the `CREATE DATABASE` block).
4. Create an Entra app registration with a federated credential for this GitHub repository and give it
   Contributor on the resource group and User Access Administrator (for the role assignments).
5. GitHub repository variables: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`,
   `AZURE_RESOURCE_GROUP`, `AZURE_ACR_NAME`, `AZURE_SQL_ADMIN_GROUP_ID`, `AZURE_SQL_ADMIN_GROUP_NAME`.

Then run the **Deploy to Azure** workflow. It authenticates with OIDC, builds both images in ACR and applies
the Bicep template.

Production hardening still to consider: private endpoints for SQL and Key Vault, VNet-integrated environment,
custom domain with managed certificate, Keycloak (or Entra ID) as the identity provider for the UI, SQL ledger
for the audit table, and alert rules on `codehealth.llm.fallback` and audit verification.
