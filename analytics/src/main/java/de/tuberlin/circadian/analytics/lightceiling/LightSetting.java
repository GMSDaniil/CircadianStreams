package de.tuberlin.circadian.analytics.lightceiling;

/**
 * One point of a Dynamic Lighting Therapy schedule.
 *
 * @param hourOfDay   time of day in hours [0,24)
 * @param intensity   relative light intensity [0,1] (1 = full brightness)
 * @param cctKelvin   correlated colour temperature in Kelvin (warm ~2300, cool ~6500)
 */
public record LightSetting(double hourOfDay, double intensity, double cctKelvin) { }