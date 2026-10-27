package co.edu.univalle.fleetops.vehicles;

import co.edu.univalle.fleetops.vehicles.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

/** Test de carga del contexto de Spring Boot. */
class VehiclesApplicationTests extends BaseIntegrationTest {

    /** Verifica que el contexto arranca: Flyway, mapeo JPA y configuración. */
    @Test
    void contextLoads() {
        // Si el contexto no arranca, el test falla
    }
}
