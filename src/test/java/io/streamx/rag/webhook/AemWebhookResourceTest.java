package io.streamx.rag.webhook;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for AemWebhookResource.
 *
 * Covers: disabled state, Admin Key auth, HMAC auth, body validation,
 * Activate and Delete actions.
 *
 * AemWebhookService is mocked in all tests — no real AEM connection needed.
 */
@QuarkusTest
class AemWebhookResourceTest {

    // ── Webhook disabled (default) ────────────────────────────────────────────

    @Test
    void shouldReturn404WhenWebhookDisabledByDefault() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/test\"]}")
                .when()
                .post("/api/webhook/aem")
                .then()
                .statusCode(404);
    }

    // ── Webhook enabled + Admin Key auth ─────────────────────────────────────

    @QuarkusTest
    @TestProfile(WebhookWithAdminKeyProfile.class)
    static class AdminKeyAuthTest {

        @InjectMock
        AemWebhookService webhookService;

        @Test
        void shouldReturn403WhenAdminKeyMissing() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn403WhenAdminKeyIsWrong() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "wrong-key")
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn400WhenActionFieldMissing() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(400);
        }

        @Test
        void shouldReturn400WhenPathsArrayIsEmpty() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"action\":\"Activate\",\"paths\":[]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(400);
        }

        @Test
        void shouldReturn400ForMalformedJson() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("not-valid-json")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(400);
        }

        @Test
        void shouldReturn200ForActivateEventWithValidKey() {
            when(webhookService.handle(any()))
                    .thenReturn(new AemWebhookService.WebhookResult("Activate", 1, 0));

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"],\"type\":\"aem-content-fragment\"}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(200)
                    .body("action",   is("Activate"))
                    .body("upserted", is(1))
                    .body("deleted",  is(0));
        }

        @Test
        void shouldReturn200ForDeactivateEventWithValidKey() {
            when(webhookService.handle(any()))
                    .thenReturn(new AemWebhookService.WebhookResult("Deactivate", 0, 1));

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"action\":\"Deactivate\",\"paths\":[\"/content/my-site/en/products/old-item\"],\"type\":\"aem-page\"}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(200)
                    .body("action",   is("Deactivate"))
                    .body("upserted", is(0))
                    .body("deleted",  is(1));
        }

        @Test
        void shouldReturn200ForDeleteEventWithValidKey() {
            when(webhookService.handle(any()))
                    .thenReturn(new AemWebhookService.WebhookResult("Delete", 0, 2));

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"action\":\"Delete\",\"paths\":[\"/content/dam/articles/a\",\"/content/dam/articles/b\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(200)
                    .body("deleted", is(2));
        }

        @Test
        void shouldReturn200WithAutoDetectedTypeWhenTypeOmitted() {
            when(webhookService.handle(any()))
                    .thenReturn(new AemWebhookService.WebhookResult("Activate", 1, 0));

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Admin-Key", "test-admin-key")
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(200)
                    .body("upserted", is(1));
        }
    }

    // ── HMAC-SHA256 authentication ────────────────────────────────────────────

    @QuarkusTest
    @TestProfile(WebhookWithHmacProfile.class)
    static class HmacAuthTest {

        private static final String HMAC_SECRET = "test-hmac-secret-for-webhook-32x";

        @InjectMock
        AemWebhookService webhookService;

        @Test
        void shouldReturn403WhenSignatureHeaderMissing() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn403WhenSignatureIsInvalid() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-AEM-Signature", "sha256=badhex00000000000000000000000000")
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn403WhenSignaturePrefixMissing() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-AEM-Signature", "no-prefix-here")
                    .body("{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}")
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn200WhenHmacSignatureIsValid() throws Exception {
            when(webhookService.handle(any()))
                    .thenReturn(new AemWebhookService.WebhookResult("Activate", 1, 0));

            String body = "{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/new-guide\"],\"type\":\"aem-content-fragment\"}";
            String signature = "sha256=" + hmac(HMAC_SECRET, body);

            given()
                    .contentType(ContentType.JSON)
                    .header("X-AEM-Signature", signature)
                    .body(body)
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(200)
                    .body("action",   is("Activate"))
                    .body("upserted", is(1));
        }

        @Test
        void shouldReturn403WhenBodyTamperedAfterSigning() throws Exception {
            String originalBody = "{\"action\":\"Activate\",\"paths\":[\"/content/dam/articles/guide\"]}";
            String signature    = "sha256=" + hmac(HMAC_SECRET, originalBody);
            String tamperedBody = "{\"action\":\"Delete\",\"paths\":[\"/content/dam/articles/guide\"]}";

            given()
                    .contentType(ContentType.JSON)
                    .header("X-AEM-Signature", signature)
                    .body(tamperedBody)
                    .when()
                    .post("/api/webhook/aem")
                    .then()
                    .statusCode(403);
        }

        private String hmac(String secret, String data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        }
    }

    // ── Test profiles ─────────────────────────────────────────────────────────

    public static class WebhookWithAdminKeyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "rag.webhook.enabled", "true",
                    "rag.admin.api-key",   "test-admin-key"
            );
        }
    }

    public static class WebhookWithHmacProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "rag.webhook.enabled",     "true",
                    "rag.webhook.hmac-secret", "test-hmac-secret-for-webhook-32x"
            );
        }
    }
}
