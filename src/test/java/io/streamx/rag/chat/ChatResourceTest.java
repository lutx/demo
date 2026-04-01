package io.streamx.rag.chat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ChatResourceTest {

    @Test
    void chatEndpointShouldAcceptPostAndReturnSse() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"question\": \"What products are available?\"}")
                .when()
                .post("/api/chat")
                .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }
}
