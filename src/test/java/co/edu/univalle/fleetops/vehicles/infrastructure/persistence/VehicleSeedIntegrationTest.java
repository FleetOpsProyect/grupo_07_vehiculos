package co.edu.univalle.fleetops.vehicles.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleValidationRules;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica la carga inicial de {@code db/seed} contra PostgreSQL real. La base de pruebas no la aplica
 * por defecto, así que cada prueba ejecuta el script sobre las tablas vacías.
 */
@AutoConfigureMockMvc
class VehicleSeedIntegrationTest extends PostgresIntegrationTest {

    private static final ClassPathResource SEED = new ClassPathResource("db/seed/R__carga_inicial_vehiculos.sql");
    private static final String SEED_ROWS_SQL = "SELECT plate, vehicle_type, brand, model, vehicle_year, color, "
            + "notes, odometer_km FROM vehicle";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;

    private record SeedRow(String plate, VehicleType type, VehicleDetails details, BigDecimal odometerKm) {
    }

    private void runSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(SEED);
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }

    private static SeedRow toSeedRow(ResultSet rs) throws SQLException {
        VehicleDetails details = new VehicleDetails(rs.getString("brand"), rs.getString("model"),
                rs.getInt("vehicle_year"), rs.getString("color"), rs.getString("notes"));
        return new SeedRow(rs.getString("plate"), VehicleType.valueOf(rs.getString("vehicle_type")), details,
                rs.getBigDecimal("odometer_km"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seed_loads50ActiveVehiclesWithTheExpectedMixAndTheirCreationHistory() {
        runSeed();

        assertThat(count("SELECT count(*) FROM vehicle")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM vehicle WHERE vehicle_type = ?", "CAR")).isEqualTo(20);
        assertThat(count("SELECT count(*) FROM vehicle WHERE vehicle_type = ?", "PICKUP_TRUCK")).isEqualTo(20);
        assertThat(count("SELECT count(*) FROM vehicle WHERE vehicle_type = ?", "SPECIALIZED_VEHICLE"))
                .isEqualTo(10);
        assertThat(count("SELECT count(*) FROM vehicle WHERE status = 'ACTIVE' AND deleted = FALSE")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM status_history WHERE cause = 'VEHICLE_CREATED' "
                + "AND from_status IS NULL AND to_status = 'ACTIVE'")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM vehicle_block")).isZero();
    }

    @Test
    void seed_everyVehiclePassesTheSameRulesAsPostVehicles() {
        runSeed();
        int currentYear = Year.now().getValue();

        List<SeedRow> rows = jdbc.query(SEED_ROWS_SQL, (rs, rowNum) -> toSeedRow(rs));

        assertThat(rows).hasSize(50);
        for (SeedRow row : rows) {
            assertThatCode(() -> VehicleValidationRules.validateNewVehicle(row.plate(), row.type(),
                    row.details(), row.odometerKm(), currentYear))
                    .as("vehículo %s", row.plate())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void seed_runTwice_doesNotDuplicateVehiclesOrHistory() {
        runSeed();
        runSeed();

        assertThat(count("SELECT count(*) FROM vehicle")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM status_history")).isEqualTo(50);
    }

    @Test
    void seed_isListedByGetVehicles() throws Exception {
        runSeed();

        mvc.perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(50))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.summary.ACTIVE").value(50));
    }
}
