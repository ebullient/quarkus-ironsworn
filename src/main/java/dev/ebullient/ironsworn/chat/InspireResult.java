package dev.ebullient.ironsworn.chat;

import dev.ebullient.ironsworn.model.OracleResult;

/**
 * Result of an "Inspire Me" request, containing optional oracle result
 * (null when tool-calling is used) and the narrative response.
 */
public record InspireResult(
        OracleResult oracleResult,
        PlayResponse response,
        String narrative) {
}
