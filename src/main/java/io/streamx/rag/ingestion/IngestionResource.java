package io.streamx.rag.ingestion;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.streamx.rag.ingestion.DocumentIngestionService.IngestionResult;
import org.jboss.logging.Logger;

import java.util.List;

@Path("/api/admin/ingest")
@Produces(MediaType.APPLICATION_JSON)
public class IngestionResource {

    private static final Logger LOG = Logger.getLogger(IngestionResource.class);

    @Inject
    DocumentIngestionService ingestionService;

    @POST
    public Response triggerFullSync() {
        try {
            return Response.ok(ingestionService.runFullSync()).build();
        } catch (Exception e) {
            LOG.error("Full sync failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Full sync failed. Check server logs for details.\"}")
                    .build();
        }
    }

    @POST
    @Path("/delta")
    public Response triggerDeltaSync() {
        try {
            return Response.ok(ingestionService.runDeltaSync()).build();
        } catch (Exception e) {
            LOG.error("Delta sync failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Delta sync failed. Check server logs for details.\"}")
                    .build();
        }
    }

    @POST
    @Path("/source")
    public Response triggerSourceSync(@QueryParam("type") String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Missing required query parameter: type\"}")
                    .build();
        }
        try {
            return Response.ok(ingestionService.ingestFromSource(sourceType)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    // ── Generic push API ────────────────────────────────────────────────────────
    // Any external system (StreamX, AEM workflow, headless CMS, scripts) can push
    // content directly without implementing AEM-specific connectors.

    /**
     * Upserts a single document into the knowledge base.
     *
     * <pre>
     * POST /api/admin/ingest/document
     * {"url":"https://example.com/page","title":"My Page","text":"Full content..."}
     * </pre>
     *
     * Re-posting the same URL replaces existing vectors (upsert).
     */
    @POST
    @Path("/document")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestDocument(GenericDocumentRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Request body is required\"}")
                    .build();
        }
        try {
            IngestionResult result = ingestionService.ingestDocuments(List.of(request));
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            LOG.error("Generic document ingestion failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Ingestion failed. Check server logs for details.\"}")
                    .build();
        }
    }

    /**
     * Upserts multiple documents in a single call (up to 500 per request).
     *
     * <pre>
     * POST /api/admin/ingest/documents
     * {"documents":[{"url":"...","title":"...","text":"..."},{"url":"...","text":"..."}]}
     * </pre>
     */
    @POST
    @Path("/documents")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestDocuments(GenericDocumentRequest.Bulk request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"'documents' array is required and must not be empty\"}")
                    .build();
        }
        if (request.documents().size() > 500) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Maximum 500 documents per call\"}")
                    .build();
        }
        try {
            IngestionResult result = ingestionService.ingestDocuments(request.documents());
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            LOG.error("Bulk document ingestion failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Ingestion failed. Check server logs for details.\"}")
                    .build();
        }
    }

    /**
     * Removes all vectors for the given URL from the knowledge base.
     *
     * <pre>
     * DELETE /api/admin/ingest/document?url=https://example.com/page
     * </pre>
     */
    @DELETE
    @Path("/document")
    public Response deleteDocument(@QueryParam("url") String url) {
        if (url == null || url.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Missing required query parameter: url\"}")
                    .build();
        }
        try {
            ingestionService.deleteDocument(url);
            return Response.ok("{\"deleted\":true,\"url\":\"" + url + "\"}").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            LOG.error("Document deletion failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Deletion failed. Check server logs for details.\"}")
                    .build();
        }
    }
}
