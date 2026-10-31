// Azure deployment: Container Apps + Azure SQL + Key Vault + managed identity.
// Secrets live only in Key Vault; container apps read them through the user-assigned identity.
// Prerequisite (once): create the Key Vault secrets listed in docs/deployment.md (db-password, ai-api-key, ...).

targetScope = 'resourceGroup'

@description('Short prefix for resource names')
param prefix string = 'codehealth'
param location string = resourceGroup().location
param acrName string
param imageTag string
@description('Entra ID group that administers Azure SQL')
param sqlAdminGroupObjectId string
param sqlAdminGroupName string
@description('Existing Key Vault that already holds the secrets')
param keyVaultName string = '${prefix}-kv'

var identityName = '${prefix}-id'

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
}

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

// Pull images with the managed identity (no ACR admin user).
resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, identity.id, 'acrpull')
  scope: acr
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
  }
}

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

// Key Vault Secrets User for the app identity.
resource kvSecretsUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(vault.id, identity.id, 'kv-secrets-user')
  scope: vault
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
  }
}

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource sqlServer 'Microsoft.Sql/servers@2023-08-01-preview' = {
  name: '${prefix}-sql'
  location: location
  properties: {
    minimalTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
    administrators: {
      administratorType: 'ActiveDirectory'
      login: sqlAdminGroupName
      sid: sqlAdminGroupObjectId
      tenantId: subscription().tenantId
      principalType: 'Group'
      azureADOnlyAuthentication: false
    }
  }
}

resource sqlDb 'Microsoft.Sql/servers/databases@2023-08-01-preview' = {
  parent: sqlServer
  name: 'codehealth'
  location: location
  sku: { name: 'GP_S_Gen5_1', tier: 'GeneralPurpose' }
  properties: {
    autoPauseDelay: 60
    minCapacity: json('0.5')
  }
}

resource allowAzure 'Microsoft.Sql/servers/firewallRules@2023-08-01-preview' = {
  parent: sqlServer
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${prefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

var kvSecret = (name) => {
  name: name
  keyVaultUrl: '${vault.properties.vaultUri}secrets/${name}'
  identity: identity.id
}

resource backend 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-backend'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identity.id}': {} }
  }
  dependsOn: [acrPull, kvSecretsUser]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: false
        targetPort: 8080
      }
      registries: [{ server: acr.properties.loginServer, identity: identity.id }]
      secrets: [
        kvSecret('db-password')
        kvSecret('db-migration-password')
        kvSecret('ai-api-key')
        kvSecret('ci-ingest-token')
        kvSecret('github-token')
      ]
    }
    template: {
      containers: [
        {
          name: 'backend'
          image: '${acr.properties.loginServer}/code-health-backend:${imageTag}'
          resources: { cpu: json('1.0'), memory: '2Gi' }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'openai,keycloak' }
            { name: 'DB_URL', value: 'jdbc:sqlserver://${sqlServer.properties.fullyQualifiedDomainName}:1433;databaseName=codehealth;encrypt=true;trustServerCertificate=false' }
            { name: 'DB_USERNAME', value: 'codehealth_app' }
            { name: 'DB_MIGRATION_USERNAME', value: 'codehealth_owner' }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'DB_MIGRATION_PASSWORD', secretRef: 'db-migration-password' }
            { name: 'AI_API_KEY', secretRef: 'ai-api-key' }
            { name: 'CI_INGEST_TOKEN', secretRef: 'ci-ingest-token' }
            { name: 'GITHUB_TOKEN', secretRef: 'github-token' }
            { name: 'LOG_FORMAT', value: 'ecs' }
          ]
          probes: [
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8080 }, initialDelaySeconds: 60 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8080 }, initialDelaySeconds: 30 }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 1 }   // one agent instance: the cycle lock is in-process
    }
  }
}

resource frontend 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-frontend'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identity.id}': {} }
  }
  dependsOn: [acrPull]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: true
        targetPort: 80
      }
      registries: [{ server: acr.properties.loginServer, identity: identity.id }]
    }
    template: {
      containers: [
        {
          name: 'frontend'
          image: '${acr.properties.loginServer}/code-health-frontend:${imageTag}'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'BACKEND_URL', value: 'http://${backend.name}' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 2 }
    }
  }
}

output frontendUrl string = 'https://${frontend.properties.configuration.ingress.fqdn}'
output sqlServer string = sqlServer.properties.fullyQualifiedDomainName
