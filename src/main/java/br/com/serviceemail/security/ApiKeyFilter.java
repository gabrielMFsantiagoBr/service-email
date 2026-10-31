package br.com.serviceemail.security;

import br.com.serviceemail.api.error.ErrorResponse;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Protege /api/** com uma chave estática no header {@code X-API-Key} quando
 * {@code email.api-key} está configurada. Sem isso, qualquer pessoa que alcance
 * o serviço poderia usá-lo para disparar emails (spam).
 */
@Singleton
public class ApiKeyFilter {

    static final String HEADER = "X-API-Key";

    @ConfigProperty(name = "email.api-key")
    Optional<String> apiKey;

    @ServerRequestFilter
    public Optional<Response> checkApiKey(ContainerRequestContext context) {
        if (apiKey.isEmpty() || !isApiPath(context)) {
            return Optional.empty();
        }
        String provided = context.getHeaderString(HEADER);
        if (provided != null && sameValue(provided, apiKey.get())) {
            return Optional.empty();
        }
        return Optional.of(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("UNAUTHORIZED", "API key ausente ou inválida."))
                .build());
    }

    private static boolean isApiPath(ContainerRequestContext context) {
        String path = context.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.startsWith("api/");
    }

    /** Comparação em tempo constante, para não vazar a chave por diferença de tempo. */
    private static boolean sameValue(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
