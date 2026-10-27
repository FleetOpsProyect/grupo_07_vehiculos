package co.edu.univalle.fleetops.vehicles.integration;

import co.edu.univalle.fleetops.vehicles.client.AssignmentClient;
import co.edu.univalle.fleetops.vehicles.client.DocumentClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Clase base de los tests de integración.
 * Levanta UN SOLO contenedor PostgreSQL para toda la suite (versión final del Paso 10)
 * y simula Documentación y Asignaciones, que no existen en el ambiente de pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    // static final: un único contenedor compartido por todas las clases de test
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("vehiculos_test")
                .withUsername("test_user")
                .withPassword("test_pass");
        POSTGRES.start();
    }

    // Reemplazan los clientes HTTP reales por mocks de Mockito en el contexto de Spring
    @MockitoBean
    protected DocumentClient documentClient;

    @MockitoBean
    protected AssignmentClient assignmentClient;

    // Sobrescribe la conexión del application.yml con la del contenedor (puerto aleatorio)
    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
