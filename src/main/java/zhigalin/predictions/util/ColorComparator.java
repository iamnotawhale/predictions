package zhigalin.predictions.util;

import java.awt.Color;

public class ColorComparator {

    public static boolean similarTo(Color home, Color away) {
        int r1 = home.getRed();
        int g1 = home.getGreen();
        int b1 = home.getBlue();

        int r2 = away.getRed();
        int g2 = away.getGreen();
        int b2 = away.getBlue();

        double[] homeCieLab = rgbToCIELAB(r1, g1, b1);
        double[] awayCieLab = rgbToCIELAB(r2, g2, b2);

        double lDiffSquared = (awayCieLab[0] - homeCieLab[0]) * (awayCieLab[0] - homeCieLab[0]);
        double aDiffSquared = (awayCieLab[1] - homeCieLab[1]) * (awayCieLab[1] - homeCieLab[1]);
        double bDiffSquared = (awayCieLab[2] - homeCieLab[2]) * (awayCieLab[2] - homeCieLab[2]);

        double sumSquared = lDiffSquared + aDiffSquared + bDiffSquared;

        double distance = Math.sqrt(sumSquared);

        return distance < 41;
    }

    private static double[] rgbToCIELAB(int red, int green, int blue) {
        double[] xyz = rgbToXYZ(red, green, blue);
        return xyzToCIELAB(xyz[0], xyz[1], xyz[2]);
    }

    private static double[] rgbToXYZ(int red, int green, int blue) {
        double r = red / 255.0;
        double g = green / 255.0;
        double b = blue / 255.0;

        r = (r <= 0.04045) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.04045) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.04045) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        r *= 100;
        g *= 100;
        b *= 100;

        double X = r * 0.4124 + g * 0.3577 + b * 0.1805;
        double Y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double Z = r * 0.0193 + g * 0.1192 + b * 0.9505;

        return new double[]{X, Y, Z};
    }

    private static double[] xyzToCIELAB(double x, double y, double z) {
        double xref = 95.044;
        double yref = 100.0;
        double zref = 108.755;

        x /= xref;
        y /= yref;
        z /= zref;

        x = (x > 0.008856) ? Math.pow(x, 1 / 3.0) : (7.787 * x + 16.0 / 116.0);
        y = (y > 0.008856) ? Math.pow(y, 1 / 3.0) : (7.787 * y + 16.0 / 116.0);
        z = (z > 0.008856) ? Math.pow(z, 1 / 3.0) : (7.787 * z + 16.0 / 116.0);

        double L = 116.0 * y - 16.0;
        double a = 500.0 * (x - y);
        double b = 200.0 * (y - z);

        return new double[]{L, a, b};
    }
}
