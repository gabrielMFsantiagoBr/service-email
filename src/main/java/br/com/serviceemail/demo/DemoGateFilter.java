package br.com.serviceemail.demo;

import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Desliga tudo em /demo quando {@code demo.enabled=false} (padrão fora do modo dev).
 * A página deixa qualquer visitante criar contas e disparar emails, então não pode
 * ficar exposta em produção.
 */
@Singleton
public class DemoGateFilter {

    @ConfigProperty(name = "demo.enabled", defaultValue = "false")
    boolean enabled;

    @ServerRequestFilter
    public Optional<Response> gate(ContainerRequestContext context) {
        if (enabled) {
            return Optional.empty();
        }
        String path = context.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        boolean isDemo = path.equals("demo") || path.startsWith("demo/");
        return isDemo
                ? Optional.of(Response.status(Response.Status.NOT_FOUND).build())
                : Optional.empty();
    }
}
