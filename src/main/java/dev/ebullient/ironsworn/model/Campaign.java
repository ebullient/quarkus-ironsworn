package dev.ebullient.ironsworn.model;

import java.nio.file.Path;

public record Campaign(
        String id,
        String name,
        Path journalPath) {
}
