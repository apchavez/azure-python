// ============================================================================
//  main.bicep — Minimal serverless infra for the "createAppointment" flow.
//  Provisions: Cosmos DB (state), Service Bus (events), Storage + Function App,
//  Application Insights, Key Vault. Mirrors the AWS stack's core, on Azure.
//
//  Deploy:
//    az deployment sub create --location eastus \
//      --template-file infra/main.bicep --parameters infra/main.parameters.json
// ============================================================================

targetScope = 'subscription'

param projectName string = 'clinic'
param location string = 'eastus'
@allowed([ 'dev', 'test', 'prod' ])
param environment string = 'dev'

@description('Administrator password for the Azure SQL. Pass at deploy time, do not commit.')
@secure()
param sqlAdminPassword string

@description('Region for the App Service plan + Function App (separate due to per-region App Service quota).')
param appLocation string = 'centralus'

var tags = {
  project: projectName
  environment: environment
  managedBy: 'bicep'
}
var rgName = 'rg-${projectName}-${environment}'
var suffix = uniqueString(subscription().subscriptionId, projectName, environment)

resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: rgName
  location: location
  tags: tags
}

module core 'core.bicep' = {
  scope: rg
  name: 'core'
  params: {
    projectName: projectName
    environment: environment
    location: location
    tags: tags
    suffix: suffix
    sqlAdminPassword: sqlAdminPassword
    appLocation: appLocation
  }
}

output resourceGroup string = rg.name
output functionAppName string = core.outputs.functionAppName
output cosmosAccountName string = core.outputs.cosmosAccountName
output serviceBusNamespace string = core.outputs.serviceBusNamespace
output sqlServerHost string = core.outputs.sqlServerHost
output keyVaultName string = core.outputs.keyVaultName
