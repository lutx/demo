package io.streamx.rag.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Protects all /api/admin/* endpoints with a static API key.
 *
 * Configure via ADMIN_API_KEY env var or rag.admin.api-key property.
 * If not configured (blank), access is allowed but a warning is logged —
 * useful for local development. In production always set ADMIN_API_KEY.
 *
 * @author Łukasz
 *
 * Usage:
 *   curl -X POST http://localhost:8081/api/admin/ingest \
 *        -H "X-Admin-Key: your-secret-key"
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AdminAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AdminAuthFilter.class);
    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String KEY_HEADER = "X-Admin-Key";

    @ConfigProperty(name = "rag.admin.api-key", defaultValue = "")
    String adminApiKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            return;
        }

        if (adminApiKey == null || adminApiKey.isBlank()) {
            LOG.warn("SECURITY: rag.admin.api-key is not set — admin endpoints are unprotected. " +
                     "Set ADMIN_API_KEY env var in production.");
            return;
        }

        String provided = ctx.getHeaderString(KEY_HEADER);
        if (provided == null || !adminApiKey.equals(provided)) {
            LOG.warnf("Rejected admin request to %s — invalid or missing %s header", path, KEY_HEADER);
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Missing or invalid X-Admin-Key header\"}")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build());
        }
    }
}
