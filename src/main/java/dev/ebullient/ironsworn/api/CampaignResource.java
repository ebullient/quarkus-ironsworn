package dev.ebullient.ironsworn.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;

import dev.ebullient.ironsworn.GameJournal;
import dev.ebullient.ironsworn.chat.CampaignAssistant;
import dev.ebullient.ironsworn.chat.CampaignResponse;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import dev.ebullient.ironsworn.memory.StoryMemoryService;
import dev.ebullient.ironsworn.model.CharacterSheet;

@ApplicationScoped
@Path("/api/campaign")
public class CampaignResource {

    @Inject
    CampaignAssistant assistant;

    @Inject
    GameJournal journal;

    @Inject
    StoryMemoryService storyMemory;

    @Inject
    MarkdownAugmenter prettify;

    @POST
    @Path("/{campaignId}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_HTML)
    public String ask(@RestPath String campaignId, String question) {
        CharacterSheet character = journal.readCharacter(campaignId);
        String charCtx = formatCharacterContext(character);
        String journalCtx = journal.getRecentJournal(campaignId, 20);
        String memoryCtx = storyMemory.relevantMemory(campaignId, question);

        CampaignResponse response = assistant.answer(campaignId, charCtx, journalCtx, memoryCtx, question);
        return prettify.markdownToHtml(response.response() != null ? response.response() : "");
    }

    private String formatCharacterContext(CharacterSheet c) {
        StringBuilder sb = new StringBuilder();
        sb.append("**%s** — Edge %d, Heart %d, Iron %d, Shadow %d, Wits %d\n".formatted(
                c.name(), c.edge(), c.heart(), c.iron(), c.shadow(), c.wits()));
        sb.append("Health %d, Spirit %d, Supply %d, Momentum %d\n".formatted(
                c.health(), c.spirit(), c.supply(), c.momentum()));
        if (!c.vows().isEmpty()) {
            sb.append("Vows:\n");
            for (var vow : c.vows()) {
                sb.append("- %s (%s, %d/10)\n".formatted(
                        vow.description(), vow.rank().name().toLowerCase(), vow.progress()));
            }
        }
        return sb.toString();
    }
}
