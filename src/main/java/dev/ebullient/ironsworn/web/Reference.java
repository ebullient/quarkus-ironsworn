package dev.ebullient.ironsworn.web;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import Datasworn.MoveCategory;
import Datasworn.OracleTablesCollection;
import dev.ebullient.ironsworn.DataswornService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/reference")
@ApplicationScoped
public class Reference {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index();

        public static native TemplateInstance moves(Collection<MoveCategory> moves);

        public static native TemplateInstance oracles(Collection<OracleTablesCollection> collection);
    }

    @Inject
    DataswornService data;

    @GET
    @Path("/")
    public TemplateInstance index() {
        return Templates.index();
    }

    @GET
    @Path("/moves")
    public TemplateInstance moves() {
        return Templates.moves(data.getMoves().values());
    }

    @GET
    @Path("/oracles")
    public TemplateInstance oracles() {
        return Templates.oracles(data.getOracles().values());
    }
}
