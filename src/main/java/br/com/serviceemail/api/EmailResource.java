package br.com.serviceemail.api;

import br.com.serviceemail.api.dto.EmailResponse;
import br.com.serviceemail.api.dto.NotificationEmailRequest;
import br.com.serviceemail.api.dto.PasswordResetEmailRequest;
import br.com.serviceemail.api.dto.SendEmailRequest;
import br.com.serviceemail.api.dto.WelcomeEmailRequest;
import br.com.serviceemail.service.EmailService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/emails")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Emails")
public class EmailResource {

    @Inject
    EmailService emailService;

    @POST
    @Path("/send")
    @Operation(summary = "Envia um email genérico (texto e, opcionalmente, HTML)")
    public Response send(@Valid @NotNull SendEmailRequest request) {
        emailService.send(request.to(), request.subject(), request.text(), request.html());
        return accepted();
    }

    @POST
    @Path("/welcome")
    @Operation(summary = "Envia o email de boas-vindas")
    public Response welcome(@Valid @NotNull WelcomeEmailRequest request) {
        emailService.sendWelcome(request.to(), request.name());
        return accepted();
    }

    @POST
    @Path("/password-reset")
    @Operation(summary = "Envia o email de recuperação de senha com o link informado")
    public Response passwordReset(@Valid @NotNull PasswordResetEmailRequest request) {
        emailService.sendPasswordReset(request.to(), request.name(), request.resetLink());
        return accepted();
    }

    @POST
    @Path("/notification")
    @Operation(summary = "Envia uma notificação (título vira o assunto)")
    public Response notification(@Valid @NotNull NotificationEmailRequest request) {
        emailService.sendNotification(request.to(), request.title(), request.message());
        return accepted();
    }

    private Response accepted() {
        return Response.accepted(new EmailResponse("accepted")).build();
    }
}
