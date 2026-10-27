# ── Etapa 1: Build ────────────────────────────────────────────────────────
FROM amazoncorretto:21-alpine AS builder

WORKDIR /app

# Primero los archivos de Gradle: Docker cachea esta capa si no cambian
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY checkstyle.xml .

RUN chmod +x gradlew

# Descarga de dependencias (capa cacheada mientras build.gradle no cambie)
RUN ./gradlew dependencies --no-daemon

# Código fuente y construcción del JAR (los tests ya corrieron en el CI)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test -x checkstyleMain -x checkstyleTest

# ── Etapa 2: Runtime ──────────────────────────────────────────────────────
FROM amazoncorretto:21-alpine

WORKDIR /app

# Usuario sin privilegios: limita el daño si comprometen el contenedor
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8085

# Memoria conservadora para t3.micro y hora de Colombia
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=America/Bogota"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
