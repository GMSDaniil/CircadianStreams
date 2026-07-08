package de.tuberlin.circadian.analytics.lightceiling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DltScheduleTest {

    private static double acrophaseForPeakHour(double peakHour) {
        return -2.0 * Math.PI * peakHour / 24.0;
    }

    @Test
    void acrophaseMapsBackToPeakHour() {
        assertEquals(15.0, DltSchedule.peakHourFromAcrophase(acrophaseForPeakHour(15.0)), 1e-9);
        assertEquals(3.0, DltSchedule.peakHourFromAcrophase(acrophaseForPeakHour(3.0)), 1e-9);
    }

    @Test
    void brightestLightIsCentredOnThePatientsPeak() {
        int steps = 48;
        List<LightSetting> s = DltSchedule.compute(acrophaseForPeakHour(15.0), 0.8, steps);

        LightSetting brightest = s.get(0);
        for (LightSetting ls : s) {
            if (ls.intensity() > brightest.intensity()) {
                brightest = ls;
            }
        }
        assertEquals(15.0, brightest.hourOfDay(), 24.0 / steps); // within one step
    }

    @Test
    void weakerRhythmGetsDarkerNights() {
        double minStrong = minIntensity(DltSchedule.compute(acrophaseForPeakHour(15.0), 0.9, 48));
        double minWeak = minIntensity(DltSchedule.compute(acrophaseForPeakHour(15.0), 0.1, 48));
        assertTrue(minWeak < minStrong, "weak floor " + minWeak + " should be below strong " + minStrong);
    }

    @Test
    void nightsAreWarmerThanDaysAndWeakRhythmGetsBluerDays() {
        List<LightSetting> s = DltSchedule.compute(acrophaseForPeakHour(15.0), 0.8, 48);
        double dayCct = cctAtHour(s, 15.0);   // at the peak
        double nightCct = cctAtHour(s, 3.0);  // 12 h away
        assertTrue(nightCct < dayCct, "night " + nightCct + " should be warmer (lower K) than day " + dayCct);

        double dayCctWeak = cctAtHour(DltSchedule.compute(acrophaseForPeakHour(15.0), 0.1, 48), 15.0);
        double dayCctStrong = cctAtHour(DltSchedule.compute(acrophaseForPeakHour(15.0), 0.9, 48), 15.0);
        assertTrue(dayCctWeak > dayCctStrong, "disrupted day CCT should be cooler/bluer");
    }

    @Test
    void valuesStayInSaneRanges() {
        for (LightSetting ls : DltSchedule.compute(acrophaseForPeakHour(9.0), 0.5, 96)) {
            assertTrue(ls.intensity() >= 0.0 && ls.intensity() <= 1.0, "intensity " + ls.intensity());
            assertTrue(ls.cctKelvin() >= 2000.0 && ls.cctKelvin() <= 7000.0, "cct " + ls.cctKelvin());
        }
    }

    private static double minIntensity(List<LightSetting> s) {
        return s.stream().mapToDouble(LightSetting::intensity).min().orElseThrow();
    }

    private static double cctAtHour(List<LightSetting> s, double hour) {
        return s.stream().min((a, b) -> Double.compare(Math.abs(a.hourOfDay() - hour), Math.abs(b.hourOfDay() - hour))).orElseThrow().cctKelvin();
    }
}