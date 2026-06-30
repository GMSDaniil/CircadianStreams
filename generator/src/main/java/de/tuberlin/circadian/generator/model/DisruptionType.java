package de.tuberlin.circadian.generator.model;

// The kinds of circadian disruption the generator can inject. 
public enum DisruptionType {
    // The rhythm amplitude shrinks toward a target fraction of its baseline (flattening).
    AMPLITUDE_DROP("amplitude_drop"),
    // The acrophase (peak time) shifts by a number of hours (jet-lag-like).
    PHASE_SHIFT("phase_shift"),
    // The coherent 24 h rhythm breaks into short bouts (high intra-daily variability).
    FRAGMENTATION("fragmentation");

    private final String dbName;

    DisruptionType(String dbName) {
        this.dbName = dbName;
    }

    public String dbName() {
        return dbName;
    }

    // Parse from a scenario/db string (e.g. "amplitude_drop"), case-insensitive.
    public static DisruptionType fromString(String s) {
        for (DisruptionType t : values()) {
            if (t.dbName.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown disruption type: " + s);
    }
}