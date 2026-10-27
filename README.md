# MS Vehículos — FleetOps (Grupo 7)

Microservicio de Vehículos de FleetOps, construido siguiendo los pasos del dojo de Desarrollo de
Software III con el mismo stack: Java 21, Spring Boot 3.5.14, Gradle, Lombok, JPA, Flyway, Swagger,
JUnit 5, Mockito, Testcontainers, JaCoCo, Checkstyle, Docker Compose, GitHub Actions, Docker Hub,
EC2 y SonarCloud.

## Correr en local

Requisitos: JDK 21 y Docker Desktop encendido.

```bash
docker compose up -d          # PostgreSQL en el puerto 3050
./gradlew bootRun             # API en el puerto 8085
```

- Swagger: http://localhost:8085/swagger-ui.html
- Salud: http://localhost:8085/health · Listo: http://localhost:8085/ready · Métricas: http://localhost:8085/metrics

`application-dev.yml` y `application-test.yml` están en `.gitignore` (Paso 3 del dojo). Vienen en el
zip para que el proyecto arranque; cada integrante los conserva en su máquina.

## Calidad

```bash
./gradlew checkstyleMain checkstyleTest   # estilo
./gradlew test jacocoTestReport           # pruebas + reporte en build/jacocoHtml/index.html
./gradlew check                           # todo, incluida cobertura >= 80 %
```

## Endpoints

| Método y ruta | Quién lo usa | Respuestas |
| --- | --- | --- |
| `POST /vehicles` | Administrador de Flota | 201, 400, 409 |
| `GET /vehicles/{id}` | Administrador, Incidentes, Documentación, Mantenimiento | 200, 400, 404 |
| `GET /vehicles?status=&type=&page=&size=` | Mantenimiento (scheduler), Reportes | 200 con `summary`, 400 |
| `GET /vehicles?type=&valid_until=AAAA-MM-DD` | Asignaciones (aptos) | 200 sin paginar (máx. 500), 400, 503 |
| `PUT /vehicles/{id}/status` | Mantenimiento (scheduler) | 200, 400, 404, 409, 422 |
| `PUT /vehicles/{id}` | Administrador de Flota | 200, 400, 404, 409 |
| `DELETE /vehicles/{id}` | Administrador de Flota | 200, 404, 409, 503 |
| `GET /health`, `GET /ready`, `GET /metrics` | Infraestructura, Prometheus | 200, 503 |

JSON en snake_case, ids UUID y estados en inglés (ver equivalencias en `VehicleStatus`).

## Estructura (capas del dojo)

```
co.edu.univalle.fleetops.vehicles
├── controller   VehicleController
├── service      VehicleService (REST), VehicleStatusService (eventos), VehicleValidator,
│                StatusHistoryRecorder, VehicleStatusRules (derivación del estado)
├── repository   VehicleRepository, StatusHistoryRepository, AppliedRouteRepository
├── model        VehicleModel, VehicleBlockModel, StatusHistoryModel, AppliedRouteModel + enums
├── dto          peticiones y respuestas de la API
├── client       DocumentClient (G1), AssignmentClient (G2)
├── exception    ApiErrorResponse, excepciones y GlobalExceptionHandler
└── config       ClockConfig
```

## Pendiente para el Sprint 1

- Consumidores del broker (cuando se decida RabbitMQ, SNS/SQS o Kafka) que llamen a
  `VehicleStatusService`, y publicación de eventos con Outbox.
- Circuit breaker en `DocumentClient` y `AssignmentClient`.
- Confirmar con G1 el nombre del campo de respuesta de `validar-vigencia` y con G2 el formato
  de `GET /assignment/active`.
