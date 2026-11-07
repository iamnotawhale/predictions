package zhigalin.predictions.miniapp;

import zhigalin.predictions.model.event.CupMatch;

/** Small rounding / labelling helpers for Mini App DTOs. */
final class MiniAppFormat {
    private MiniAppFormat() {
    }

    /** Score to show for a cup match: the latest score, including extra time once it is played. */
    static Integer cupDisplayHome(CupMatch m) {
        return m.getAetHome() != null ? m.getAetHome() : m.getHomeScore();
    }

    static Integer cupDisplayAway(CupMatch m) {
        return m.getAetAway() != null ? m.getAetAway() : m.getAwayScore();
    }

    /** "осн. время 1:1 · пен. 4:3" once a cup tie goes past 90 minutes; points use the 90-minute score. */
    static String cupScoreNote(CupMatch m) {
        if (m == null || m.getAetHome() == null || m.getHomeScore() == null) {
            return null;
        }
        String note = "осн. время " + m.getHomeScore() + ":" + m.getAwayScore();
        if (m.getPenHome() != null && m.getPenAway() != null) {
            note += " · пен. " + m.getPenHome() + ":" + m.getPenAway();
        }
        return note;
    }

    static Double firstNonNull(Double a, Double b) {
        return a != null ? a : b;
    }

    static Double round1(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return null;
        }
        return Math.round(v * 10.0) / 10.0;
    }

    /** Accepts either a 0..1 fraction or an already-scaled percentage. */
    static Double roundPct(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return null;
        }
        double pct = v <= 1.5 ? v * 100.0 : v;
        return Math.round(pct * 10.0) / 10.0;
    }

    static int percent(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    static String competitionLabel(String competition) {
        if (competition == null) {
            return "Cup";
        }
        return switch (competition) {
            case "eng.fa" -> "FA Cup";
            case "eng.league_cup" -> "Carabao Cup";
            case "uefa.champions" -> "UCL";
            case "uefa.europa" -> "UEL";
            case "uefa.europa.conf" -> "UECL";
            default -> competition;
        };
    }
}
