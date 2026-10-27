package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj del servicio: todas las fechas se calculan en GMT-5 (America/Bogota). */
@Configuration
public class ClockConfig {

    /**
     * Reloj de pared en Bogotá.
     *
     * @return el reloj inyectado en los casos de uso
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
