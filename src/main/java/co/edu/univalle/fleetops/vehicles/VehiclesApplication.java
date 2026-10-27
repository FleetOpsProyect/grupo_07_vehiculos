package co.edu.univalle.fleetops.vehicles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada del microservicio de Vehículos (Grupo 7) de FleetOps. */
@SpringBootApplication
public class VehiclesApplication {

    /**
     * Arranca la aplicación Spring Boot.
     * @param args argumentos de línea de comandos
     */
    public static void main(final String[] args) {
        SpringApplication.run(VehiclesApplication.class, args);
    }
}
