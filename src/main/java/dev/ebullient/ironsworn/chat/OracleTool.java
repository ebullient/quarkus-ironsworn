package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.ebullient.ironsworn.IronswornMechanics;
import dev.ebullient.ironsworn.model.OracleResult;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.quarkus.logging.Log;

/**
 * Tool that allows the PlayAssistant to roll on Ironsworn oracle tables.
 *
 * Use this when the player has left a question open and has not defined the
 * answer themselves — a discovery, an NPC detail, a location descriptor, etc.
 * Do NOT use this to resolve questions the player has already answered.
 */
@ApplicationScoped
public class OracleTool {

    @Inject
    IronswornMechanics mechanics;

    public String rollOracle(String collectionKey, String tableKey, @ToolMemoryId String campaignId) {
        Log.debugf("%s: Ask the oracle - %s/%s", campaignId, collectionKey, tableKey);

        OracleResult result = mechanics.rollOracle(collectionKey, tableKey);
        return "> **Oracle** (%s / %s): rolled %d → **%s**".formatted(
                result.collectionName(), result.tableName(),
                result.roll(), result.resultText());
    }
}
