package br.com.serviceemail.demo;

import br.com.serviceemail.api.error.ErrorResponse;
import br.com.serviceemail.demo.DemoDtos.ForgotPasswordRequest;
import br.com.serviceemail.demo.DemoDtos.InfoResponse;
import br.com.serviceemail.demo.DemoDtos.LoginRequest;
import br.com.serviceemail.demo.DemoDtos.MessageResponse;
import br.com.serviceemail.demo.DemoDtos.ResetPasswordRequest;
import br.com.serviceemail.demo.DemoDtos.SignupRequest;
import br.com.serviceemail.demo.DemoDtos.SignupResponse;
import br.com.serviceemail.demo.DemoDtos.SupportRequest;
import br.com.serviceemail.demo.DemoDtos.UserResponse;
import br.com.serviceemail.service.EmailSendException;
import br.com.serviceemail.service.EmailService;
import io.quarkus.runtime.LaunchMode;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * API da página de teste (/demo). Só responde quando {@code demo.enabled=true}
 * (ver {@link DemoGateFilter}): em produção fica desligada.
 */
@Path("/demo/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Demo")
public class DemoResource {

    private static final Logger LOG = Logger.getLogger(DemoResource.class);

    @Inject
    DemoAccountService accounts;

    @Inject
    EmailService emailService;

    @Inject
    Config config;

    @ConfigProperty(name = "demo.base-url")
    String baseUrl;

    @ConfigProperty(name = "demo.support-to")
    String supportTo;

    @ConfigProperty(name = "demo.reset-token-ttl-minutes", defaultValue = "30")
    long tokenTtlMinutes;

    @GET
    @Path("/info")
    @Operation(summary = "Mostra se os emails saem de verdade (SMTP) ou ficam só no log (mock)")
    public InfoResponse info() {
        boolean mock = config.getOptionalValue("quarkus.mailer.mock", Boolean.class)
                .orElse(LaunchMode.current() != LaunchMode.NORMAL);
        String host = config.getOptionalValue("quarkus.mailer.host", String.class).orElse("");
        return new InfoResponse(mock ? "mock" : "smtp", mock ? "" : host, supportTo);
    }

    @POST
    @Path("/signup")
    @Operation(summary = "Cria uma conta e envia o email de boas-vindas")
    public Response signup(@Valid @NotNull SignupRequest request) {
        var created = accounts.register(request.name(), request.email(), request.password());
        if (created.isEmpty()) {
            return error(Response.Status.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "Já existe uma conta com esse email.");
        }
        DemoAccount account = created.get();

        String welcomeStatus = "sent";
        try {
            emailService.sendWelcome(account.email(), account.name());
        } catch (EmailSendException e) {
            // A conta já existe; quem testa vê no resultado que só o email falhou.
            welcomeStatus = "failed";
        }
        return Response.status(Response.Status.CREATED)
                .entity(new SignupResponse(account.name(), account.email(), welcomeStatus))
                .build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Confere email e senha")
    public Response login(@Valid @NotNull LoginRequest request) {
        return accounts.authenticate(request.email(), request.password())
                .map(a -> Response.ok(new UserResponse(a.name(), a.email())).build())
                .orElseGet(() -> error(Response.Status.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email ou senha incorretos."));
    }

    @POST
    @Path("/forgot-password")
    @Operation(summary = "Envia o link de redefinição de senha, se existir conta com esse email")
    public Response forgotPassword(@Valid @NotNull ForgotPasswordRequest request) {
        accounts.issueResetToken(request.email(), Duration.ofMinutes(tokenTtlMinutes)).ifPresent(issued -> {
            String link = trimTrailingSlash(baseUrl) + "/demo?token=" + issued.token();
            try {
                emailService.sendPasswordReset(issued.email(), issued.name(), link);
            } catch (EmailSendException e) {
                // A falha já foi logada pelo EmailService. A resposta continua igual para não
                // revelar se o email tem conta.
                LOG.warn("Email de redefinição não foi enviado (veja o erro acima)");
            }
        });
        return Response.accepted(new MessageResponse(
                "Se o email estiver cadastrado, enviamos o link de redefinição.")).build();
    }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Define a nova senha usando o token recebido por email")
    public Response resetPassword(@Valid @NotNull ResetPasswordRequest request) {
        if (!accounts.resetPassword(request.token(), request.newPassword())) {
            return error(Response.Status.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN",
                    "Link inválido ou expirado. Peça um novo.");
        }
        return Response.ok(new MessageResponse("Senha alterada. Já pode entrar com a nova senha.")).build();
    }

    @POST
    @Path("/support")
    @Operation(summary = "Envia uma mensagem para o email de suporte (o remetente vai em Reply-To)")
    public Response support(@Valid @NotNull SupportRequest request) {
        emailService.sendSupport(supportTo, request.name(), request.email(), request.message());
        return Response.accepted(new MessageResponse("Mensagem enviada ao suporte.")).build();
    }

    private static Response error(Response.Status status, String code, String message) {
        return Response.status(status).entity(new ErrorResponse(code, message)).build();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
