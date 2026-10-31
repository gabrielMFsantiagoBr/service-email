package br.com.serviceemail.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ApiKeyFilterTest.WithApiKey.class)
class ApiKeyFilterTest {

    public static class WithApiKey implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("email.api-key", "chave-de-teste");
        }
    }

    private static final Map<String, String> BODY =
            Map.of("to", "ana@example.com", "subject", "Oi", "text", "corpo");

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void missingKey_returns401AndSendsNothing() {
        given().contentType(ContentType.JSON).body(BODY)
                .when().post("/api/emails/send")
                .then().statusCode(401).body("code", is("UNAUTHORIZED"));
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void wrongKey_returns401AndSendsNothing() {
        given().contentType(ContentType.JSON).header("X-API-Key", "chave-errada").body(BODY)
                .when().post("/api/emails/send")
                .then().statusCode(401);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void correctKey_isAccepted() {
        given().contentType(ContentType.JSON).header("X-API-Key", "chave-de-teste").body(BODY)
                .when().post("/api/emails/send")
                .then().statusCode(202);
        assertEquals(1, mailbox.getTotalMessagesSent());
    }

    @Test
    void keyIsCheckedBeforeValidation() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/api/emails/send")
                .then().statusCode(401);
    }

    @Test
    void healthEndpoint_doesNotRequireKey() {
        given().when().get("/q/health/live").then().statusCode(200);
    }
}
