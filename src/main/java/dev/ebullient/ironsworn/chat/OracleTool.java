package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.ebullient.ironsworn.IronswornMechanics;
import dev.ebullient.ironsworn.model.OracleResult;
import dev.langchain4j.agent.tool.Tool;

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

    @Tool("""
            Roll on an Ironsworn oracle table to answer an open question in the story.
            Use this ONLY when the player has not defined what happens and the situation
            calls for a random detail — a discovery, descriptor, NPC trait, or event.
            Do NOT use this to resolve questions the player has already answered.

            Available collections and tables:
            - action_and_theme: action, theme
            - character: descriptor, role, goal, disposition
            - place: location, descriptor
            - settlement: name, trouble
            - turning_point: (default table)

            Returns a formatted journal entry you should include verbatim in your narrative.
            """)
    public String rollOracle(String collectionKey, String tableKey) {
        OracleResult result = mechanics.rollOracle(collectionKey, tableKey);
        return "> **Oracle** (%s / %s): rolled %d → **%s**".formatted(
                result.collectionName(), result.tableName(),
                result.roll(), result.resultText());
    }
}
