package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zhigalin.predictions.recommender.model.ExplanationBreakdown;
import zhigalin.predictions.recommender.model.ExtendedTeamMetrics;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
import zhigalin.predictions.util.DaoUtil;

final class MatchNarrativeCompactEncoder {

    private MatchNarrativeCompactEncoder() {
    }

    static String encode(
            String homeCode,
            String awayCode,
            MatchRecommendationSnapshot tip,
            SoccerStatsMatchPreviewScraper.FixturePair fixture,
            SoccerStatsMatchPreviewScraper.PreviewLine homeAwayPreview,
            SoccerStatsMatchPreviewScraper.PreviewLine overallPreview,
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam
    ) {
        List<String> lines = new ArrayList<>();
        String homeName = teamName(homeCode);
        String awayName = teamName(awayCode);
        if (fixture != null) {
            if (fixture.homeLabel() != null && !fixture.homeLabel().isBlank()) {
                homeName = fixture.homeLabel();
            }
            if (fixture.awayLabel() != null && !fixture.awayLabel().isBlank()) {
                awayName = fixture.awayLabel();
            }
        }
        lines.add(String.join("|",
                "M",
                esc(homeName),
                homeCode,
                "home",
                esc(awayName),
                awayCode,
                "away"
        ));

        lines.add(String.format(Locale.US, "TIP|%d:%d|p=%s|lam=%s/%s",
                tip.recommendedHome(),
                tip.recommendedAway(),
                num(tip.scoreProbability()),
                num(tip.expectedHome()),
                num(tip.expectedAway())
        ));

        ExplanationBreakdown expl = tip.explanation();
        if (expl != null && expl.modelScore() != null) {
            ExplanationBreakdown.ModelScore ms = expl.modelScore();
            lines.add(String.format(Locale.US, "MS|%d:%d|p=%s|1x2=%s/%s/%s",
                    ms.home(),
                    ms.away(),
                    num(ms.probability()),
                    num(ms.homeWin()),
                    num(ms.draw()),
                    num(ms.awayWin())
            ));
        }

        appendPreview(lines, "SS.HA", homeAwayPreview);
        if (homeAwayPreview == null) {
            appendPreview(lines, "SS.OV", overallPreview);
        }

        if (homeTeam != null) {
            lines.add(teamLine(homeTeam, true));
        }
        if (awayTeam != null) {
            lines.add(teamLine(awayTeam, false));
        }
        return String.join("\n", lines);
    }

    private static void appendPreview(
            List<String> lines,
            String tag,
            SoccerStatsMatchPreviewScraper.PreviewLine preview
    ) {
        if (preview == null) {
            return;
        }
        lines.add(previewTeamLine(tag, preview.homeCode(), preview.homeScope(),
                preview.homeCells(), preview.homeStats()));
        lines.add(previewTeamLine(tag, preview.awayCode(), preview.awayScope(),
                preview.awayCells(), preview.awayStats()));
    }

    private static String previewTeamLine(
            String tag,
            String code,
            String scope,
            List<String> cells,
            String stats
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(tag).append('|').append(code).append('|').append(safeScope(scope));
        if (cells != null && !cells.isEmpty()) {
            String[] keys = metricKeysFor(cells);
            if (keys != null) {
                for (int i = 0; i < keys.length; i++) {
                    sb.append('|').append(keys[i]).append('=').append(esc(cells.get(i)));
                }
                return sb.toString();
            }
            sb.append('|').append(String.join(",", cells.stream().map(MatchNarrativeCompactEncoder::esc).toList()));
            return sb.toString();
        }
        sb.append('|').append(esc(stats == null ? "" : stats.replace('·', ',').replace("|", "/")));
        return sb.toString();
    }

    private static String teamLine(TeamStatsSnapshot team, boolean homeVenue) {
        ExtendedTeamMetrics e = team.extendedOrEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("T.").append(team.teamCode());
        sb.append("|venue=").append(homeVenue ? "home" : "away");
        if (homeVenue) {
            sb.append("|gfPm=").append(num(team.scoredHome()));
            sb.append("|gaPm=").append(num(team.concededHome()));
            append(sb, "ppg", e.homePpg());
            append(sb, "o25%", e.over25Home());
            append(sb, "btts%", e.seasonBttsHome());
        } else {
            sb.append("|gfPm=").append(num(team.scoredAway()));
            sb.append("|gaPm=").append(num(team.concededAway()));
            append(sb, "ppg", e.awayPpg());
            append(sb, "o25%", e.over25Away());
            append(sb, "btts%", e.seasonBttsAway());
        }
        return sb.toString();
    }

    private static String[] metricKeysFor(List<String> cells) {
        if (cells.size() == 5
                && isPct(cells.get(0)) && isNum(cells.get(1)) && isNum(cells.get(2))
                && isNum(cells.get(3)) && isPct(cells.get(4))) {
            return new String[]{"ptsShare%", "gfPm", "gaPm", "tgPm", "o25%"};
        }
        if (cells.size() == 3 && isPct(cells.get(0)) && isNum(cells.get(1)) && isNum(cells.get(2))) {
            return new String[]{"ptsShare%", "gfPm", "gaPm"};
        }
        if (cells.size() == 2 && isPct(cells.get(0)) && isNum(cells.get(1))) {
            return new String[]{"ptsShare%", "gfPm"};
        }
        return null;
    }

    private static boolean isPct(String raw) {
        if (raw == null) {
            return false;
        }
        String c = raw.trim();
        return c.endsWith("%") && isNum(c.substring(0, c.length() - 1).trim());
    }

    private static boolean isNum(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(raw.trim().replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void append(StringBuilder sb, String key, Double value) {
        if (value == null) {
            return;
        }
        sb.append('|').append(key).append('=').append(num(value));
    }

    private static String safeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "-";
        }
        return scope.toLowerCase(Locale.ROOT);
    }

    private static String num(Double value) {
        if (value == null) {
            return "-";
        }
        double v = value;
        if (Math.rint(v) == v && Math.abs(v) < 1_000_000) {
            return Long.toString((long) v);
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static String num(double value) {
        if (Math.rint(value) == value && Math.abs(value) < 1_000_000) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('|', '/').replace('\n', ' ').trim();
    }

    private static String teamName(String code) {
        if (code == null || DaoUtil.TEAMS == null) {
            return code;
        }
        return DaoUtil.TEAMS.values().stream()
                .filter(t -> code.equalsIgnoreCase(t.getCode()))
                .map(t -> t.getName() != null ? t.getName() : code)
                .findFirst()
                .orElse(code);
    }
}
