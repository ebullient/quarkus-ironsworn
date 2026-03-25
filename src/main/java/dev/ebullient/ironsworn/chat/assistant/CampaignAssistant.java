package dev.ebullient.ironsworn.chat.assistant;

import dev.ebullient.ironsworn.chat.response.CampaignResponse;
import dev.ebullient.ironsworn.chat.response.CampaignResponseGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
@OutputGuardrails(CampaignResponseGuardrail.class)
public interface CampaignAssistant {

    @SystemMessage(fromResource = "prompts/campaign-answer-system.txt")
    @UserMessage(fromResource = "prompts/campaign-answer-user.txt")
    CampaignResponse answer(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext,
            String question);
}
