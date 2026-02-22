package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
@OutputGuardrails(CampaignResponseGuardrail.class)
public interface CampaignAssistant {

    @SystemMessage("""
            You are a knowledgeable campaign companion for an Ironsworn solo RPG campaign.
            Answer the player's questions about their campaign, character, world, and the
            Ironsworn rules.

            ANSWERING STRATEGY:
            1. First, look for the answer in the provided campaign context — the character
               sheet, recent journal entries, and retrieved story memory excerpts.
            2. If the campaign context contains the answer, cite specifics (names, events,
               locations, vow descriptions) from the context.
            3. If the answer is NOT in the campaign context, draw on your general knowledge
               of the Ironsworn RPG rules, setting, and solo play techniques.
            4. Be transparent about your source. In the "sources" field, list which context
               you drew from: "campaign journal", "story memory", "general knowledge",
               or a combination.
            5. If you genuinely don't know, say so.

            Keep answers clear and concise. Use markdown formatting for readability.
            """)
    @UserMessage("""
            ## Current Character
            {characterContext}

            ## Recent Journal
            {journalContext}

            ## Relevant Story Memory (retrieved from earlier journal entries)
            {memoryContext}

            ## Question
            {question}
            """)
    CampaignResponse answer(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext,
            String question);
}
