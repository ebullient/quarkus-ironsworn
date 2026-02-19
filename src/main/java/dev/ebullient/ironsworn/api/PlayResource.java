package dev.ebullient.ironsworn.api;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestPath;

import com.fasterxml.jackson.databind.JsonNode;

import dev.ebullient.ironsworn.GameJournal;
import dev.ebullient.ironsworn.model.Campaign;
import dev.ebullient.ironsworn.model.CharacterSheet;

@ApplicationScoped
@Path("/api/play")
public class PlayResource {

    @Inject
    GameJournal journal;

    @GET
    @Path("/campaigns")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Campaign> listCampaigns() {
        return journal.listCampaigns();
    }

    @POST
    @Path("/campaigns")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createCampaign(JsonNode request) {
        String name = request.path("name").asText();
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Name is required\"}").build();
        }
        Campaign campaign = journal.createStubCampaign(name);
        return Response.status(Response.Status.CREATED).entity(campaign).build();
    }

    @GET
    @Path("/{campaignId}/character")
    @Produces(MediaType.APPLICATION_JSON)
    public CharacterSheet getCharacter(@RestPath String campaignId) {
        return journal.readCharacter(campaignId);
    }

    @PATCH
    @Path("/{campaignId}/character")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CharacterSheet updateCharacter(@RestPath String campaignId, CharacterSheet character) {
        journal.updateCharacter(campaignId, character);
        return journal.readCharacter(campaignId);
    }
}
