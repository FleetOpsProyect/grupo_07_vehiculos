package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Implementa {@link TransactionPort} con {@link TransactionTemplate}: revierte si la acción lanza. */
@Component
public class SpringTransactionAdapter implements TransactionPort {

    private final TransactionTemplate template;

    /**
     * Crea el adaptador.
     *
     * @param transactionManager administrador de transacciones de Spring
     */
    public SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
