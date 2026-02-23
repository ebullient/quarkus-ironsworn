package dev.ebullient.ironsworn.web;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import Datasworn.MoveCategory;
import Datasworn.OracleTablesCollection;
import dev.ebullient.ironsworn.DataswornService;
import dev.ebullient.ironsworn.GameJournal;
import dev.ebullient.ironsworn.model.Campaign;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/play")
@ApplicationScoped
public class Play extends Controller {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(List<Campaign> campaigns);

        public static native TemplateInstance play(
                Campaign campaign,
                Map<String, MoveCategory> moves,
                Map<String, OracleTablesCollection> oracles);
    }

    @Inject
    GameJournal journal;

    @Inject
    DataswornService data;

    @GET
    @Path("/")
    public TemplateInstance index() {
        return Templates.index(journal.listCampaigns());
    }

    @GET
    @Path("/{campaignId}")
    public TemplateInstance play(String campaignId) {
        Campaign campaign = journal.getCampaign(campaignId);
        if (campaign == null) {
            flash("error", "Campaign not found: " + campaignId);
            return Templates.index(journal.listCampaigns());
        }
        return Templates.play(campaign, data.getMoves(), data.getOracles());
    }
}
