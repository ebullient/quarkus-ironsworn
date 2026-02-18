package dev.ebullient.ironsworn.api;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import Datasworn.OracleTablesCollection;
import dev.ebullient.ironsworn.DataswornService;

@Path("/api/oracles")
@ApplicationScoped
public class OraclesResource {

    @Inject
    DataswornService data;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, OracleTablesCollection> getOracleCollections() {
        return data.getOracles();
    }
}
