package br.com.serviceemail.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    Mailer mailer;

    @Inject
    @Location("email/welcome")
    Template welcome;

    @Inject
    @Location("email/password-reset")
    Template passwordReset;

    @Inject
    @Location("email/notification")
    Template notification;

    @ConfigProperty(name = "email.app-name", defaultValue = "Service Email")
    String appName;

    /** Envio genérico. Se {@code html} vier preenchido, o {@code text} segue como alternativa em texto puro. */
    public void send(String to, String subject, String text, String html) {
        Mail mail = (html == null || html.isBlank())
                ? Mail.withText(to, subject, text)
                : Mail.withHtml(to, subject, html).setText(text);
        dispatch(mail);
    }

    public void sendWelcome(String to, String name) {
        String html = welcome
                .data("appName", appName)
                .data("name", name)
                .render();
        String text = """
                Olá, %s!

                Sua conta no %s foi criada com sucesso. Seja bem-vindo(a)!
                """.formatted(name, appName);
        send(to, "Bem-vindo(a) ao " + appName, text, html);
    }

    public void sendPasswordReset(String to, String name, String resetLink) {
        String html = passwordReset
                .data("appName", appName)
                .data("name", name)
                .data("resetLink", resetLink)
                .render();
        String text = """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no %s.
                Para criar uma nova senha, acesse o link abaixo:

                %s

                Se você não fez esse pedido, ignore este email.
                """.formatted(name, appName, resetLink);
        send(to, "Redefinição de senha - " + appName, text, html);
    }

    public void sendNotification(String to, String title, String message) {
        String html = notification
                .data("appName", appName)
                .data("title", title)
                .data("message", message)
                .render();
        send(to, title, message, html);
    }

    /**
     * Mensagem de suporte para a equipe. O visitante vai em Reply-To, então responder o
     * email já responde a ele (o Gmail e outros SMTPs fixam o remetente real em From).
     */
    public void sendSupport(String to, String senderName, String senderEmail, String message) {
        // Defesa extra: nome vira parte do assunto, então não pode ter quebra de linha.
        String safeName = senderName.replaceAll("[\\r\\n]+", " ").trim();
        String text = """
                Nova mensagem de suporte

                Nome: %s
                Email: %s

                %s
                """.formatted(safeName, senderEmail, message);
        dispatch(Mail.withText(to, "[Suporte] Mensagem de " + safeName, text).setReplyTo(senderEmail));
    }

    private void dispatch(Mail mail) {
        try {
            mailer.send(mail);
        } catch (Exception e) {
            // Não loga o destinatário (dado pessoal); o assunto basta para rastrear.
            LOG.errorf(e, "Falha ao enviar email com assunto '%s'", mail.getSubject());
            throw new EmailSendException("Falha ao enviar email", e);
        }
    }
}
