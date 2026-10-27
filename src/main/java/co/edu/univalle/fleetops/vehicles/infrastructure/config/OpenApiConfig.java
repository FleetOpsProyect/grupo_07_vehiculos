package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de Swagger (UI en {@code /swagger-ui.html}). */
@Configuration
public class OpenApiConfig {

    /**
     * Describe la API de Vehículos.
     *
     * @return la definición base de OpenAPI
     */
    @Bean
    public OpenAPI vehiclesOpenApi() {
        return new OpenAPI().info(new Info().title("MS Vehículos (G7) — FleetOps").version("1.0.0")
                .description("Dueño del parque automotor: datos, estado operativo y odómetro oficial."));
    }
}
