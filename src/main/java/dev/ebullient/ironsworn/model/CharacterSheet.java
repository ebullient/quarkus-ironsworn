package dev.ebullient.ironsworn.model;

import java.util.List;

public record CharacterSheet(
        String name,
        int edge,
        int heart,
        int iron,
        int shadow,
        int wits,
        int health,
        int spirit,
        int supply,
        int momentum,
        List<Vow> vows) {

    public static CharacterSheet defaults(String name) {
        return new CharacterSheet(name, 1, 1, 1, 1, 1, 5, 5, 5, 2, List.of());
    }

    public boolean hasDefaultStats() {
        return edge == 1 && heart == 1 && iron == 1 && shadow == 1 && wits == 1;
    }

    public int stat(String statName) {
        return switch (statName.toLowerCase()) {
            case "edge" -> edge;
            case "heart" -> heart;
            case "iron" -> iron;
            case "shadow" -> shadow;
            case "wits" -> wits;
            default -> throw new IllegalArgumentException("Unknown stat: " + statName);
        };
    }
}
