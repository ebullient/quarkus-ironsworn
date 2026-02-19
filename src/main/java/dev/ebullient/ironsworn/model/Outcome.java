package dev.ebullient.ironsworn.model;

public enum Outcome {
    STRONG_HIT("Strong Hit"),
    WEAK_HIT("Weak Hit"),
    MISS("Miss");

    private final String display;

    Outcome(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
