package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
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
        return new OpenAPI().info(new Info().title("MS Vehículos (G7) - FleetOps").version("1.0.0")
                .description("Dueño del parque automotor: datos, estado operativo y odómetro oficial."));
    }

    /**
     * Fuerza a Swagger a documentar los esquemas usando la estrategia snake_case de Jackson.
     *
     * @param objectMapper mapper configurado por Spring Boot
     * @return resolvedor de modelos para OpenAPI
     */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }
}