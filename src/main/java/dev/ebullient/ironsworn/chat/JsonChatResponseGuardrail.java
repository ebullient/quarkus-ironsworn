package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

@ApplicationScoped
public class JsonChatResponseGuardrail implements OutputGuardrail {

    /**
     * The default message to use when reprompting (JsonExtractorOutputGuardrail)
     */
    public static final String REPROMPT_MESSAGE = "Invalid JSON";

    /**
     * The default prompt to append to the LLM during a reprompt (JsonExtractorOutputGuardrail)
     */
    public static final String REPROMPT_PROMPT = "Make sure you return a valid JSON object following the specified format";

    @Inject
    ObjectMapper objectMapper;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        try {
            JsonChatResponse response = objectMapper.readValue(responseFromLLM.text(), JsonChatResponse.class);
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt(REPROMPT_MESSAGE, e, REPROMPT_PROMPT);
        }
    }
}
