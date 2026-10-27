package co.edu.univalle.fleetops.vehicles.infrastructure.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee {@code X-Correlation-Id} (o genera un UUID si falta o no es un UUID), lo devuelve en la
 * respuesta y lo deja en el MDC como {@code correlation_id} para los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming != null) {
            try {
                return UUID.fromString(incoming.trim()).toString();
            } catch (IllegalArgumentException e) {
                // cabecera inválida: se descarta y se genera una nueva
            }
        }
        return UUID.randomUUID().toString();
    }
}
