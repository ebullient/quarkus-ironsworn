package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

@ApplicationScoped
public class CreationResponseGuardrail implements OutputGuardrail {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        try {
            CreationResponse response = objectMapper.readValue(responseFromLLM.text(), CreationResponse.class);
            if (response.message() == null || response.message().isBlank()) {
                return reprompt("The 'message' field must be present and non-empty.",
                        """
                                Return a valid JSON object with fields: message (string), suggestedVow (string).
                                The message field MUST be non-empty markdown text (1-2 paragraphs).
                                """
                                .trim());
            }
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt("Invalid JSON", e,
                    "Make sure you return a valid JSON object with fields: message (string), suggestedVow (string or null)");
        }
    }
}
