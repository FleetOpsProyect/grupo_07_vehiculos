# MS Vehículos — FleetOps (Grupo 7)

Microservicio de Vehículos de FleetOps. Java 21, Spring Boot 3.5.14, Gradle, PostgreSQL 17, Flyway,
JPA, Swagger, JUnit 5, Mockito, Testcontainers, ArchUnit, JaCoCo y Checkstyle, con **arquitectura
hexagonal**. Fuentes de verdad: `docs/contrato_ms_vehiculos.md` (contrato) y `docs/claude.md`
(decisiones y alcance).

## Correr en local

Requisitos: **JDK 21** y un contenedor de PostgreSQL 17.

```bash
docker run -d --name vehiculos_postgres -e POSTGRES_DB=vehiculos_db -e POSTGRES_USER=vehiculos_user \
  -e POSTGRES_PASSWORD=vehiculos_pass -p 3050:5432 postgres:17.5-alpine
./gradlew bootRun             # API en el puerto 8085
```

(`docker-compose.yml` todavía trae `postgres:16`; se corrige en el paso de Docker, ver "Pendiente".)

- Swagger: http://localhost:8085/swagger-ui.html
- Salud: http://localhost:8085/health · Listo: `/ready` · Métricas: `/metrics`
- `application-dev.yml` y `application-test.yml` están en `.gitignore` (Paso 3 del dojo); cada
  integrante conserva el suyo. Las pruebas no los necesitan: usan Testcontainers.
- Si tenían una base local de antes del Sprint 0, bórrenla: `docker rm -f vehiculos_postgres`
  (o `docker compose down -v`). Las migraciones V1–V3 cambiaron y no incluyen `applied_route`
  (se crea en V4 en el Sprint 1).

### Nota sobre el JDK

La toolchain del proyecto es Java 21 (`build.gradle`). Si el JDK por defecto de su máquina no es el
21 (por ejemplo, es el 25 y Gradle 8.14 falla con `Unsupported class file major version`), indiquen
el JDK **en su archivo personal** `~/.gradle/gradle.properties`, nunca en el del proyecto:

```properties
org.gradle.java.home=/ruta/a/su/jdk-21
```

### Nota sobre Podman

Las pruebas con Testcontainers necesitan un socket de Docker. Con Podman:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

## Calidad

```bash
./gradlew check     # Checkstyle + pruebas + JaCoCo (100 % domain/application, 80 % global)
```

Reporte HTML en `build/jacocoHtml/index.html`; CSV en `build/reports/jacoco/test/`.

## Arquitectura

```
co.edu.univalle.fleetops.vehicles
├── domain            Reglas del negocio. Sin Spring, JPA ni Lombok.
│   ├── model         Vehicle, VehicleBlock, VehicleDetails, StatusHistoryEntry y enums
│   ├── rule          VehicleStatusRule (prioridad de estados), VehicleValidationRules
│   └── exception     DomainException y errores con el código del contrato
├── application       Casos de uso. Sin Spring, JPA ni Lombok.
│   ├── port.in       Un puerto de entrada (interfaz) por caso de uso
│   ├── port.out      VehicleRepository, StatusHistoryRepository, DocumentValidityPort,
│   │                 ActiveAssignmentsPort, TransactionPort
│   ├── usecase       Implementaciones (clases simples, sin anotaciones)
│   ├── command/query/result   Entradas y salidas de los casos de uso
│   └── exception     Fallas de los puertos de salida (DocumentServiceUnavailable, ...)
└── infrastructure    Todo lo que usa un framework
    ├── config        UseCaseConfig (composition root), ClockConfig (America/Bogota), OpenApiConfig
    ├── persistence   entity, repository (Spring Data), mapper, adapter (VehicleRepositoryAdapter, ...)
    ├── web           controller, dto, mapper, error (GlobalExceptionHandler), filter (X-Correlation-Id)
    └── client        Clientes HTTP de G1 y G2 (hoy son marcadores PENDIENTES)
```

`HexagonalArchTest` hace cumplir las reglas: `domain` y `application` no dependen de
`infrastructure`, de Spring, de `jakarta.*`, de Hibernate, de Jackson ni de Lombok, y los puertos
son interfaces.

Decisiones que conviene conocer:

- **Estado calculado**: nadie asigna `status` a mano; `Vehicle` lo recalcula con `VehicleStatusRule`
  a partir de los bloqueos.
- **Transacciones**: los casos de uso piden transacción por el puerto `TransactionPort`
  (`SpringTransactionAdapter`). Las llamadas a otros servicios van fuera de la transacción.
- **Bloqueo optimista**: la columna `version` la maneja JPA dentro del adaptador. El dominio no
  conoce la versión (no es el `version_number` de negocio, que es del Sprint 1).
- **Placa única**: índice parcial `ux_vehicle_plate_active`; su violación se traduce en
  `PlateAlreadyExistsException` (409).
- **Errores**: `GlobalExceptionHandler` traduce cada `code` a su HTTP en un solo mapa
  (`STATUS_BY_CODE`). El formato es `{code, message, timestamp, path, errors[]}`, sin `status`.

## Cómo agregar un endpoint

Ejemplo de referencia: `POST /vehicles` (ya hecho). Sigan este orden y no se salten pasos.

**Reglas**: en `domain` y `application` no hay Spring, JPA, Jackson ni Lombok. El controller solo
traduce HTTP ↔ caso de uso: nada de reglas de negocio ni validaciones (eso vive en `domain/rule`).
Todo lo que no esté en `docs/` se marca como "Archetype Convention Addition". Si el contrato es
ambiguo, se pregunta antes de suponer.

1. **Puerto de entrada** — `application/port/in/RegisterVehicleUseCase.java`: una interfaz con un
   solo método `execute`. Su entrada es un comando o consulta (`application/command` o
   `application/query`, ej. `RegisterVehicleCommand`) y su salida un `result`
   (`VehicleResult`). Para los endpoints que faltan, el puerto y el caso de uso **ya existen**
   (`ListVehiclesUseCase`, `UpdateVehicleUseCase`, ...): empiecen en el paso 4.
2. **Caso de uso** — `application/usecase/RegisterVehicleService.java`: clase simple que
   implementa el puerto y recibe por constructor los puertos de salida que necesita. Aquí se
   normaliza, valida (`VehicleValidationRules`), se decide y se persiste dentro de
   `transaction.execute(...)`. Los errores son excepciones de `domain.exception` (o de
   `application.exception` si fallan puertos externos). Si necesitan algo nuevo del exterior,
   agreguen un puerto de salida (interfaz) en `application/port/out` y su adaptador en
   `infrastructure`.
3. **Bean en el composition root** — `infrastructure/config/UseCaseConfig.java`: un método
   `@Bean` que hace `new RegisterVehicleService(...)`. Los 7 casos de uso ya tienen bean.
4. **Controller** — `infrastructure/web/controller/VehicleController.java`: inyecten el
   **puerto de entrada** (no la clase). Convierta DTO → comando, llame a `execute` y convierta el
   resultado → DTO (`VehicleWebMapper`). Agreguen `@Operation` y `@ApiResponse` para Swagger. Los
   errores no se atrapan aquí: `GlobalExceptionHandler` ya los convierte. Si el endpoint añade un
   código de error nuevo, agréguenlo a `STATUS_BY_CODE`.
5. **DTO** — `infrastructure/web/dto/CreateVehicleRequest.java` y `VehicleResponse.java`: `record`
   en camelCase; Jackson los publica en snake_case (`vehicle_type`). Las fechas de respuesta salen
   en GMT-5 (`VehicleWebMapper`). Los ids de ruta se reciben como `String` y, si no son UUID,
   responden 404 (`parseId` en el controller). Los campos no editables (`NOT_EDITABLE`) los
   detecta la capa REST.
6. **Prueba unitaria** — `src/test/.../application/usecase/RegisterVehicleServiceTest.java`:
   patrón AAA, Mockito para los puertos de salida y `PassThroughTransactionPort` como transacción.
   Cubran **todas** las ramas: `domain` y `application` exigen 100 % de líneas y ramas.
7. **Prueba de integración** — `src/test/.../infrastructure/web/VehicleApiIntegrationTest.java`:
   hereda de `PostgresIntegrationTest` (PostgreSQL 17 con Testcontainers, Flyway aplicado, tablas
   limpias en cada prueba) y usa `MockMvc` para verificar código HTTP, `code` y la forma del JSON
   del contrato.

Al terminar: `./gradlew check` en verde.

## Estado del Sprint 0

Hecho: dominio y casos de uso (7), persistencia JPA con Flyway V1–V3, `POST /vehicles`,
`GET /vehicles/{id}`, manejador de errores, filtro `X-Correlation-Id`, Swagger, `/health`,
`/ready`, `/metrics`.

## Pendiente del Sprint 0

- [ ] **Endpoints restantes** (puerto y caso de uso ya existen; falta controller, DTO y pruebas):
  `GET /vehicles` (con `summary`), `GET /vehicles?valid_until=` (aptos), `PUT /vehicles/{id}`,
  `PUT /vehicles/{id}/status`, `DELETE /vehicles/{id}`.
- [ ] **Clientes de G1 y G2**: reemplazar `PendingDocumentValidityClient` (G1,
  `GET /documents/validity-check`, lotes de 100) y `PendingAssignmentsClient` (G2,
  `GET /assignment/active`) por clientes HTTP con timeout de 2 s que lancen
  `DocumentServiceUnavailableException` / `AssignmentServiceUnavailableException` (503). Hoy esos
  marcadores responden 503 siempre. `infrastructure/client` está excluido de JaCoCo hasta entonces.
- [ ] **Docker**: `Dockerfile`, `docker-compose.yml` (PostgreSQL 17 + API), `docker-compose.prod.yml`,
  `.env.example`; imágenes con versión exacta, nunca `latest`. Corregir `postgres:16-alpine` del
  compose actual.
- [ ] **CI/CD**: `ci.yml`, `cd.yml`, `test-ssh.yml`, `deploy.sh`.
- [ ] **SonarCloud**: reemplazar `TU-ORGANIZACION` en `build.gradle` y ajustar las exclusiones de
  cobertura a la estructura hexagonal.
- [ ] **Despliegue en EC2** y checklist C01–C12 del dojo.
- [ ] Mapear `ObjectOptimisticLockingFailureException` a 409 si el equipo lo decide.

## Pendiente para el Sprint 1

Outbox, SNS, SQS, idempotencia por `event_id`, DLQ, LocalStack, casos de uso de eventos,
`version_number` de negocio y la tabla `applied_route` (V4).
