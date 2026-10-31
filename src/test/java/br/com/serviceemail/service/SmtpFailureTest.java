package br.com.serviceemail.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Desliga o mock e aponta para uma porta onde não há SMTP: a conexão é
 * recusada na hora, o que exercita o caminho de falha de envio.
 */
@QuarkusTest
@TestProfile(SmtpFailureTest.UnreachableSmtp.class)
class SmtpFailureTest {

    public static class UnreachableSmtp implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.mailer.mock", "false",
                    "quarkus.mailer.host", "localhost",
                    "quarkus.mailer.port", "1",
                    "quarkus.mailer.start-tls", "DISABLED");
        }
    }

    @Test
    void smtpDown_returns502WithGenericError() {
        given().contentType(ContentType.JSON)
                .body(Map.of("to", "ana@example.com", "subject", "Oi", "text", "corpo"))
                .when().post("/api/emails/send")
                .then().statusCode(502)
                .body("code", is("EMAIL_SEND_FAILED"))
                .body("message", is("Não foi possível enviar o email."));
    }
}
