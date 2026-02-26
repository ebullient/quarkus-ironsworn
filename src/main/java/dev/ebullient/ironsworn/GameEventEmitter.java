package dev.ebullient.ironsworn;

import java.util.Map;

public interface GameEventEmitter {
    /** Send a fully serialized JSON message to the client immediately. */
    void emit(String json) throws Exception;

    void emit(Map<String, Object> map) throws Exception;

    /** Send transient progress text (replaces loading indicator, replaced by final content). */
    void delta(String text) throws Exception;

    /** Create a JSON error message */
    String errorJson(String message);
}
