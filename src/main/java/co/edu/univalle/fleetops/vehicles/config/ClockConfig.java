package co.edu.univalle.fleetops.vehicles.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en hora de Colombia.
 * Se inyecta en los services para que las pruebas usen un reloj fijo.
 */
@Configuration
public class ClockConfig {

    /**
     * Reloj del sistema en la zona America/Bogota (GMT-5).
     * @return reloj
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
