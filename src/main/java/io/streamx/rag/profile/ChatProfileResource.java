package io.streamx.rag.profile;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Admin API for managing chat profiles.
 *
 * <p>All endpoints are protected by {@code X-Admin-Key} (via {@link io.streamx.rag.security.AdminAuthFilter}).
 *
 * <pre>
 * GET    /api/admin/profiles              — list all profiles
 * GET    /api/admin/profiles/{name}       — get one profile by name
 * POST   /api/admin/profiles              — create a new profile
 * PUT    /api/admin/profiles/{name}       — update an existing profile (PATCH semantics)
 * DELETE /api/admin/profiles/{name}       — delete a profile (the "default" profile cannot be deleted)
 * </pre>
 */
@Path("/api/admin/profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatProfileResource {

    private static final Logger LOG = Logger.getLogger(ChatProfileResource.class);

    @Inject
    ChatProfileService profileService;

    @GET
    public List<ChatProfile> listAll() {
        return profileService.listAll();
    }

    @GET
    @Path("/{name}")
    public Response getByName(@PathParam("name") String name) {
        ChatProfile profile = profileService.findByName(name);
        if (profile == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Profile '" + name + "' not found\"}")
                    .build();
        }
        return Response.ok(profile).build();
    }

    @POST
    public Response create(ChatProfileRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"'name' is required\"}")
                    .build();
        }
        if (req.systemPrompt() == null || req.systemPrompt().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"'systemPrompt' is required\"}")
                    .build();
        }
        try {
            ChatProfile created = profileService.create(req);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @PUT
    @Path("/{name}")
    public Response update(@PathParam("name") String name, ChatProfileRequest req) {
        try {
            ChatProfile updated = profileService.update(name, req);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Profile '" + name + "' not found\"}")
                        .build();
            }
            return Response.ok(updated).build();
        } catch (Exception e) {
            LOG.errorf("Failed to update profile '%s': %s", name, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Update failed. Check server logs.\"}")
                    .build();
        }
    }

    @DELETE
    @Path("/{name}")
    public Response delete(@PathParam("name") String name) {
        try {
            boolean deleted = profileService.delete(name);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Profile '" + name + "' not found\"}")
                        .build();
            }
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}
