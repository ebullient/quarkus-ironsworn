package dev.ebullient.ironsworn.model;

public record Vow(
        String description,
        Rank rank,
        int progress) {

    public Vow {
        if (progress < 0 || progress > 10) {
            throw new IllegalArgumentException("Progress must be 0-10, got " + progress);
        }
    }
}
