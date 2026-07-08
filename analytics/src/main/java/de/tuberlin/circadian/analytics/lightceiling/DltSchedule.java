package de.tuberlin.circadian.analytics.lightceiling;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Lighting Therapy (DLT) demonstrator. Maps a patient's circadian state to a 24 h schedule of light intensity + colour temperature, on the clinical
 * premise that bright, blue-enriched (high-CCT) light by day and dim, warm light by night helps entrain the circadian rhythm.
 */
public final class DltSchedule {
    // Day/night endpoints for a strong (s=1) vs weak (s=0) rhythm.
    private static final double CCT_DAY_STRONG = 5000.0;
    private static final double CCT_DAY_WEAK = 6500.0;   // more blue-enriched = more aggressive
    private static final double CCT_NIGHT_STRONG = 2700.0;
    private static final double CCT_NIGHT_WEAK = 2300.0; // warmer nights
    private static final double NIGHT_FLOOR_STRONG = 0.25;
    private static final double NIGHT_FLOOR_WEAK = 0.05;  // darker nights

    private DltSchedule() { }

    /** The hour of day at which a rhythm with the given acrophase peaks, in [0,24). */
    public static double peakHourFromAcrophase(double acrophaseRad) {
        double h = -acrophaseRad * 24.0 / (2.0 * Math.PI);
        return ((h % 24.0) + 24.0) % 24.0;
    }

    /**
     * Compute the schedule.
     *
     * @param acrophaseRad   the patient's rhythm phase
     * @param rhythmStrength rhythm quality in [0,1] (e.g. CWT CRI); low = disrupted
     * @param stepsPerDay    schedule resolution (e.g. 48 = every 30 min)
     */
    public static List<LightSetting> compute(double acrophaseRad, double rhythmStrength, int stepsPerDay) {
        if (stepsPerDay < 1) {
            throw new IllegalArgumentException("stepsPerDay must be >= 1");
        }
        double s = Math.max(0.0, Math.min(1.0, rhythmStrength));
        double peakHour = peakHourFromAcrophase(acrophaseRad);

        double nightFloor = lerp(NIGHT_FLOOR_WEAK, NIGHT_FLOOR_STRONG, s);
        double cctDay = lerp(CCT_DAY_WEAK, CCT_DAY_STRONG, s);
        double cctNight = lerp(CCT_NIGHT_WEAK, CCT_NIGHT_STRONG, s);

        List<LightSetting> schedule = new ArrayList<>(stepsPerDay);
        for (int i = 0; i < stepsPerDay; i++) {
            double hour = i * 24.0 / stepsPerDay;
            // Raised cosine centred on the patient's peak: 1 at peakHour, 0 twelve hours away.
            double dayShape = 0.5 * (1.0 + Math.cos(2.0 * Math.PI * (hour - peakHour) / 24.0));
            double intensity = nightFloor + (1.0 - nightFloor) * dayShape;
            double cct = cctNight + (cctDay - cctNight) * dayShape;
            schedule.add(new LightSetting(hour, intensity, cct));
        }
        return schedule;
    }

    private static double lerp(double weak, double strong, double s) {
        return weak + (strong - weak) * s;
    }
}