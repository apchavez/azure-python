# Clinic Scheduling Platform — Azure (Java 21)

Migración a **Azure** de la plataforma de citas médicas originalmente construida en **AWS** (`clinic-scheduling-platform`, TypeScript). Misma **lógica de negocio** y misma **Clean Architecture**, reescrita en **Java 21** sobre Azure Functions.

> Objetivo: demostrar que los patrones serverless y event-driven son portables entre nubes. Lo que cambia es la capa de infraestructura; el dominio permanece intacto.

---

## Mapeo AWS → Azure

| AWS (proyecto original)   | Azure (este proyecto)                        |
|---------------------------|----------------------------------------------|
| AWS Lambda                | Azure Functions v4                           |
| API Gateway               | HTTP trigger (+ APIM opcional)               |
| DynamoDB (estado)         | Cosmos DB (serverless, Managed Identity)     |
| MySQL (persistencia)      | Azure SQL Database (HikariCP)                |
| SNS (topic)               | Service Bus topic                            |
| SQS (cola)                | Service Bus subscription                     |
| EventBridge               | Service Bus topic `appointment-completed`    |
| Serverless Framework      | Bicep                                        |
| CloudWatch                | Application Insights + Log Analytics         |

---

## Clean Architecture

```
src/main/java/com/clinic/
├── domain/
│   ├── entities/   Appointment, AppointmentStatus, CountryISO
│   └── ports/      AppointmentStateRepository, AppointmentRelationalRepository, AppointmentEventPublisher
├── application/
│   └── usecases/   CreateAppointmentUseCase, GetAppointmentsUseCase, ProcessAppointmentUseCase
├── infrastructure/
│   ├── config/     AppContext (composition root, manual DI)
│   ├── messaging/  ServiceBusEventPublisher (Managed Identity)
│   └── repos/      CosmosAppointmentStateRepository (Managed Identity)
│                   AzureSqlAppointmentRepository (HikariCP)
├── api/
│   └── functions/  CreateAppointmentHandler (HTTP POST)
│                   GetAppointmentsHandler   (HTTP GET)
│                   AppointmentWorkerPE/CL   (Service Bus triggers)
│                   AppointmentWorkerBase    (lógica compartida de workers)
└── shared/
    └── ApiResponse  (helper centralizado de respuestas HTTP)
```

El dominio y los casos de uso **no conocen Azure**: dependen solo de los puertos.
Por eso los tests corren sin nube, con fakes en memoria.

---

## Flujo end-to-end

```
POST /api/appointments
  → CreateAppointmentHandler
    → CreateAppointmentUseCase
      → CosmosDB (PENDING)
      → ServiceBus topic "appointment-created" (subject = countryISO)
        → pe-worker / cl-worker subscription
          → AppointmentWorkerPE / AppointmentWorkerCL
            → ProcessAppointmentUseCase
              → CosmosDB (COMPLETED)
              → Azure SQL (persistencia final)
              → ServiceBus topic "appointment-completed"

GET /api/appointments/{insuredId}
  → GetAppointmentsHandler
    → GetAppointmentsUseCase
      → CosmosDB (query by insuredId)
```

---

## Seguridad

| Recurso       | Autenticación                                       |
|---------------|-----------------------------------------------------|
| Cosmos DB     | Managed Identity (CosmosDBDataContributor role)     |
| Service Bus   | Managed Identity (ServiceBusDataOwner role)         |
| Azure SQL     | Usuario/contraseña; password en Key Vault (KV ref)  |
| Function App  | SystemAssigned identity; HTTPS-only; TLS 1.2 mín.   |

---

## Variables de entorno

| Variable                              | Descripción                                          |
|---------------------------------------|------------------------------------------------------|
| `COSMOS_ENDPOINT`                     | URI del Cosmos DB account                            |
| `COSMOS_DATABASE`                     | Nombre de la base de datos (default: `clinicdb`)     |
| `COSMOS_CONTAINER`                    | Nombre del contenedor (default: `appointments`)      |
| `SERVICEBUS__fullyQualifiedNamespace` | FQNS del namespace (`<ns>.servicebus.windows.net`)   |
| `SERVICEBUS_CREATED_TOPIC`            | Nombre del topic de creación                         |
| `SERVICEBUS_COMPLETED_TOPIC`          | Nombre del topic de completado                       |
| `SQL_HOST`                            | Host del servidor Azure SQL                          |
| `SQL_DATABASE`                        | Nombre de la base de datos SQL                       |
| `SQL_AUTHENTICATION`                  | `SqlPassword` o `ActiveDirectoryManagedIdentity`     |
| `SQL_USER`                            | Usuario administrador SQL                            |
| `SQL_PASSWORD`                        | Contraseña (Key Vault reference en producción)       |
| `ACS_ENDPOINT`                        | URI del recurso Azure Communication Services (opcional; si ausente, notificaciones deshabilitadas) |
| `ACS_SENDER_ADDRESS`                  | Dirección de email verificada para envío (ej. `DoNotReply@<domain>.azurecomm.net`) |

---

## Despliegue

```bash
# 1. Infra (incluye Key Vault, roles de Managed Identity, SQL, Cosmos, Service Bus)
az deployment sub create \
  --location eastus \
  --template-file infra/main.bicep \
  --parameters infra/main.parameters.json \
  --parameters sqlAdminPassword='<password>' \
  --name clinic-dev

# 2. Build y publicación
mvn clean package
mvn azure-functions:deploy
```

---

## Pruebas

```bash
# Ejecutar tests y verificar cobertura (gate: 80% en domain + application)
mvn clean verify

# POST — crear cita
curl -X POST https://<function-app>.azurewebsites.net/api/appointments \
  -H "Content-Type: application/json" \
  -d '{"insuredId":"12345","scheduleId":10,"countryISO":"PE"}'
# -> {"appointmentId":"...","message":"Appointment received","status":"pending"}

# GET — consultar citas de un asegurado
curl https://<function-app>.azurewebsites.net/api/appointments/12345
```

---

## Mejoras respecto al original (AWS)

- `appointmentId` generado con UUID en el caso de uso (no lo recibe el cliente).
- Invariante de negocio: una cita solo se completa desde PENDING (idempotencia).
- Dead-letter queue configurada desde el IaC (`maxDeliveryCount: 5`).
- Validación de entrada en el borde (handler) antes de tocar el dominio.
- Managed Identity para Cosmos y Service Bus — sin secretos en configuración.
- Key Vault reference para SQL password — nunca visible en portal ni en código.
- HikariCP — pool de conexiones SQL (reutilizado entre invocaciones warm).
- Workers PE/CL sin duplicación — `AppointmentWorkerBase` centraliza la lógica.
- Cobertura de tests con JaCoCo (gate 80% en domain/application).
- CI/CD con GitHub Actions en cada push a `master` o `main`.

---

## Future Improvements

Ruta incremental, ordenada de menor a mayor complejidad, para evolucionar el proyecto como pieza de portafolio:

| Estado | Mejora | Objetivo |
|--------|--------|----------|
| Done | Documentar roadmap de mejoras futuras | Mostrar direccion tecnica clara en el portafolio. |
| Done | CI con build, tests y validacion OpenAPI | Garantizar que cada push compile, ejecute pruebas y valide el contrato HTTP. |
| Done | Publicar `openapi.yaml` como artifact de CI | Facilitar revision externa del contrato de API desde GitHub Actions. |
| Done | Observabilidad estructurada | Agregar correlation IDs, logs consistentes y metricas por flujo de cita. |
| Done | Manejo avanzado de retries y dead-letter | Documentar y exponer operacion de fallos de Service Bus. |
| Done | API Management opcional | Agregar una fachada APIM activable para gobierno de API. |
| Done | Managed Identity para Azure SQL | Adaptador JDBC listo para `ActiveDirectoryManagedIdentity`; requiere crear usuario contenido en la base. |
| Done | Soporte escalable para mas paises | Centralizar paises soportados en el dominio para evitar validaciones hardcodeadas. |
| Done | Pruebas de integracion cloud/local | Workflow manual con Newman contra Function App o APIM desplegado. |
| Done | Load testing y performance baselines | Medir cold starts, throughput de Service Bus y uso del pool SQL. |
| Done | Health check endpoint `/api/health` | Verificar conectividad con Cosmos DB, Service Bus y SQL antes de reportar estado; buena practica operativa basica. |
| Done | Pagination con cursor en `GET /appointments` | Usar el continuation token nativo de Cosmos DB para soportar asegurados con muchas citas sin cargar todo en memoria. |
| Done | Casos de uso de cancelacion y reagendado | Agregar `CancelAppointmentUseCase` y `RescheduleAppointmentUseCase` con sus puertos; demuestra que el dominio evoluciona sin tocar infraestructura. |
| Done | Circuit breaker y retry exponencial con Resilience4j | Envolver adaptadores de Cosmos DB y SQL con politica de reintentos y circuit breaker para mayor resiliencia ante fallos transitorios. |
| Done | Notificaciones con Azure Communication Services | Enviar confirmacion al asegurado cuando su cita es completada; introduce un nuevo puerto de notificacion en el dominio sin acoplarlo a ACS. |
| Pending | Event sourcing ligero en Cosmos DB | Guardar cada cambio de estado como un documento de evento (`AppointmentCreated`, `AppointmentCompleted`, `AppointmentCancelled`) para auditar el ciclo de vida completo de una cita. |

---

## Portfolio Enhancements

| Estado | Mejora | Referencia |
|--------|--------|------------|
| Done | Deploy manual por GitHub Actions con OIDC | `.github/workflows/deploy.yml` |
| Done | Ambientes separados `dev`, `test`, `prod` | `infra/parameters.*.json` |
| Done | APIM con throttling y JWT opcional | `infra/core.bicep`, `docs/deployment.md` |
| Done | Alertas operativas opcionales | `infra/core.bicep`, `docs/deployment.md` |
| Done | Workbook de observabilidad | `infra/core.bicep` |
| Done | Hardening de red parametrizable | `allowPublicNetworkAccess` en Bicep |
| Done | Guia operativa y de despliegue | `docs/operations.md`, `docs/deployment.md` |
