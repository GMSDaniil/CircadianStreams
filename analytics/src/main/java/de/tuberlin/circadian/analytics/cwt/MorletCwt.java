package de.tuberlin.circadian.analytics.cwt;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Hand-written continuous wavelet transform with a complex Morlet wavelet. There is no production-quality CWT library for Java, so this implements the FFT/convolution-theorem approach
 * (Torrence &amp; Compo, 1998): the signal is transformed once, multiplied by the analytic frequency-domain wavelet at each scale, and inverse-transformed.
 */
public final class MorletCwt {

    private final int n;
    private final double dt;            // sample spacing, hours
    private final double[] periods;     // Fourier period of each scale, hours
    private final double[][] psiHat;    // [scale][freqBin] real frequency-domain wavelet
    private final DoubleFFT_1D fft;

    // Pre-aggregated per-frequency scale weights for the fast CRI
    private final double[] gTotal;
    private double[] gBand;
    private double cachedBandLo = Double.NaN;
    private double cachedBandHi = Double.NaN;

    /**
     * @param n               window length (samples)
     * @param dtHours         sample spacing in hours (1-min aggregates => 1/60)
     * @param omega0          Morlet central frequency (commonly 6)
     * @param minPeriodHours  shortest Fourier period to resolve (e.g. 0.5)
     * @param maxPeriodHours  longest Fourier period to resolve (e.g. 48)
     * @param voicesPerOctave scale resolution (e.g. 8 or 12)
     */
    public MorletCwt(int n, double dtHours, double omega0, double minPeriodHours, double maxPeriodHours, int voicesPerOctave) {
        this.n = n;
        this.dt = dtHours;
        this.fft = new DoubleFFT_1D(n);

        // Angular frequencies for each FFT bin (negative for the upper half).
        double[] omega = new double[n];
        for (int k = 0; k < n; k++) {
            int kk = (k <= n / 2) ? k : k - n;
            omega[k] = 2.0 * Math.PI * kk / (n * dt);
        }

        // Log-spaced scales between the requested periods. For Morlet, period ~= scale; the exact
        // scale<->period relation is period = 4*pi*s / (omega0 + sqrt(2 + omega0^2)).
        double scalePerPeriod = (omega0 + Math.sqrt(2.0 + omega0 * omega0)) / (4.0 * Math.PI);
        int octaves = (int) Math.floor(voicesPerOctave * (Math.log(maxPeriodHours / minPeriodHours) / Math.log(2.0)));
        int numScales = octaves + 1;

        this.periods = new double[numScales];
        this.psiHat = new double[numScales][n];
        double piQuarter = Math.pow(Math.PI, -0.25);

        for (int j = 0; j < numScales; j++) {
            double period = minPeriodHours * Math.pow(2.0, (double) j / voicesPerOctave);
            double s = scalePerPeriod * period;
            periods[j] = period;
            double norm = Math.sqrt(2.0 * Math.PI * s / dt) * piQuarter;
            for (int k = 0; k < n; k++) {
                double w = omega[k];
                if (w > 0.0) {
                    double arg = s * w - omega0;
                    psiHat[j][k] = norm * Math.exp(-0.5 * arg * arg);
                } // else 0: the analytic wavelet has no negative-frequency support
            }
        }

        // Collapse the total-power scale sum into one per-frequency weight so the CRI denominator is a single dot product at run time (sum_s |psiHat_s|^2), instead of a sum over all scales.
        this.gTotal = new double[n];
        for (int j = 0; j < numScales; j++) {
            for (int k = 0; k < n; k++) {
                gTotal[k] += psiHat[j][k] * psiHat[j][k];
            }
        }
    }

    /** Fourier period (hours) of each scale, shortest first. */
    public double[] periods() {
        return periods.clone();
    }

    /**
     * Power scalogram |W(scale, t)|^2, shape [numScales][n].
     */
    public double[][] power(double[] signal) {
        if (signal.length != n) {
            throw new IllegalArgumentException("signal length " + signal.length + " != window " + n);
        }
        double mean = 0.0;
        for (double v : signal) {
            mean += v;
        }
        mean /= n;

        // Forward FFT once (interleaved complex: [re0, im0, re1, im1, ...]).
        double[] spectrum = new double[2 * n];
        for (int i = 0; i < n; i++) {
            spectrum[2 * i] = signal[i] - mean;
        }
        fft.complexForward(spectrum);

        double[][] power = new double[periods.length][n];
        for (int j = 0; j < periods.length; j++) {
            double[] band = psiHat[j];
            double[] w = new double[2 * n];
            for (int k = 0; k < n; k++) {
                double g = band[k];
                w[2 * k] = spectrum[2 * k] * g;
                w[2 * k + 1] = spectrum[2 * k + 1] * g;
            }
            fft.complexInverse(w, true);
            for (int i = 0; i < n; i++) {
                double re = w[2 * i];
                double im = w[2 * i + 1];
                power[j][i] = re * re + im * im;
            }
        }
        return power;
    }

    /**
     * Circadian Rhythm Index: total scalogram power in the period band
     */
    public double circadianRhythmIndex(double[] signal, double bandLowHours, double bandHighHours) {
        double[][] power = power(signal);
        double bandPower = 0.0;
        double totalPower = 0.0;
        for (int j = 0; j < periods.length; j++) {
            double scaleSum = 0.0;
            for (int i = 0; i < n; i++) {
                scaleSum += power[j][i];
            }
            totalPower += scaleSum;
            if (periods[j] >= bandLowHours && periods[j] <= bandHighHours) {
                bandPower += scaleSum;
            }
        }
        return totalPower > 0.0 ? bandPower / totalPower : 0.0;
    }

    /**
     * Same CRI, computed directly in the frequency domain — no inverse FFTs, and no per-scale loop. Two identities collapse the work:
     *
     * <ul>
     *   <li><b>Parseval:</b> the time-summed power of scale {@code s} equals {@code (1/N) * sum_omega |X(omega)|^2 * |psiHat_s(omega)|^2}; the {@code 1/N} is the same for every scale, so it cancels in the band/total ratio.</li>
     *   <li><b>Linearity:</b> the numerator and denominator are linear in {@code |X(omega)|^2}, so the scale sums fold into two fixed per-frequency weights,
     *       {@code gBand(omega)=sum_{s in band}|psiHat_s|^2} and {@code gTotal(omega)=sum_s|psiHat_s|^2}, precomputed once from the wavelet bank.</li>
     * </ul>
     */
    public double circadianRhythmIndexFast(double[] signal, double bandLowHours, double bandHighHours) {
        if (signal.length != n)
            throw new IllegalArgumentException("signal length " + signal.length + " != window " + n);

        ensureBandWeights(bandLowHours, bandHighHours);

        double mean = 0.0;
        for (double v : signal)
            mean += v;

        mean /= n;

        double[] spectrum = new double[2 * n];
        for (int i = 0; i < n; i++) 
            spectrum[2 * i] = signal[i] - mean;

        fft.complexForward(spectrum); // one forward FFT

        double bandPower = 0.0;
        double totalPower = 0.0;
        for (int k = 0; k < n; k++) {
            double re = spectrum[2 * k];
            double im = spectrum[2 * k + 1];
            double p = re * re + im * im;      // |X(omega)|^2
            bandPower += p * gBand[k];
            totalPower += p * gTotal[k];
        }
        return totalPower > 0.0 ? bandPower / totalPower : 0.0;
    }

    private void ensureBandWeights(double bandLowHours, double bandHighHours) {
        if (gBand != null && bandLowHours == cachedBandLo && bandHighHours == cachedBandHi)
            return;

        double[] w = new double[n];
        for (int j = 0; j < periods.length; j++) {
            if (periods[j] >= bandLowHours && periods[j] <= bandHighHours) {
                double[] g = psiHat[j];
                for (int k = 0; k < n; k++) 
                    w[k] += g[k] * g[k];
            }
        }
        this.gBand = w;
        this.cachedBandLo = bandLowHours;
        this.cachedBandHi = bandHighHours;
    }
}