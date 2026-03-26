package dev.ebullient.ironsworn.chat.assistant;

import dev.ebullient.ironsworn.chat.response.PlayResponse;
import dev.ebullient.ironsworn.chat.response.PlayResponseGuardrail;
import dev.ebullient.ironsworn.memory.StoryMemoryTool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class, tools = StoryMemoryTool.class)
@OutputGuardrails(PlayResponseGuardrail.class)
public interface PlayAssistant {

    @SystemMessage(fromResource = "prompts/play-narrator-system.txt")
    @UserMessage(fromResource = "prompts/play-narrate-user.txt")
    PlayResponse narrate(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext,
            String playerInput,
            String choiceInstruction);

    @SystemMessage(fromResource = "prompts/play-narrator-system.txt")
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
            String memoryContext,
            String choiceInstruction);

    @SystemMessage(fromResource = "prompts/play-narrator-system.txt")
    @UserMessage(fromResource = "prompts/play-inspire-user.txt")
    PlayResponse inspire(
            @MemoryId String campaignId,
            String oracleResult,
            String characterContext,
            String journalContext,
            String memoryContext,
            String choiceInstruction);
}
