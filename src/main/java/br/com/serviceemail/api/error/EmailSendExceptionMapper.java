package br.com.serviceemail.api.error;

import br.com.serviceemail.service.EmailSendException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Falha ao falar com o SMTP vira 502 (o problema está no servidor de email, não na requisição). */
@Provider
public class EmailSendExceptionMapper implements ExceptionMapper<EmailSendException> {

    @Override
    public Response toResponse(EmailSendException exception) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("EMAIL_SEND_FAILED", "Não foi possível enviar o email."))
                .build();
    }
}
