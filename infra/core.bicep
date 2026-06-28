// core.bicep — all resources for the createAppointment flow, in one module.
// Secrets strategy:
//   • Cosmos DB  → Managed Identity (CosmosDBDataContributor role) — no key in config
//   • Service Bus → Managed Identity (ServiceBusDataOwner role)    — no connection string in config
//   • SQL password → stored in Key Vault; Function App reads via KV reference (@Microsoft.KeyVault(...))

param projectName string
param environment string
param location string
param tags object
param suffix string

@secure()
param sqlAdminPassword string

@description('SQL administrator username.')
param sqlAdminUser string = 'clinicadmin'

@description('Region for Azure SQL (separate because not all regions accept new SQL servers).')
param sqlLocation string = 'westus3'

@description('Region for the App Service plan + Function App.')
param appLocation string = 'centralus'

// --- Cosmos DB (state tracking, serverless) ---
resource cosmos 'Microsoft.DocumentDB/databaseAccounts@2024-05-15' = {
  name: 'cosmos-${projectName}-${environment}-${suffix}'
  location: location
  tags: tags
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    capabilities: [ { name: 'EnableServerless' } ]
    consistencyPolicy: { defaultConsistencyLevel: 'Session' }
    locations: [ { locationName: location, failoverPriority: 0 } ]
  }
}

resource cosmosDb 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2024-05-15' = {
  parent: cosmos
  name: 'clinicdb'
  properties: { resource: { id: 'clinicdb' } }
}

resource cosmosContainer 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2024-05-15' = {
  parent: cosmosDb
  name: 'appointments'
  properties: {
    resource: {
      id: 'appointments'
      partitionKey: { paths: [ '/id' ], kind: 'Hash' }
    }
  }
}

// --- Service Bus (event backbone: created + completed topics) ---
resource sb 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: 'sb-${projectName}-${environment}-${suffix}'
  location: location
  tags: tags
  sku: { name: 'Standard', tier: 'Standard' }
  properties: { minimumTlsVersion: '1.2' }
}

resource createdTopic 'Microsoft.ServiceBus/namespaces/topics@2022-10-01-preview' = {
  parent: sb
  name: 'appointment-created'
  properties: { defaultMessageTimeToLive: 'P14D' }
}

resource completedTopic 'Microsoft.ServiceBus/namespaces/topics@2022-10-01-preview' = {
  parent: sb
  name: 'appointment-completed'
  properties: { defaultMessageTimeToLive: 'P14D' }
}

// Country-specific subscriptions — each only receives messages for its country.
// The publisher sets message.Subject = countryISO (e.g. "PE"), so the SQL filter
// "sys.Subject = 'PE'" routes messages at the broker level, not in application code.
resource subPE 'Microsoft.ServiceBus/namespaces/topics/subscriptions@2022-10-01-preview' = {
  parent: createdTopic
  name: 'pe-worker'
  properties: {
    maxDeliveryCount: 5
    deadLetteringOnMessageExpiration: true
    lockDuration: 'PT1M'
    defaultRuleAction: 'drop'  // drop the default TrueFilter rule; replaced below
  }
}

resource subPEFilter 'Microsoft.ServiceBus/namespaces/topics/subscriptions/rules@2022-10-01-preview' = {
  parent: subPE
  name: 'country-pe'
  properties: {
    filterType: 'SqlFilter'
    sqlFilter: { sqlExpression: 'sys.Subject = \'PE\'' }
  }
}

resource subCL 'Microsoft.ServiceBus/namespaces/topics/subscriptions@2022-10-01-preview' = {
  parent: createdTopic
  name: 'cl-worker'
  properties: {
    maxDeliveryCount: 5
    deadLetteringOnMessageExpiration: true
    lockDuration: 'PT1M'
    defaultRuleAction: 'drop'
  }
}

resource subCLFilter 'Microsoft.ServiceBus/namespaces/topics/subscriptions/rules@2022-10-01-preview' = {
  parent: subCL
  name: 'country-cl'
  properties: {
    filterType: 'SqlFilter'
    sqlFilter: { sqlExpression: 'sys.Subject = \'CL\'' }
  }
}

// --- Monitoring ---
resource log 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${projectName}-${environment}'
  location: location
  tags: tags
  properties: { sku: { name: 'PerGB2018' }, retentionInDays: 30 }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-${projectName}-${environment}'
  location: location
  tags: tags
  kind: 'web'
  properties: { Application_Type: 'web', WorkspaceResourceId: log.id }
}

// --- Storage (Functions runtime) ---
resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: take('st${projectName}${environment}${suffix}', 24)
  location: location
  tags: tags
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
  properties: { minimumTlsVersion: 'TLS1_2', allowBlobPublicAccess: false, supportsHttpsTrafficOnly: true }
}

// --- Azure SQL Database (final relational persistence) ---
// Used instead of Azure Database for MySQL because new free subscriptions are
// temporarily blocked from provisioning MySQL Flexible Server. Equivalent role
// to the AWS project's MySQL store for completed appointments.
// Deployed in sqlLocation (westus3) because not all regions accept new SQL servers.
resource sqlServer 'Microsoft.Sql/servers@2023-08-01-preview' = {
  name: 'sql-${projectName}-${environment}-${suffix}'
  location: sqlLocation
  tags: tags
  properties: {
    administratorLogin: sqlAdminUser
    administratorLoginPassword: sqlAdminPassword
    minimalTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
  }
}

resource sqlDb 'Microsoft.Sql/servers/databases@2023-08-01-preview' = {
  parent: sqlServer
  name: 'clinicdb'
  location: sqlLocation
  tags: tags
  sku: { name: 'Basic', tier: 'Basic' }
}

// Allow other Azure services (the Function App) to reach the server.
resource sqlFirewallAzure 'Microsoft.Sql/servers/firewallRules@2023-08-01-preview' = {
  parent: sqlServer
  name: 'AllowAzureServices'
  properties: { startIpAddress: '0.0.0.0', endIpAddress: '0.0.0.0' }
}

// --- Key Vault (secrets store: SQL password) ---
resource kv 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: take('kv-${projectName}-${environment}-${suffix}', 24)
  location: location
  tags: tags
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

resource kvSqlPassword 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: kv
  name: 'sql-admin-password'
  properties: { value: sqlAdminPassword }
}

// --- App Service Plan B1 + Function App (Java 21) ---
// Switched from Flex Consumption to B1 because Flex + Java has deployment
// friction (no remote build for Java, zip package not registering). B1 is a
// dedicated plan that reliably runs Java Functions and supports standard zip /
// maven deployment. Trade-off: always-on (no scale-to-zero) vs. predictable deploy.
resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: 'plan-${projectName}-${environment}'
  location: appLocation
  tags: tags
  sku: { name: 'B1', tier: 'Basic' }
  kind: 'linux'
  properties: { reserved: true }
}

resource functionApp 'Microsoft.Web/sites@2023-12-01' = {
  name: 'func-${projectName}-${environment}'
  location: appLocation
  tags: tags
  kind: 'functionapp,linux'
  identity: { type: 'SystemAssigned' }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'JAVA|21'
      minTlsVersion: '1.2'
      ftpsState: 'Disabled'
      alwaysOn: true
      appSettings: [
        { name: 'AzureWebJobsStorage', value: 'DefaultEndpointsProtocol=https;AccountName=${storage.name};EndpointSuffix=${az.environment().suffixes.storage};AccountKey=${storage.listKeys().keys[0].value}' }
        { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
        { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
        { name: 'WEBSITE_RUN_FROM_PACKAGE', value: '1' }
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
        // Cosmos: endpoint only — SDK uses the Function App's Managed Identity (no key)
        { name: 'COSMOS_ENDPOINT', value: cosmos.properties.documentEndpoint }
        { name: 'COSMOS_DATABASE', value: 'clinicdb' }
        { name: 'COSMOS_CONTAINER', value: 'appointments' }
        // Service Bus: Azure Functions MI convention — triggers read SERVICEBUS__fullyQualifiedNamespace,
        // SDK publisher reads the same var via AppContext.
        { name: 'SERVICEBUS__fullyQualifiedNamespace', value: '${sb.name}.servicebus.windows.net' }
        { name: 'SERVICEBUS_CREATED_TOPIC', value: 'appointment-created' }
        { name: 'SERVICEBUS_COMPLETED_TOPIC', value: 'appointment-completed' }
        // SQL: password via Key Vault reference — never stored in plain config
        { name: 'SQL_HOST', value: '${sqlServer.name}.database.windows.net' }
        { name: 'SQL_DATABASE', value: 'clinicdb' }
        { name: 'SQL_USER', value: sqlAdminUser }
        { name: 'SQL_PASSWORD', value: '@Microsoft.KeyVault(VaultName=${kv.name};SecretName=sql-admin-password)' }
      ]
    }
  }
}

// --- Managed Identity role assignments ---

// Cosmos DB Data Contributor — allows the Function App to read/write documents without a key
var cosmosDataContributorRoleId = '00000000-0000-0000-0000-000000000002'
resource cosmosFuncRole 'Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments@2024-05-15' = {
  parent: cosmos
  name: guid(cosmos.id, functionApp.id, cosmosDataContributorRoleId)
  properties: {
    roleDefinitionId: '${cosmos.id}/sqlRoleDefinitions/${cosmosDataContributorRoleId}'
    principalId: functionApp.identity.principalId
    scope: cosmos.id
  }
}

// Service Bus Data Owner — allows publish and consume without a connection string
var sbDataOwnerRoleId = '090c5cfd-751d-490a-894a-3ce6f1109419'
resource sbFuncRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: sb
  name: guid(sb.id, functionApp.id, sbDataOwnerRoleId)
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', sbDataOwnerRoleId)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Key Vault Secrets User — allows the Function App to resolve the KV reference for SQL_PASSWORD
var kvSecretsUserRoleId = '4633458b-17de-408a-b874-0445c86b69e6'
resource kvFuncRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: kv
  name: guid(kv.id, functionApp.id, kvSecretsUserRoleId)
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', kvSecretsUserRoleId)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

output functionAppName string = functionApp.name
output cosmosAccountName string = cosmos.name
output serviceBusNamespace string = sb.name
output sqlServerHost string = '${sqlServer.name}.database.windows.net'
output keyVaultName string = kv.name
