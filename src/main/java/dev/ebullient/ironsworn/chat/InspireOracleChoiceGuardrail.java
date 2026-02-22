package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

@ApplicationScoped
public class InspireOracleChoiceGuardrail implements OutputGuardrail {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        try {
            InspireOracleChoice response = objectMapper.readValue(responseFromLLM.text(), InspireOracleChoice.class);
            if (response.collectionKey() == null || response.collectionKey().isBlank()) {
                return reprompt("Missing collectionKey", new IllegalArgumentException("collectionKey is blank"),
                        "Return valid JSON with fields: collectionKey (string), tableKey (string), reason (string).");
            }
            if (response.tableKey() == null || response.tableKey().isBlank()) {
                return reprompt("Missing tableKey", new IllegalArgumentException("tableKey is blank"),
                        "Return valid JSON with fields: collectionKey (string), tableKey (string), reason (string).");
            }
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt("Invalid JSON", e,
                    "Make sure you return a valid JSON object with fields: collectionKey (string), tableKey (string), reason (string)");
        }
    }
}
