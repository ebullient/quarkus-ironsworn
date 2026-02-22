package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
@OutputGuardrails(PlayResponseGuardrail.class)
public interface PlayAssistant {

    @SystemMessage(fromResource = "prompts/play-narrate-system.txt")
    @UserMessage(fromResource = "prompts/play-narrate-user.txt")
    PlayResponse narrate(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext,
            String playerInput);

    @SystemMessage(fromResource = "prompts/play-narrateMoveResult-system.txt")
    @UserMessage(fromResource = "prompts/play-narrateMoveResult-user.txt")
    PlayResponse narrateMoveResult(
            @MemoryId String campaignId,
            String moveName,
            String outcome,
            int actionScore,
            int challenge1,
            int challenge2,
            String moveOutcomeText,
            String journalContext,
            String memoryContext);

    @SystemMessage(fromResource = "prompts/play-inspire-system.txt")
    @UserMessage(fromResource = "prompts/play-inspire-user.txt")
    PlayResponse inspire(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext);
}
