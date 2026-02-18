package dev.ebullient.ironsworn.api;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.resteasy.reactive.RestPath;

import Datasworn.Move;
import dev.ebullient.ironsworn.DataswornService;

@Path("/api/moves")
@ApplicationScoped
public class MovesResource {

    @Inject
    DataswornService data;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Move> listAllMoves() {
        return data.getMoves().values().stream()
                .flatMap(x -> x.getContents().values().stream())
                .toList();
    }

    @GET
    @Path("/categories")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<String> listCategories() {
        return data.getMoves().keySet();
    }

    @GET
    @Path("/category")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listMovesByCategory(@RestPath String category) {
        var allMoves = data.getMoves();
        var moves = allMoves.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue().getContents().values()));
        return Response.ok(moves).build();
    }

    @GET
    @Path("/category/{category}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCategoryMoves(@RestPath String category) {
        var moveCategory = data.getMoves().get(category);
        if (moveCategory == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(moveCategory.getContents().values()).build();
    }
}
