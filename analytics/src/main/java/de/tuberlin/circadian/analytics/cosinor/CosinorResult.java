package de.tuberlin.circadian.analytics.cosinor;

/**
 * Result of a single-component Cosinor fit y = mesor + amplitude*cos(2*pi*t/24 + acrophase)
 *
 * @param mesor        rhythm-adjusted mean
 * @param amplitude    half the peak-to-trough swing
 * @param acrophaseRad phase of the peak, radians
 * @param r2           coefficient of determination of the fit
 */
public record CosinorResult(double mesor, double amplitude, double acrophaseRad, double r2) { }