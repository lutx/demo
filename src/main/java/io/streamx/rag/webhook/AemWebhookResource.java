package io.streamx.rag.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamx.rag.config.RagConfiguration;
import io.streamx.rag.webhook.AemWebhookService.WebhookEvent;
import io.streamx.rag.webhook.AemWebhookService.WebhookResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * AEM push-ingestion webhook.
 *
 * <p>AEM calls this endpoint after publishing or deleting content.
 * The RAG service immediately fetches the changed content from AEM,
 * removes stale vectors, and re-ingests fresh chunks — no polling needed.
 *
 * <p>Disabled by default. Enable with {@code rag.webhook.enabled=true}
 * (or {@code AEM_WEBHOOK_ENABLED=true} env var).
 *
 * <p><b>Authentication</b> (two options, first configured wins):
 * <ol>
 *   <li>HMAC-SHA256 — set {@code rag.webhook.hmac-secret} (recommended for production).
 *       AEM must send {@code X-AEM-Signature: sha256=<hex>} computed over the raw request body.</li>
 *   <li>Admin Key — leave {@code rag.webhook.hmac-secret} empty.
 *       AEM sends {@code X-Admin-Key: <key>} (same key as admin endpoints).</li>
 * </ol>
 *
 * @author Łukasz
 */
@Path("/api/webhook/aem")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AemWebhookResource {

    private static final Logger LOG = Logger.getLogger(AemWebhookResource.class);
    private static final String HEADER_SIGNATURE = "X-AEM-Signature";
    private static final String HEADER_ADMIN_KEY  = "X-Admin-Key";

    @Inject RagConfiguration config;
    @Inject AemWebhookService webhookService;
    @Inject ObjectMapper objectMapper;

    @ConfigProperty(name = "rag.admin.api-key", defaultValue = "")
    String adminApiKey;

    @POST
    public Response handleEvent(@Context HttpHeaders headers, String body) {
        if (!config.webhook().enabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"AEM webhook is not enabled. Set rag.webhook.enabled=true.\"}")
                    .build();
        }

        Response authError = authenticate(headers, body);
        if (authError != null) return authError;

        WebhookEvent event;
        try {
            event = objectMapper.readValue(body, WebhookEvent.class);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}")
                    .build();
        }

        if (event.action() == null || event.paths() == null || event.paths().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"'action' and non-empty 'paths' are required\"}")
                    .build();
        }

        WebhookResult result = webhookService.handle(event);
        return Response.ok(result).build();
    }

    // ── authentication ────────────────────────────────────────────────────────

    /**
     * Returns null if auth passes, or an error Response if it fails.
     * Priority: HMAC secret → Admin Key.
     */
    private Response authenticate(HttpHeaders headers, String body) {
        String hmacSecret = config.webhook().hmacSecret()
                .filter(s -> !s.isBlank())
                .orElse(null);

        if (hmacSecret != null) {
            return verifyHmac(headers, body, hmacSecret);
        }

        // Fallback: Admin Key (same behaviour as AdminAuthFilter)
        if (adminApiKey != null && !adminApiKey.isBlank()) {
            String provided = headers.getHeaderString(HEADER_ADMIN_KEY);
            if (provided == null || !adminApiKey.equals(provided)) {
                LOG.warn("Webhook rejected: missing or invalid X-Admin-Key");
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\":\"Missing or invalid X-Admin-Key header\"}")
                        .build();
            }
        } else {
            LOG.warn("SECURITY: rag.webhook.hmac-secret and rag.admin.api-key are both unset " +
                     "— webhook is unprotected. Set one of them in production.");
        }
        return null;
    }

    /**
     * Verifies {@code X-AEM-Signature: sha256=<hex>} using HMAC-SHA256.
     * Comparison is constant-time to prevent timing attacks.
     */
    private Response verifyHmac(HttpHeaders headers, String body, String secret) {
        String signature = headers.getHeaderString(HEADER_SIGNATURE);
        if (signature == null || !signature.startsWith("sha256=")) {
            LOG.warn("Webhook rejected: missing or malformed X-AEM-Signature header");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Missing or malformed X-AEM-Signature header. " +
                            "Expected: sha256=<hex(HMAC-SHA256(secret, body))>\"}")
                    .build();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hmac);

            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                       signature.getBytes(StandardCharsets.UTF_8))) {
                LOG.warn("Webhook rejected: HMAC signature mismatch");
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\":\"X-AEM-Signature verification failed\"}")
                        .build();
            }
        } catch (Exception e) {
            LOG.error("Webhook HMAC verification error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Signature verification error\"}")
                    .build();
        }
        return null;
    }
}
