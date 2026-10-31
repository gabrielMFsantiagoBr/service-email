package br.com.serviceemail.demo;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Com {@code demo.enabled=false} (padrão em produção) a página e a API somem. */
@QuarkusTest
@TestProfile(DemoDisabledTest.DemoOff.class)
class DemoDisabledTest {

    public static class DemoOff implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("demo.enabled", "false");
        }
    }

    @Inject
    MockMailbox mailbox;

    @Test
    void page_returns404() {
        given().when().get("/demo").then().statusCode(404);
    }

    @Test
    void demoApi_returns404_andNothingIsSent() {
        mailbox.clear();
        given().when().get("/demo/api/info").then().statusCode(404);
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "Maria", "email", "maria@example.com", "password", "senha-forte-123"))
                .when().post("/demo/api/signup")
                .then().statusCode(404);
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "Joao", "email", "joao@example.com", "message", "Oi"))
                .when().post("/demo/api/support")
                .then().statusCode(404);
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void emailApi_isUnaffected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("to", "ana@example.com", "subject", "Oi", "text", "corpo"))
                .when().post("/api/emails/send")
                .then().statusCode(202);
    }
}
