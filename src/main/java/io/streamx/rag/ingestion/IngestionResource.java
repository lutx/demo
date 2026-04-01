package io.streamx.rag.ingestion;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.streamx.rag.ingestion.DocumentIngestionService.IngestionResult;
import org.jboss.logging.Logger;

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
}
