package br.com.serviceemail.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serve a página de teste em {@code /demo}. O HTML fica em {@code src/main/resources/demo/}
 * (e não em META-INF/resources) para só ser entregue por aqui, quando a demo está ligada.
 */
@Path("/demo")
public class DemoPageResource {

    /** A página não carrega nada de fora e só fala com o próprio servidor. */
    private static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; "
            + "style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'none'";

    @GET
    @Produces("text/html;charset=UTF-8")
    public Response page() {
        return Response.ok(loadHtml())
                .header("Cache-Control", "no-store")
                .header("Content-Security-Policy", CSP)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    private static String loadHtml() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("demo/index.html")) {
            if (in == null) {
                throw new IllegalStateException("demo/index.html não encontrado no classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
