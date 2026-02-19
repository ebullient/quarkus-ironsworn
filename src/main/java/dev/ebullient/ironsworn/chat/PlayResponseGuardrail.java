package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

@ApplicationScoped
public class PlayResponseGuardrail implements OutputGuardrail {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        try {
            PlayResponse response = objectMapper.readValue(responseFromLLM.text(), PlayResponse.class);
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt("Invalid JSON", e,
                    "Make sure you return a valid JSON object following the specified format");
        }
    }
}
