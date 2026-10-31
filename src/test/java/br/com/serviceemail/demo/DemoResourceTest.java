package br.com.serviceemail.demo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DemoResourceTest {

    private static final String PASSWORD = "senha-forte-123";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Inject
    MockMailbox mailbox;

    @Inject
    DemoAccountService accounts;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static ValidatableResponse post(String path, Map<String, String> body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/demo/api" + path)
                .then();
    }

    private static void signup(String email, String password) {
        post("/signup", Map.of("name", "Maria", "email", email, "password", password)).statusCode(201);
    }

    private static int loginStatus(String email, String password) {
        return post("/login", Map.of("email", email, "password", password)).extract().statusCode();
    }

    /** Pede a redefinição e devolve o token que foi no link do email. */
    private String requestResetToken(String email) {
        post("/forgot-password", Map.of("email", email)).statusCode(202);
        List<Mail> sent = mailbox.getMailsSentTo(email);
        Mail reset = sent.stream()
                .filter(m -> m.getSubject().startsWith("Redefinição de senha"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("email de redefinição não foi enviado"));
        Matcher m = TOKEN.matcher(reset.getText());
        assertTrue(m.find(), "link com token não encontrado no email");
        return m.group(1);
    }

    // ------------------------------------------------------------------ página

    @Test
    void page_isServedWithSecurityHeaders() {
        given().when().get("/demo")
                .then().statusCode(200)
                .contentType(containsString("text/html"))
                .header("Content-Security-Policy", notNullValue())
                .header("Cache-Control", is("no-store"))
                .body(containsString("Criar conta"))
                .body(containsString("Esqueci a senha"))
                .body(containsString("Suporte"));
    }

    @Test
    void info_reportsMockModeInTests() {
        given().when().get("/demo/api/info")
                .then().statusCode(200)
                .body("mailMode", is("mock"))
                .body("supportTo", is("suporte@example.com"));
    }

    // ------------------------------------------------------------ criar conta

    @Test
    void signup_createsAccountAndSendsWelcomeEmail() {
        String email = uniqueEmail();
        post("/signup", Map.of("name", "Maria", "email", email, "password", PASSWORD))
                .statusCode(201)
                .body("name", is("Maria"))
                .body("email", is(email))
                .body("welcomeEmail", is("sent"));

        List<Mail> sent = mailbox.getMailsSentTo(email);
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getSubject().startsWith("Bem-vindo(a) ao "));
        assertTrue(sent.get(0).getHtml().contains("Maria"));
    }

    @Test
    void signup_normalizesEmailToLowerCase() {
        String email = uniqueEmail();
        post("/signup", Map.of("name", "Ana", "email", email.toUpperCase(), "password", PASSWORD))
                .statusCode(201)
                .body("email", is(email));
        assertEquals(1, mailbox.getMailsSentTo(email).size());
        assertEquals(200, loginStatus(email, PASSWORD));
    }

    @Test
    void signup_duplicateEmail_returns409AndSendsNoSecondEmail() {
        String email = uniqueEmail();
        signup(email, PASSWORD);
        mailbox.clear();

        post("/signup", Map.of("name", "Outra", "email", email.toUpperCase(), "password", "outra-senha-123"))
                .statusCode(409)
                .body("code", is("EMAIL_ALREADY_REGISTERED"));
        assertEquals(0, mailbox.getTotalMessagesSent());
        // a conta original continua com a senha original
        assertEquals(200, loginStatus(email, PASSWORD));
        assertEquals(401, loginStatus(email, "outra-senha-123"));
    }

    @Test
    void signup_invalidData_isRejected() {
        post("/signup", Map.of("name", "Maria", "email", "nao-e-email", "password", PASSWORD)).statusCode(400);
        post("/signup", Map.of("name", "Maria", "email", uniqueEmail(), "password", "curta")).statusCode(400);
        post("/signup", Map.of("name", "", "email", uniqueEmail(), "password", PASSWORD)).statusCode(400);
        post("/signup", Map.of("name", "Oi\r\nBcc: x@example.com", "email", uniqueEmail(),
                "password", PASSWORD)).statusCode(400);
        post("/signup", Map.of()).statusCode(400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void signup_neverReturnsOrEmailsThePassword() {
        String email = uniqueEmail();
        String body = given().contentType(ContentType.JSON)
                .body(Map.of("name", "Maria", "email", email, "password", PASSWORD))
                .when().post("/demo/api/signup")
                .then().statusCode(201).extract().asString();

        assertFalse(body.contains(PASSWORD));
        Mail welcome = mailbox.getMailsSentTo(email).get(0);
        assertFalse(welcome.getText().contains(PASSWORD));
        assertFalse(welcome.getHtml().contains(PASSWORD));
    }

    // ------------------------------------------------------------------ login

    @Test
    void login_withCorrectCredentials_returnsUser() {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        post("/login", Map.of("email", email, "password", PASSWORD))
                .statusCode(200)
                .body("name", is("Maria"))
                .body("email", is(email));
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_getTheSameGenericError() {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        post("/login", Map.of("email", email, "password", "senha-errada-1"))
                .statusCode(401).body("code", is("INVALID_CREDENTIALS"));
        post("/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .statusCode(401).body("code", is("INVALID_CREDENTIALS"));
    }

    @Test
    void login_missingFields_isRejected() {
        post("/login", Map.of("email", uniqueEmail())).statusCode(400);
        post("/login", Map.of("password", PASSWORD)).statusCode(400);
    }

    // ---------------------------------------------------- recuperação de senha

    @Test
    void forgotPassword_existingAccount_emailsLinkWithToken() {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        String token = requestResetToken(email);

        Mail mail = mailbox.getMailsSentTo(email).stream()
                .filter(m -> m.getSubject().startsWith("Redefinição de senha")).findFirst().orElseThrow();
        assertTrue(mail.getText().contains("/demo?token=" + token));
        assertTrue(mail.getHtml().contains("/demo?token=" + token));
        assertTrue(token.length() >= 40, "token precisa ser longo e aleatório");
    }

    @Test
    void forgotPassword_unknownEmail_looksTheSameAndSendsNothing() {
        post("/forgot-password", Map.of("email", uniqueEmail()))
                .statusCode(202)
                .body("message", containsString("Se o email estiver cadastrado"));
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void forgotPassword_invalidEmail_isRejected() {
        post("/forgot-password", Map.of("email", "isto-nao-e-email")).statusCode(400);
    }

    @Test
    void resetPassword_fullFlow_changesPasswordAndTokenWorksOnlyOnce() {
        String email = uniqueEmail();
        signup(email, PASSWORD);
        String token = requestResetToken(email);

        post("/reset-password", Map.of("token", token, "newPassword", "nova-senha-456")).statusCode(200);

        assertEquals(200, loginStatus(email, "nova-senha-456"));
        assertEquals(401, loginStatus(email, PASSWORD));

        // reutilizar o mesmo link não funciona
        post("/reset-password", Map.of("token", token, "newPassword", "outra-senha-789"))
                .statusCode(400).body("code", is("INVALID_OR_EXPIRED_TOKEN"));
        assertEquals(200, loginStatus(email, "nova-senha-456"));
    }

    @Test
    void resetPassword_newRequestInvalidatesPreviousLink() {
        String email = uniqueEmail();
        signup(email, PASSWORD);
        String first = requestResetToken(email);
        String second = requestResetToken(email);

        post("/reset-password", Map.of("token", first, "newPassword", "nova-senha-456")).statusCode(400);
        post("/reset-password", Map.of("token", second, "newPassword", "nova-senha-456")).statusCode(200);
    }

    @Test
    void resetPassword_expiredToken_isRejectedAndPasswordUnchanged() {
        String email = uniqueEmail();
        signup(email, PASSWORD);
        String expired = accounts.issueResetToken(email, Duration.ofMinutes(-1)).orElseThrow().token();

        post("/reset-password", Map.of("token", expired, "newPassword", "nova-senha-456"))
                .statusCode(400).body("code", is("INVALID_OR_EXPIRED_TOKEN"));
        assertEquals(200, loginStatus(email, PASSWORD));
    }

    @Test
    void resetPassword_invalidToken_isRejected() {
        post("/reset-password", Map.of("token", "token-que-nao-existe", "newPassword", "nova-senha-456"))
                .statusCode(400).body("code", is("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void resetPassword_weakNewPassword_isRejectedAndTokenStaysUsable() {
        String email = uniqueEmail();
        signup(email, PASSWORD);
        String token = requestResetToken(email);

        post("/reset-password", Map.of("token", token, "newPassword", "curta")).statusCode(400);
        post("/reset-password", Map.of("token", token, "newPassword", "nova-senha-456")).statusCode(200);
    }

    // ---------------------------------------------------------------- suporte

    @Test
    void support_sendsToSupportAddressWithSenderInReplyTo() {
        post("/support", Map.of("name", "João", "email", "joao@example.com",
                "message", "Não consigo acessar minha conta.\nPode me ajudar?"))
                .statusCode(202);

        List<Mail> sent = mailbox.getMailsSentTo("suporte@example.com");
        assertEquals(1, sent.size());
        Mail mail = sent.get(0);
        assertEquals("[Suporte] Mensagem de João", mail.getSubject());
        assertEquals("joao@example.com", mail.getReplyTo());
        assertTrue(mail.getText().contains("Não consigo acessar minha conta."));
        assertTrue(mail.getText().contains("joao@example.com"));
    }

    @Test
    void support_invalidData_isRejected() {
        post("/support", Map.of("name", "João", "email", "x", "message", "Oi")).statusCode(400);
        post("/support", Map.of("name", "João", "email", "joao@example.com", "message", " ")).statusCode(400);
        post("/support", Map.of("name", "Oi\r\nBcc: x@example.com", "email", "joao@example.com",
                "message", "Oi")).statusCode(400);
        post("/support", Map.of("name", "João", "email", "joao@example.com",
                "message", "a".repeat(5001))).statusCode(400);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }
}
