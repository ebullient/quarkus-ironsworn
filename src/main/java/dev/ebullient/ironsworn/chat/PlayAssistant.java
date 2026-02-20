package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
@OutputGuardrails(PlayResponseGuardrail.class)
public interface PlayAssistant {

    @SystemMessage("""
            You are a collaborative narrator for an Ironsworn solo roleplaying game.
            Your role is purely narrative — you bring the Ironlands to life through
            evocative, immersive descriptions and dialogue.

            IMPORTANT CONSTRAINTS:
            - You do NOT roll dice. Dice results will be provided to you.
            - You do NOT determine move outcomes. The game system determines outcomes.
            - You do NOT make mechanical decisions for the player.
            - Keep responses focused: 2-4 paragraphs of vivid narrative prose.
            - Do NOT use blockquote formatting (lines starting with ">") in your narrative.
              Blockquotes in the journal are reserved for mechanical results.
            - Stay consistent with the character's established story from the journal.
            - End each response with a moment of tension, discovery, or decision that
              invites the player to act next.

            The Ironlands is a dark, mythic frontier. Harsh weather, ancient mysteries,
            and dangerous creatures define this world. Tone is gritty and grounded,
            but not hopeless — there is always a reason to press on.
            """)
    @UserMessage("""
            ## Current Character
            {characterContext}

            ## Recent Journal
            {journalContext}

            ## Player
            {playerInput}
            """)
    PlayResponse narrate(
            @MemoryId String campaignId,
            @V("characterContext") String characterContext,
            @V("journalContext") String journalContext,
            @V("playerInput") String playerInput);

    @SystemMessage("""
            You are a collaborative narrator for an Ironsworn solo roleplaying game.
            Narrate the outcome of the move result below in 2-3 paragraphs.
            Be vivid but concise. The mechanical outcome has already been determined —
            your job is to bring it to life in the fiction.
            Do NOT use blockquote formatting (lines starting with ">") in your narrative.

            If the move text describes consequences (lose health, lose supply, etc.),
            weave them naturally into the narrative without explicitly stating the
            mechanical effects.
            """)
    @UserMessage("""
            ## Move Result
            **Move**: {moveName}
            **Outcome**: {outcome}
            **Roll**: Action {actionScore} vs Challenge {challenge1}/{challenge2}

            ## What the move says on {outcome}:
            {moveOutcomeText}

            ## Recent Journal
            {journalContext}

            Narrate what happens.
            """)
    PlayResponse narrateMoveResult(
            @MemoryId String campaignId,
            @V("moveName") String moveName,
            @V("outcome") String outcome,
            @V("actionScore") int actionScore,
            @V("challenge1") int challenge1,
            @V("challenge2") int challenge2,
            @V("moveOutcomeText") String moveOutcomeText,
            @V("journalContext") String journalContext);
}
