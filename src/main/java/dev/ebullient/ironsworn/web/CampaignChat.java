package dev.ebullient.ironsworn.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.ebullient.ironsworn.GameJournal;
import dev.ebullient.ironsworn.model.Campaign;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/campaign")
@ApplicationScoped
public class CampaignChat extends Controller {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance assistant(Campaign campaign);
    }

    @Inject
    GameJournal journal;

    @GET
    @Path("/{campaignId}")
    public TemplateInstance assistant(String campaignId) {
        Campaign campaign = journal.getCampaign(campaignId);
        if (campaign == null) {
            flash("error", "Campaign not found: " + campaignId);
            return Play.Templates.index(journal.listCampaigns());
        }
        return Templates.assistant(campaign);
    }
}
