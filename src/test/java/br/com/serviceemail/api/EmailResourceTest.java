package br.com.serviceemail.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmailResourceTest {

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private void post(String path, Map<String, String> body, int expectedStatus) {
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(path)
                .then().statusCode(expectedStatus);
    }

    // ----------------------------------------------------------------- /send

    @Test
    void send_textOnly_deliversPlainTextEmail() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("to", "ana@example.com", "subject", "Olá", "text", "Corpo da mensagem"))
                .when().post("/api/emails/send")
                .then().statusCode(202).body("status", is("accepted"));

        List<Mail> sent = mailbox.getMailsSentTo("ana@example.com");
        assertEquals(1, sent.size());
        assertEquals("Olá", sent.get(0).getSubject());
        assertEquals("Corpo da mensagem", sent.get(0).getText());
        assertNull(sent.get(0).getHtml());
    }

    @Test
    void send_withHtml_keepsTextAsAlternative() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com", "subject", "Novidades",
                        "text", "versão em texto", "html", "<p>versão em html</p>"),
                202);

        Mail mail = mailbox.getMailsSentTo("ana@example.com").get(0);
        assertEquals("<p>versão em html</p>", mail.getHtml());
        assertEquals("versão em texto", mail.getText());
    }

    @Test
    void send_blankHtml_isTreatedAsTextOnly() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com", "subject", "Oi", "text", "só texto", "html", "   "),
                202);

        assertNull(mailbox.getMailsSentTo("ana@example.com").get(0).getHtml());
    }

    @Test
    void send_emptyJsonObject_isRejected() {
        post("/api/emails/send", Map.of(), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_invalidRecipient_isRejectedAndNothingIsSent() {
        post("/api/emails/send",
                Map.of("to", "isto-nao-e-email", "subject", "Oi", "text", "corpo"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_blankSubject_isRejected() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com", "subject", "  ", "text", "corpo"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_blankText_isRejected() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com", "subject", "Oi", "text", ""), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_subjectWithLineBreak_isRejected_headerInjection() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com",
                        "subject", "Oi\r\nBcc: vitima@example.com",
                        "text", "corpo"),
                400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_subjectLongerThanLimit_isRejected() {
        post("/api/emails/send",
                Map.of("to", "ana@example.com", "subject", "a".repeat(201), "text", "corpo"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void send_malformedJson_isRejected() {
        given()
                .contentType(ContentType.JSON)
                .body("{ isto não é json")
                .when().post("/api/emails/send")
                .then().statusCode(400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    // -------------------------------------------------------------- /welcome

    @Test
    void welcome_sendsGreetingWithNameInHtmlAndText() {
        post("/api/emails/welcome", Map.of("to", "maria@example.com", "name", "Maria"), 202);

        Mail mail = mailbox.getMailsSentTo("maria@example.com").get(0);
        assertTrue(mail.getSubject().startsWith("Bem-vindo(a) ao "));
        assertTrue(mail.getHtml().contains("Maria"));
        assertTrue(mail.getText().contains("Maria"));
    }

    @Test
    void welcome_escapesHtmlInName() {
        post("/api/emails/welcome",
                Map.of("to", "maria@example.com", "name", "<script>alert(1)</script>"), 202);

        String html = mailbox.getMailsSentTo("maria@example.com").get(0).getHtml();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void welcome_missingName_isRejected() {
        post("/api/emails/welcome", Map.of("to", "maria@example.com"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    // -------------------------------------------------------- /password-reset

    @Test
    void passwordReset_includesLinkInHtmlAndText() {
        String link = "https://meusite.com/redefinir?token=abc123&u=1";
        post("/api/emails/password-reset",
                Map.of("to", "joao@example.com", "name", "João", "resetLink", link), 202);

        Mail mail = mailbox.getMailsSentTo("joao@example.com").get(0);
        assertTrue(mail.getSubject().startsWith("Redefinição de senha"));
        assertTrue(mail.getText().contains(link));
        // no HTML o "&" da query string é escapado como entidade
        assertTrue(mail.getHtml().contains("https://meusite.com/redefinir?token=abc123&amp;u=1"));
    }

    @Test
    void passwordReset_nonHttpLink_isRejected() {
        post("/api/emails/password-reset",
                Map.of("to", "joao@example.com", "name", "João", "resetLink", "javascript:alert(1)"),
                400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void passwordReset_linkWithSpaces_isRejected() {
        post("/api/emails/password-reset",
                Map.of("to", "joao@example.com", "name", "João",
                        "resetLink", "https://meusite.com/a b"),
                400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void passwordReset_missingLink_isRejected() {
        post("/api/emails/password-reset",
                Map.of("to", "joao@example.com", "name", "João"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    // ---------------------------------------------------------- /notification

    @Test
    void notification_usesTitleAsSubjectAndMessageAsBody() {
        post("/api/emails/notification",
                Map.of("to", "ana@example.com", "title", "Pedido enviado",
                        "message", "Seu pedido #42 saiu para entrega."),
                202);

        Mail mail = mailbox.getMailsSentTo("ana@example.com").get(0);
        assertEquals("Pedido enviado", mail.getSubject());
        assertTrue(mail.getHtml().contains("Seu pedido #42 saiu para entrega."));
        assertEquals("Seu pedido #42 saiu para entrega.", mail.getText());
    }

    @Test
    void notification_titleWithLineBreak_isRejected() {
        post("/api/emails/notification",
                Map.of("to", "ana@example.com", "title", "Oi\nBcc: x@example.com", "message", "corpo"),
                400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void notification_missingMessage_isRejected() {
        post("/api/emails/notification",
                Map.of("to", "ana@example.com", "title", "Aviso"), 400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }
}
