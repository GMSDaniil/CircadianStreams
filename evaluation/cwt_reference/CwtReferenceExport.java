import de.tuberlin.circadian.analytics.cwt.MorletCwt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Evaluation tool (NOT part of the production build): regenerates the golden CWT reference
 * evaluation/reference/cwt_java_*.csv from the Java MorletCwt, for the
 * Python parity check (evaluation/validate_cwt.py).
 */
public final class CwtReferenceExport {

    private static final int N = 4320;            // 3 days at 1-min resolution
    private static final double DT = 1.0 / 60.0;  // hours per sample
    private static final double OMEGA0 = 6.0;
    private static final int VOICES = 12;
    private static final double BAND_LOW = 20.0;
    private static final double BAND_HIGH = 28.0;

    public static void main(String[] args) throws IOException {
        Path outDir = Paths.get(args.length > 0 ? args[0] : "evaluation/reference");
        Files.createDirectories(outDir);

        MorletCwt cwt = new MorletCwt(N, DT, OMEGA0, 0.5, 48.0, VOICES);
        double[] periods = cwt.periods();

        String[] names = {"s24", "s12", "s24_12", "s24_decay"};
        double[][] sig = new double[names.length][N];
        for (int i = 0; i < N; i++) {
            double tH = i * DT;
            sig[0][i] = Math.cos(2 * Math.PI * tH / 24.0);
            sig[1][i] = Math.cos(2 * Math.PI * tH / 12.0);
            sig[2][i] = Math.cos(2 * Math.PI * tH / 24.0) + 0.5 * Math.cos(2 * Math.PI * tH / 12.0);
            sig[3][i] = (1.0 - 0.8 * i / (N - 1.0)) * Math.cos(2 * Math.PI * tH / 24.0);
        }

        StringBuilder spectrum = new StringBuilder("signal,period,power\n");
        StringBuilder cri = new StringBuilder("signal,cri\n");
        for (int s = 0; s < names.length; s++) {
            double[][] power = cwt.power(sig[s]);
            for (int j = 0; j < periods.length; j++) {
                double mean = 0.0;
                for (int t = 0; t < N; t++) {
                    mean += power[j][t];
                }
                mean /= N;
                spectrum.append(String.format(Locale.ROOT, "%s,%.6f,%.8e\n", names[s], periods[j], mean));
            }
            cri.append(String.format(Locale.ROOT, "%s,%.6f\n",
                    names[s], cwt.circadianRhythmIndex(sig[s], BAND_LOW, BAND_HIGH)));
        }

        Files.writeString(outDir.resolve("cwt_java_spectrum.csv"), spectrum.toString());
        Files.writeString(outDir.resolve("cwt_java_cri.csv"), cri.toString());
        System.out.println("Wrote CWT reference to " + outDir.toAbsolutePath());
    }

    private CwtReferenceExport() {
    }
}