package dev.ebullient.ironsworn.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

/**
 * Renarde controller for the main index/home page.
 * Provides an overview of the application's features.
 */
@Path("/")
@ApplicationScoped
public class Index extends Controller {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index();
    }

    /**
     * Serve the main index/home page.
     */
    @Path("/")
    public TemplateInstance index() {
        return Templates.index();
    }
}
