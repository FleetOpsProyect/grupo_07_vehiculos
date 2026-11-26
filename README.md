# MS Vehículos — FleetOps (Grupo 7)

Microservicio de Vehículos de FleetOps. Java 21, Spring Boot 3.5.14, Gradle, PostgreSQL 17, Flyway,
JPA, Swagger, JUnit 5, Mockito, Testcontainers, ArchUnit, JaCoCo y Checkstyle, con **arquitectura
hexagonal**.

## Correr en local

Requisitos: **JDK 21** y Docker (o Podman).

```bash
cp .env.example .env          # una sola vez; ponga su DB_PASS (el mismo de su application-dev.yml)
docker compose up -d          # solo PostgreSQL 17.5 en el puerto 3050
./gradlew bootRun             # API en el puerto 8085
```

Para correr también la API dentro de un contenedor (imagen `ms-vehiculos:local`):

```bash
docker compose --profile app up -d --build
docker compose --profile app down        # apagar (agregue -v para borrar la base)
```

- Swagger: http://localhost:8085/swagger-ui.html
- Listo: http://localhost:8085/ready. `/health` y `/metrics` van en el puerto 8085 en local y en el
  **18001** en producción (`MANAGEMENT_SERVER_PORT`); `/ready` siempre está en el 8085.
- `docker compose up -d` levanta PostgreSQL (puerto 3050) y **LocalStack** (SNS y SQS, puerto 4566) con el
  topic `fleetops_vehicle_events` y las colas `vehicles_queue` y `vehicles_queue_dlq` ya creados. Sin
  LocalStack, apaguen la mensajería con `FLEETOPS_MESSAGING_ENABLED=false` (ver «Mensajería»).
- `application-dev.yml` y `application-test.yml` están en `.gitignore` (Paso 3 del dojo); cada
  integrante conserva el suyo. Las pruebas no los necesitan: usan Testcontainers. El contenedor
  de la API tampoco: recibe la conexión por variables de entorno.
- Si tenían una base local de antes del Sprint 0 (PostgreSQL 16 o migraciones viejas), bórrenla:
  `docker compose down -v`. Las migraciones V1–V3 cambiaron y no incluyen `applied_route` (se crea
  en V5 en una HU posterior; V4 es el outbox), y un volumen de PostgreSQL 16 no arranca con la imagen 17.

### Nota sobre el JDK

La toolchain del proyecto es Java 21 (`build.gradle`). Si el JDK por defecto de su máquina no es el
21 (por ejemplo, Gradle falla con `Unsupported class file major version`), indiquen el JDK **en su
archivo personal** `~/.gradle/gradle.properties`, nunca en el del proyecto:

```properties
org.gradle.java.home=/ruta/a/su/jdk-21
```

### Nota sobre Podman

Las pruebas con Testcontainers y `docker compose` necesitan un socket de Docker. Con Podman:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

Podman exige nombres de imagen completos: para construir el `Dockerfile` (usa `amazoncorretto:...`
sin registro) agregue `unqualified-search-registries = ["docker.io"]` a
`~/.config/containers/registries.conf`. `HEALTHCHECK` solo funciona con `podman build --format docker`.
El volumen de LocalStack lleva la etiqueta `:z` para que SELinux (Fedora) deje leer el script que crea el
topic y las colas; Docker la ignora.

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
│   └── exception     DomainException y errores con su código de error de la API
├── application       Casos de uso. Sin Spring, JPA ni Lombok.
│   ├── port.in       Un puerto de entrada (interfaz) por caso de uso, e IncomingEventHandler
│   ├── port.out      VehicleRepository, StatusHistoryRepository, DocumentValidityPort,
│   │                 ActiveAssignmentsPort, TransactionPort y los de mensajería: EventOutbox,
│   │                 OutboxQueue, EventPublisher, ProcessedEventRepository
│   ├── usecase       Implementaciones (clases simples, sin anotaciones)
│   ├── event         PendingEvent e IncomingEvent (mensajes sin formato de transporte)
│   ├── command/query/result   Entradas y salidas de los casos de uso
│   └── exception     Fallas de los puertos de salida (DocumentServiceUnavailable, ...)
└── infrastructure    Todo lo que usa un framework
    ├── config        UseCaseConfig (composition root), ClockConfig (America/Bogota), OpenApiConfig,
    │                 AwsMessagingConfig y MessagingProperties (SNS/SQS)
    ├── persistence   entity, repository (Spring Data), mapper, adapter (VehicleRepositoryAdapter,
    │                 JdbcOutboxAdapter, JdbcProcessedEventAdapter)
    ├── messaging     Sobre del evento y su JSON, SnsEventPublisher, OutboxPublisherJob,
    │                 SqsQueueListener y MessagingMetrics
    ├── web           controller, dto, mapper, error (GlobalExceptionHandler), filter (X-Correlation-Id)
    └── client        Clientes HTTP de G1 y G2 (hoy son marcadores PENDIENTES)
```

`HexagonalArchTest` hace cumplir las reglas: `domain` y `application` no dependen de
`infrastructure`, de Spring, de `jakarta.*`, de Hibernate, de Jackson, de Lombok, del SDK de AWS ni de
Micrometer, y los puertos son interfaces.

Decisiones que conviene conocer:

- **Estado calculado**: nadie asigna `status` a mano; `Vehicle` lo recalcula con `VehicleStatusRule`
  a partir de los bloqueos.
- **Transacciones**: los casos de uso piden transacción por el puerto `TransactionPort`
  (`SpringTransactionAdapter`). Las llamadas a otros servicios van fuera de la transacción.
- **Bloqueo optimista**: la columna `version` la maneja JPA dentro del adaptador. El dominio no
  conoce la versión (no es el `version_number` de negocio, que llega con los eventos de negocio).
- **Placa única**: índice parcial `ux_vehicle_plate_active`; su violación se traduce en
  `PlateAlreadyExistsException` (409).
- **Errores**: `GlobalExceptionHandler` traduce cada `code` a su HTTP en un solo mapa
  (`STATUS_BY_CODE`). El formato es `{code, message, timestamp, path, errors[]}`, sin `status`.

## Mensajería (SNS y SQS)

HU-13 deja lista la infraestructura. Ningún caso de uso publica todavía eventos de negocio ni hay
consumidores reales (ver «Pendiente de mensajería»).

```
caso de uso ──(misma transacción)──▶ outbox_events (PENDING)
                                          │ cada 2 s: hasta 50, del más antiguo, FOR UPDATE SKIP LOCKED
                                          ▼
                                 OutboxPublisherJob ──▶ SNS fleetops_vehicle_events (atributo event_type)
                                                        └─ si falla: attempts+1, last_error, sigue PENDING

SQS vehicles_queue ──▶ SqsQueueListener ──▶ ProcessIncomingEventService (una transacción):
        │                  processed_events (event_id) + IncomingEventHandler + EventOutbox
        └─ error: no se borra; al 3.er fallo pasa a vehicles_queue_dlq
```

- **Publicar un evento**: el caso de uso llama a `EventOutbox.record(tipo, vehicleId, data)` dentro de su
  `TransactionPort`. El adaptador arma el sobre del curso (`event_id` nuevo, `occurred_at` en GMT-5,
  `source = vehicle`, `correlation_id` del `X-Correlation-Id` o del mensaje que originó el cambio) y lo
  guarda como `PENDING`. Si la transacción se revierte, el evento no queda.
- **Consumir un evento**: se implementa `IncomingEventHandler` (un `eventType()` y un `handle`) y se
  registra como bean. Corre dentro de la transacción del consumo: si lanza una excepción no queda nada y el
  mensaje se reintenta. Un `event_id` repetido se borra sin ejecutar nada; un `event_type` sin manejador se
  registra en el log y se borra.
- **Entrega al menos una vez y sin orden**: por eso existe `processed_events` (el `event_id` es texto porque
  G1 no usa UUID) y los suscriptores deben descartar eventos con `version_number` menor o igual al último.

| Variable | Qué hace |
| --- | --- |
| `FLEETOPS_MESSAGING_ENABLED` | `false` apaga el publicador y el listener (por defecto `true`) |
| `SNS_TOPIC_ARN` | ARN de `fleetops_vehicle_events`; vacío = no publica |
| `SQS_QUEUE_URL` | URL de `vehicles_queue`; vacía = no consume |
| `AWS_REGION` | Región (por defecto `us-east-1`) |
| `FLEETOPS_MESSAGING_ENDPOINT` | Solo local: URL de LocalStack (`http://localhost:4566`) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Solo local: cualquier valor (por ejemplo `test`); el compose ya los pone. En el EC2 no se definen: el SDK usa el rol de la instancia |

Para correr la API con `./gradlew bootRun` contra el LocalStack del compose:

```bash
export FLEETOPS_MESSAGING_ENDPOINT=http://localhost:4566 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
export SNS_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:fleetops_vehicle_events
export SQS_QUEUE_URL=http://localhost:4566/000000000000/vehicles_queue
./gradlew bootRun
```

La API no crea topics ni colas. En local los crea `localstack/init/ready.d/01-create-resources.sh`; en AWS
deben existir antes (topic estándar, cola con `maxReceiveCount = 3` hacia `vehicles_queue_dlq` y
suscripciones con *raw message delivery*). El rol del EC2 necesita `sns:Publish` sobre el topic y
`sqs:ReceiveMessage`, `sqs:DeleteMessage` y `sqs:GetQueueAttributes` sobre la cola. La disponibilidad de
SNS no forma parte de `/ready`: si SNS cae, la API sigue respondiendo y los eventos quedan en el outbox.

**Métricas** (Prometheus las lee en `GET /metrics`, puerto 18001 en producción): `vehicles_outbox_pending`
(gauge), `vehicles_outbox_published_total`, `vehicles_messages_consumed_total` y
`vehicles_messaging_errors_total{operation="publish|consume"}`.

**Verificar en el EC2 con un evento de prueba** (hasta que exista un evento de negocio real):

```bash
docker exec -i vehiculos_postgres_prod psql -U "$DB_USER" -d "$DB_NAME" <<'SQL'
INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload, status, attempts, created_at)
VALUES (gen_random_uuid(), 'vehicle_test_event', gen_random_uuid(),
        jsonb_build_object('event_id', gen_random_uuid(), 'event_type', 'vehicle_test_event',
          'occurred_at', to_char(now() AT TIME ZONE 'America/Bogota', 'YYYY-MM-DD"T"HH24:MI:SS') || '-05:00',
          'source', 'vehicle', 'correlation_id', gen_random_uuid(), 'data', '{}'::jsonb),
        'PENDING', 0, now());
SELECT event_type, status, attempts, last_error FROM outbox_events ORDER BY created_at DESC LIMIT 3;
SQL
```

En 2 s el evento debe pasar a `PUBLISHED` (si no, `last_error` dice por qué). Memoria: el cliente de AWS y el
listener suman memoria en el t3.micro; revisen el uso en Grafana después del despliegue.

## Cómo agregar un endpoint

Ejemplos de referencia: `POST /vehicles` (alta) y `PUT /vehicles/{id}/status` (HU-04, con body que
se valida en el mapper). Sigan este orden y no se salten pasos.

**Reglas**: en `domain` y `application` no hay Spring, JPA, Jackson ni Lombok. El controller solo
traduce HTTP ↔ caso de uso: nada de reglas de negocio ni validaciones (eso vive en `domain/rule`).

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
   en GMT-5 (`VehicleWebMapper`). Los ids de ruta se reciben como `String` y, si no son un UUID
   canónico (`VehicleWebMapper.parseUuid`, también para los UUID del body), responden 404 (`parseId`
   en el controller). Los campos no editables (`NOT_EDITABLE`) los
   detecta la capa REST. Si el body trae campos que hay que validar todos a la vez, el DTO los
   recibe como `String` y el mapper los convierte y lanza un solo `ValidationException` con todos
   los errores (ver `ChangeStatusRequest` y `VehicleWebMapper.toCommand`).
6. **Prueba unitaria** — `src/test/.../application/usecase/RegisterVehicleServiceTest.java`:
   patrón AAA, Mockito para los puertos de salida y `PassThroughTransactionPort` como transacción.
   Cubran **todas** las ramas: `domain` y `application` exigen 100 % de líneas y ramas.
7. **Prueba de integración** — `src/test/.../infrastructure/web/VehicleApiIntegrationTest.java`:
   hereda de `PostgresIntegrationTest` (PostgreSQL 17 con Testcontainers, Flyway aplicado, tablas
   limpias en cada prueba) y usa `MockMvc` para verificar código HTTP, `code` y la forma del JSON
   esperada. Una placa distinta por prueba. Ver también
   `VehicleMaintenanceApiIntegrationTest` (PUT) y `VehicleWebMapperTest` (validación del body).

Al terminar: `./gradlew check` en verde.

## CI/CD y despliegue

Flujo de ramas (dojo, Paso 13): rama personal → PR a `dev` (corre el CI) → PR de `dev` a `main`
(al fusionar corre el CD). Nadie hace push directo a `dev` ni a `main`.

| Workflow | Cuándo | Qué hace |
| --- | --- | --- |
| `ci.yml` | PR hacia `dev` o `main`, y push a `dev` o `main` | Job **Build, Test y Calidad**: `./gradlew check` (Checkstyle, pruebas unitarias, ArchUnit, integración con Testcontainers y cobertura 100 %/80 %), `bootJar` y reporte JaCoCo. Job **SonarCloud Analysis**: `./gradlew sonar`; el push a `dev` y `main` deja la base con la que se comparan los PR. |
| `cd.yml` | push a `main` (merge del PR) | Construye la imagen (el `Dockerfile` compila el JAR), la sube a Docker Hub con el tag del **SHA del commit** (nunca `latest`), copia `docker-compose.prod.yml` y `deploy.sh` al EC2 y ejecuta `./deploy.sh <sha>`. |
| `test-ssh.yml` | manual | Verifica la conexión SSH al EC2 antes del primer despliegue. Bórrenlo cuando ya funcione. |

`deploy.sh` levanta la imagen, espera hasta 90 s a que `GET /ready` responda y, si no responde,
vuelve a la versión anterior (guardada en `~/app/.deployed_tag`) y falla el job. Flyway aplica las
migraciones nuevas al arrancar; el pipeline no necesita lógica extra.

**Secrets de GitHub** (Settings → Secrets and variables → Actions): `DOCKERHUB_USERNAME`,
`DOCKERHUB_TOKEN`, `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` y `SONAR_TOKEN`. En el EC2, `~/app/.env`
se crea desde `.env.example` (sin `IMAGE_TAG`: lo pasa `deploy.sh`).

Despliegue manual o rollback a mano desde el EC2: `cd ~/app && ./deploy.sh <sha_anterior>`.

## Estado

Hecho: dominio y casos de uso, persistencia JPA con Flyway V1–V4, `POST /vehicles` (HU-01),
`GET /vehicles/{id}` (HU-02), `GET /vehicles` con filtros y `summary` (HU-03), `PUT /vehicles/{id}/status`
(HU-04: paso a IN_MAINTENANCE, idempotente por `maintenance_id`), `PUT /vehicles/{id}` (HU-11), manejador de
errores, filtro `X-Correlation-Id`, Swagger, `/health`, `/ready` y `/metrics` (en el puerto 18001 en
producción), Docker, los workflows de CI/CD, el EC2 y la infraestructura de mensajería (HU-13). Si la base de
datos no responde, cualquier endpoint responde 503 `SERVICE_UNAVAILABLE` y registra el fallo con nivel ERROR.

## Pendiente

- [ ] **Endpoints restantes**: `GET /vehicles?valid_until=` (aptos; caso de uso y puerto ya existen) y
  `DELETE /vehicles/{id}`.
- [ ] **Clientes de G1 y G2**: reemplazar `PendingDocumentValidityClient` (G1,
  `GET /documents/validity-check`, lotes de 100) y `PendingAssignmentsClient` (G2,
  `GET /assignment/active`) por clientes HTTP con timeout de 2 s que lancen
  `DocumentServiceUnavailableException` / `AssignmentServiceUnavailableException` (503). Hoy esos
  marcadores responden 503 siempre. `infrastructure/client` está excluido de JaCoCo hasta entonces.
- [ ] **SonarCloud**: la organización, la clave del proyecto y `SONAR_TOKEN` ya están configurados.
  Falta ajustar `sonar.coverage.exclusions` en `build.gradle` a la estructura hexagonal: hoy excluye
  `**/model/**` y `**/exception/**`, que ahora son del dominio y sí deben medirse.
- [ ] **Cierre del Paso 11**: proteger las ramas `dev` y `main` en GitHub y el checklist C01–C12.
- [ ] **Recursos de AWS**: acordar con el curso quién crea el topic, las colas y las suscripciones, y
  configurar `SNS_TOPIC_ARN` y `SQS_QUEUE_URL` en el `.env` del EC2.

## Pendiente de mensajería

HU-13 dejó la tubería; falta conectarla:

- `version_number` de negocio (el alta lo publica en 0) y la tabla `applied_route` (V5).
- Publicar `vehicle_created_event`, `vehicle_updated_event`, `vehicle_deleted_event`,
  `vehicle_status_changed_event` y `vehicle_odometer_updated_event` desde los casos de uso con `EventOutbox`.
- Los consumidores del contrato (sección 5), cada uno como `IncomingEventHandler`.

## Deuda técnica

- **Concurrencia**: el adaptador de persistencia traduce el bloqueo optimista y el bloqueo duplicado
  a `ConcurrentVehicleUpdateException` (sin Spring). Solo `ChangeToMaintenanceService` reintenta una
  vez en una transacción nueva; si el reintento también choca, `GlobalExceptionHandler` lo responde
  como 503 con un log WARN. `UpdateVehicleService` y `DeleteVehicleService` no reintentan: ante un
  conflicto responden 503. Evaluar reintento o 409 en una HU futura.
- **Publicador del outbox**: una vuelta mantiene el bloqueo `SKIP LOCKED` (y una conexión de Hikari) mientras
  publica el lote; con SNS caído y el tiempo de espera de 2 s, una vuelta de 50 eventos puede tardar más de
  un minuto. Un evento que falla siempre se reintenta primero en cada vuelta: muchos eventos inválidos
  podrían retrasar a los demás. Evaluar un máximo de intentos o un estado FAILED.
