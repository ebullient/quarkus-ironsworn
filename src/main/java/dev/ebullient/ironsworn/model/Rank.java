package dev.ebullient.ironsworn.model;

public enum Rank {
    TROUBLESOME(3),
    DANGEROUS(2),
    FORMIDABLE(2),
    EXTREME(1),
    EPIC(1);

    private final int progressPerMark;

    Rank(int progressPerMark) {
        this.progressPerMark = progressPerMark;
    }

    /** How many progress boxes to fill per "mark progress" */
    public int progressPerMark() {
        return progressPerMark;
    }
}
