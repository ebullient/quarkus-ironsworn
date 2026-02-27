package dev.ebullient.ironsworn.web;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import Datasworn.AtlasEntry;
import Datasworn.MoveCategory;
import Datasworn.OracleTablesCollection;
import Datasworn.Rules;
import dev.ebullient.ironsworn.DataswornService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/reference")
@ApplicationScoped
public class Reference {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index();

        public static native TemplateInstance atlas(Map<String, AtlasEntry> locations);

        public static native TemplateInstance moves(Map<String, MoveCategory> moves);

        public static native TemplateInstance oracles(Map<String, OracleTablesCollection> oracles);

        public static native TemplateInstance rules(Rules rules);
    }

    @Inject
    DataswornService data;

    @GET
    @Path("/")
    public TemplateInstance index() {
        return Templates.index();
    }

    @GET
    @Path("/atlas")
    public TemplateInstance atlas() {
        return Templates.atlas(data.getAtlas());
    }

    @GET
    @Path("/moves")
    public TemplateInstance moves() {
        return Templates.moves(data.getMoves());
    }

    @GET
    @Path("/oracles")
    public TemplateInstance oracles() {
        return Templates.oracles(data.getOracles());
    }

    @GET
    @Path("/rules")
    public TemplateInstance rules() {
        return Templates.rules(data.getRules());
    }
}
