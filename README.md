[![CI](https://github.com/apchavez/clean-arch-azure-functions-java/actions/workflows/ci.yml/badge.svg)](https://github.com/apchavez/clean-arch-azure-functions-java/actions/workflows/ci.yml)

# Clinic Scheduling Platform — Azure (Java 21)

Azure migration of the medical appointment platform originally built on AWS ([clean-arch-aws-lambda-typescript](https://github.com/apchavez/clean-arch-aws-lambda-typescript), TypeScript). Same business logic and same Clean Architecture — only the infrastructure adapters change.

> The domain has no knowledge of Azure. What changes between clouds is exclusively the infrastructure layer; use cases and entities remain intact.

> **Zero cost at rest** — CI only compiles and runs tests. No Azure resources are provisioned until the deploy workflow is triggered manually.

---

## Tech Stack

| Category | Technology |
|---|---|
| Language / Runtime | Java 21, Azure Functions v4 |
| State store (NoSQL) | Cosmos DB serverless (Managed Identity) |
| Relational persistence | Azure SQL Database (HikariCP, Flyway) |
| Messaging | Service Bus topics + subscriptions (Managed Identity) |
| Notifications | Azure Communication Services Email |
| Resilience | Resilience4j — circuit breaker + exponential retry |
| IaC | Bicep (subscription-level deployment) |
| Security | Managed Identity, Key Vault references, HTTPS-only |
| Observability | Application Insights, correlation IDs, structured logs |
| API Docs | OpenAPI 3.0 (validated in CI with Redocly) |
| Build / Tests | Maven, JUnit 5, JaCoCo (80% gate on domain + application) |
| CI/CD | GitHub Actions (automatic CI, manual deploy/destroy) |

---

## AWS → Azure Mapping

| AWS (original project) | Azure (this project) |
|---|---|
| AWS Lambda | Azure Functions v4 |
| API Gateway | HTTP trigger (+ optional APIM) |
| DynamoDB | Cosmos DB |
| MySQL / RDS | Azure SQL Database |
| SNS topic | Service Bus topic |
| SQS queue | Service Bus subscription |
| EventBridge | Service Bus topic `appointment-completed` |
| Serverless Framework | Bicep |
| CloudWatch | Application Insights + Log Analytics |

---

## Architecture

Clean Architecture with four well-defined layers:

```
src/main/java/com/clinic/
├── domain/
│   ├── entities/        Appointment, AppointmentEvent, AppointmentStatus, CountryISO
│   ├── ports/           AppointmentStateRepository, AppointmentRelationalRepository,
│   │                    AppointmentEventPublisher, AppointmentEventStore, AppointmentNotifier
│   └── shared/          Page<T>
├── application/
│   └── usecases/        CreateAppointmentUseCase, GetAppointmentsUseCase,
│                        ProcessAppointmentUseCase, CancelAppointmentUseCase,
│                        RescheduleAppointmentUseCase
├── infrastructure/
│   ├── config/          AppContext (composition root), ResilienceConfig
│   ├── messaging/       ServiceBusEventPublisher
│   ├── notifications/   AcsAppointmentNotifier, NoOpAppointmentNotifier
│   └── repos/           CosmosAppointmentStateRepository, CosmosAppointmentEventStore,
│                        AzureSqlAppointmentRepository
└── api/
    └── functions/       HTTP triggers and Service Bus triggers
```

**Dependency rule:** `api` / `infrastructure` → `application` → `domain`  
The domain imports no Azure classes. Tests run entirely in memory, no cloud required.

---

## End-to-End Flow

```
POST /api/appointments
  → CreateAppointmentUseCase
      → Cosmos DB (status PENDING) + event APPOINTMENT_CREATED
      → Service Bus topic "appointment-created"
          → AppointmentWorkerPE / AppointmentWorkerCL
              → ProcessAppointmentUseCase
                  → Cosmos DB (COMPLETED) + event APPOINTMENT_COMPLETED
                  → Azure SQL (final persistence)
                  → ACS Email (notification to insured)
                  → Service Bus topic "appointment-completed"

DELETE /api/appointments/{id}             → CancelAppointmentUseCase     → CANCELLED
PATCH  /api/appointments/{id}/reschedule  → RescheduleAppointmentUseCase → RESCHEDULED + new appointment
GET    /api/appointments/{id}/history     → immutable event log from Cosmos DB
```

---

## Getting Started

Requires [Azure Functions Core Tools v4](https://learn.microsoft.com/azure/azure-functions/functions-run-local) and a Cosmos DB account or emulator.

```bash
# 1. Build
mvn clean package

# 2. Configure variables (copy and edit)
cp local.settings.json.example local.settings.json

# 3. Start
mvn azure-functions:run
```

The function will be available at `http://localhost:7071/api`.

To run only the tests (no cloud, no environment variables):

```bash
mvn clean verify
```

---

## API Endpoints

Base path: `/api`

| Method | Route | Description |
|---|---|---|
| `POST` | `/appointments` | Create appointment (PENDING → Service Bus) |
| `GET` | `/appointments/{insuredId}` | List appointments with cursor-based pagination |
| `DELETE` | `/appointments/{appointmentId}` | Cancel a PENDING appointment |
| `PATCH` | `/appointments/{appointmentId}/reschedule` | Reschedule a PENDING appointment |
| `GET` | `/appointments/{appointmentId}/history` | Immutable event log for an appointment |
| `GET` | `/health` | Status of Cosmos DB, SQL, and Service Bus |

Full contract: [`src/docs/openapi.yaml`](src/docs/openapi.yaml)

---

## Testing

```bash
mvn clean verify
```

Tests run entirely in memory -- no Azure account, no environment variables, and no network connection required.

| Type | Scope | Description |
|---|---|---|
| Unit | Domain & Application | Use cases and entities with plain mocks -- zero Azure dependencies |
| Architecture | All layers | Dependency rule enforced at build time: `api`/`infrastructure` -> `application` -> `domain` |

JaCoCo enforces **>= 80% coverage** on the `domain` and `application` packages. Infrastructure adapters that require live Azure connections are excluded from the threshold.

---

## Deploy

The deploy is **exclusively manual** via GitHub Actions (`workflow_dispatch`). CI never provisions Azure resources.

```
.github/workflows/
├── ci.yml          Push/PR → build, tests, OpenAPI validation   (no Azure cost)
├── deploy.yml      Manual  → Bicep infra + Function App         (incurs cost)
├── destroy.yml     Manual  → deletes the resource group         (stops cost)
└── integration.yml Manual  → Postman tests against live env
```

To deploy to Azure, configure the OIDC environment variables (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`) and the `SQL_ADMIN_PASSWORD` secret in the repository.

---

## What This Project Demonstrates

- Clean Architecture portable across clouds — only the infrastructure adapters change, domain and use cases are identical to the AWS version
- Azure-native services: Cosmos DB event sourcing, Service Bus fan-out, ACS email notifications
- Managed Identity throughout — no hardcoded credentials anywhere in the codebase
- Resilience4j circuit breaker + exponential retry on all external calls
- Cursor-based pagination on Cosmos DB for large result sets
- Bicep IaC at subscription level — full stack provisioned in a single workflow
- OpenAPI contract validated on every CI run with Redocly
- Zero-cost CI design — no Azure resources are created by the CI pipeline

---

## Related Projects

| Project | Description |
|---|---|
| [clean-arch-aws-lambda-typescript](https://github.com/apchavez/clean-arch-aws-lambda-typescript) | The original AWS version — TypeScript, Lambda, DynamoDB, SNS/SQS. Same domain logic, different cloud |
| [spring-webflux-hexagonal-arch](https://github.com/apchavez/spring-webflux-hexagonal-arch) | Java 21 reactive REST API with Spring Boot WebFlux and hexagonal architecture |
