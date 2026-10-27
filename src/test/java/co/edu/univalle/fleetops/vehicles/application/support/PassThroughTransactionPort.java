package co.edu.univalle.fleetops.vehicles.application.support;

import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import java.util.function.Supplier;

/** Transacción de prueba: ejecuta la acción directamente. */
public class PassThroughTransactionPort implements TransactionPort {

    @Override
    public <T> T execute(Supplier<T> action) {
        return action.get();
    }
}
