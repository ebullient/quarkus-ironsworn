package dev.ebullient.ironsworn.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class Chat extends Controller {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chat();

        public static native TemplateInstance rules();
    }

    /**
     * Serve the main chat page.
     */
    @GET
    @Path("/chat")
    public TemplateInstance chat() {
        return Templates.chat();
    }

    /**
     * Serve the main chat page.
     */
    @GET
    @Path("/rules")
    public TemplateInstance rules() {
        return Templates.rules();
    }
}
