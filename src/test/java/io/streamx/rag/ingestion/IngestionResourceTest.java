package io.streamx.rag.ingestion;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class IngestionResourceTest {

    /**
     * With no ADMIN_API_KEY configured (default empty), admin endpoints are
     * open — this is the dev-mode behaviour verified here.
     */
    @Test
    void fullSyncEndpointShouldReturnResultWhenNoKeyConfigured() {
        given()
                .when()
                .post("/api/admin/ingest")
                .then()
                .statusCode(200)
                .body("syncType", is("full"))
                .body("documentCount", notNullValue());
    }

    @Test
    void deltaSyncEndpointShouldReturnResult() {
        given()
                .when()
                .post("/api/admin/ingest/delta")
                .then()
                .statusCode(200)
                .body("syncType", notNullValue());
    }

    /**
     * When ADMIN_API_KEY is set, requests without the header must be rejected.
     * Uses a separate profile that sets the key.
     */
    @QuarkusTest
    @TestProfile(WithAdminKeyProfile.class)
    static class AdminKeyProtectionTest {

        @Test
        void shouldReturn403WithoutAdminKey() {
            given()
                    .when()
                    .post("/api/admin/ingest")
                    .then()
                    .statusCode(403);
        }

        @Test
        void shouldReturn200WithCorrectAdminKey() {
            given()
                    .header("X-Admin-Key", "test-secret-key")
                    .when()
                    .post("/api/admin/ingest")
                    .then()
                    .statusCode(200);
        }
    }

    public static class WithAdminKeyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("rag.admin.api-key", "test-secret-key");
        }
    }
}
