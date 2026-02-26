package dev.ebullient.ironsworn.api;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import Datasworn.OracleTablesCollection;
import dev.ebullient.ironsworn.DataswornService;
import dev.ebullient.ironsworn.chat.OracleTool;

@Path("/api/oracles")
@ApplicationScoped
public class OraclesResource {

    @Inject
    DataswornService data;

    @Inject
    OracleTool oracleTool;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, OracleTablesCollection> getOracleCollections() {
        return data.getOracles();
    }

    @GET
    @Path("/test-roll")
    @Produces(MediaType.TEXT_PLAIN)
    public String testRoll(
            @QueryParam("collection") String collection,
            @QueryParam("table") String table) {
        return oracleTool.rollOracle(
                collection != null ? collection : "action_and_theme",
                table != null ? table : "action");
    }
}
