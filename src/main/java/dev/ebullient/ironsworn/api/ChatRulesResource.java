package dev.ebullient.ironsworn.api;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestQuery;

import dev.ebullient.ironsworn.chat.ChatAssistant;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;

/**
 * Generic LLM chat interface - independent of any specific setting or story.
 * Provides direct access to the underlying chat model.
 */
@ApplicationScoped
@Path("/api/rules")
public class ChatRulesResource {

    @Inject
    ChatAssistant chatService;

    @Inject
    MarkdownAugmenter prettify;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String chat(@RestQuery String question) {
        String markdownResponse = chatService.rules(question);
        return prettify.markdownToHtml(markdownResponse);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> chatJson(@RestQuery String question) {
        return Map.of("response", chatService.rules(question));
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_HTML)
    public String postChat(String question) {
        String markdownResponse = chatService.rules(question);
        return prettify.markdownToHtml(markdownResponse);
    }
}
