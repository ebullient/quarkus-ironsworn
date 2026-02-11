package dev.ebullient.ironsworn.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

public class Chat extends Controller {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chat();
    }

    /**
     * Serve the main chat page.
     */
    @GET
    @Path("/chat")
    public TemplateInstance index() {
        return Templates.chat();
    }

}
