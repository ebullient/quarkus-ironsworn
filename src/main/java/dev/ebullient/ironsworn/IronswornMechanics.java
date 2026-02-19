package dev.ebullient.ironsworn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import Datasworn.Move;
import Datasworn.MoveActionRoll;
import Datasworn.MoveCategory;
import Datasworn.MoveOutcome;
import Datasworn.MoveOutcomes;
import Datasworn.OracleCollection;
import Datasworn.OracleTableRollable;
import Datasworn.OracleTableRowText;
import Datasworn.OracleTablesCollection;
import dev.ebullient.ironsworn.model.OracleResult;
import dev.ebullient.ironsworn.model.Outcome;
import dev.ebullient.ironsworn.model.Rank;

@Singleton
public class IronswornMechanics {

    @Inject
    DataswornService datasworn;

    /**
     * Roll on an oracle table (server generates the roll).
     */
    public OracleResult rollOracle(String collectionKey, String tableKey) {
        OracleTableRollable table = findOracleTable(collectionKey, tableKey);
        int maxRoll = findMaxRoll(table.getRows());
        int roll = ThreadLocalRandom.current().nextInt(1, maxRoll + 1);
        return lookupResult(table, collectionKey, tableKey, roll);
    }

    /**
     * Look up an oracle result for a player-provided roll (physical dice).
     */
    public OracleResult lookupOracleResult(String collectionKey, String tableKey, int roll) {
        OracleTableRollable table = findOracleTable(collectionKey, tableKey);
        return lookupResult(table, collectionKey, tableKey, roll);
    }

    /**
     * Get the rules text for a specific move outcome.
     */
    public String getMoveOutcomeText(String categoryKey, String moveKey, Outcome outcome) {
        Map<String, MoveCategory> moves = datasworn.getMoves();
        MoveCategory category = moves.get(categoryKey);
        if (category == null || category.getContents() == null) {
            return "Move category not found: " + categoryKey;
        }

        Move move = category.getContents().get(moveKey);
        if (move == null) {
            return "Move not found: " + moveKey;
        }

        if (move instanceof MoveActionRoll actionRoll) {
            MoveOutcomes outcomes = actionRoll.getOutcomes();
            if (outcomes == null) {
                return "No outcomes defined for move: " + moveKey;
            }
            MoveOutcome moveOutcome = switch (outcome) {
                case STRONG_HIT -> outcomes.getStrongHit();
                case WEAK_HIT -> outcomes.getWeakHit();
                case MISS -> outcomes.getMiss();
            };
            return moveOutcome != null && moveOutcome.getText() != null
                    ? moveOutcome.getText().getValue()
                    : "No text for outcome: " + outcome;
        }

        return "Move is not an action roll: " + moveKey;
    }

    /**
     * Mark progress on a track. Returns the new progress value (clamped 0-10).
     */
    public int markProgress(int currentProgress, Rank rank) {
        return Math.min(10, currentProgress + rank.progressPerMark());
    }

    // --- Private helpers ---

    private OracleTableRollable findOracleTable(String collectionKey, String tableKey) {
        Map<String, OracleTablesCollection> oracles = datasworn.getOracles();
        OracleTablesCollection collection = oracles.get(collectionKey);
        if (collection == null) {
            throw new IllegalArgumentException("Oracle collection not found: " + collectionKey);
        }

        // Check direct contents first
        if (collection.getContents() != null) {
            OracleTableRollable table = collection.getContents().get(tableKey);
            if (table != null) {
                return table;
            }
        }

        // Check nested sub-collections
        if (collection.getCollections() != null) {
            for (OracleCollection subCollection : collection.getCollections().values()) {
                for (OracleTableRollable table : subCollection.rollableTable()) {
                    if (table.getName() != null && tableKey.equals(StringUtils.slugify(table.getName().getValue()))) {
                        return table;
                    }
                }
            }
        }

        throw new IllegalArgumentException("Oracle table not found: " + collectionKey + "/" + tableKey);
    }

    private OracleResult lookupResult(OracleTableRollable table, String collectionKey, String tableKey, int roll) {
        String collectionName = collectionKey;
        String tableName = table.getName() != null ? table.getName().getValue() : tableKey;

        for (OracleTableRowText row : table.getRows()) {
            if (row.getMin() != null && row.getMax() != null
                    && roll >= row.getMin() && roll <= row.getMax()) {
                String text = row.getText() != null ? row.getText().getValue() : "???";
                return new OracleResult(collectionName, tableName, roll, text);
            }
        }

        return new OracleResult(collectionName, tableName, roll, "No matching result for roll: " + roll);
    }

    private int findMaxRoll(List<OracleTableRowText> rows) {
        int max = 0;
        for (OracleTableRowText row : rows) {
            if (row.getMax() != null && row.getMax() > max) {
                max = row.getMax();
            }
        }
        return max > 0 ? max : 100;
    }
}
