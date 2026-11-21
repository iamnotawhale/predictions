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
        appendPreview(lines, "SS.OV", overallPreview);

        if (homeTeam != null) {
            lines.add(teamLine(homeTeam));
        }
        if (awayTeam != null) {
            lines.add(teamLine(awayTeam));
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
        lines.add(String.join("|",
                tag,
                preview.homeCode(),
                safeScope(preview.homeScope()),
                cellsOrStats(preview.homeCells(), preview.homeStats())
        ));
        lines.add(String.join("|",
                tag,
                preview.awayCode(),
                safeScope(preview.awayScope()),
                cellsOrStats(preview.awayCells(), preview.awayStats())
        ));
    }

    private static String cellsOrStats(List<String> cells, String stats) {
        if (cells != null && !cells.isEmpty()) {
            return String.join(",", cells.stream().map(MatchNarrativeCompactEncoder::esc).toList());
        }
        return esc(stats == null ? "" : stats.replace('·', ',').replace("|", "/"));
    }

    private static String teamLine(TeamStatsSnapshot team) {
        ExtendedTeamMetrics e = team.extendedOrEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("T.").append(team.teamCode());
        sb.append("|s=").append(num(team.scoredOverall())).append('/')
                .append(num(team.scoredHome())).append('/')
                .append(num(team.scoredAway()));
        sb.append("|c=").append(num(team.concededOverall())).append('/')
                .append(num(team.concededHome())).append('/')
                .append(num(team.concededAway()));
        if (team.xgOverall() != null || team.xgaOverall() != null) {
            sb.append("|xg=").append(num(team.xgOverall())).append('/')
                    .append(num(team.xgaOverall()));
        }
        append(sb, "sf%", e.ssScoredFirstPct());
        append(sb, "sfPpg", e.ssScoredFirstPpg());
        append(sb, "cf%", e.ssConcededFirstPct());
        append(sb, "cfPpg", e.ssConcededFirstPpg());
        append(sb, "lead%", e.ssLeadPct());
        append(sb, "trail%", e.ssTrailPct());
        append(sb, "eqS%", e.ssEqualiserScoredPct());
        append(sb, "eqC%", e.ssEqualiserConcededPct());
        append(sb, "ogs%", e.ssOgsPct());
        append(sb, "ogc%", e.ssOgcPct());
        append(sb, "avg1st", e.ssAvgFirstGoalMin());
        append(sb, "favPpg", e.ssFavouritePpg());
        append(sb, "late%", e.ssLateScoredPct());
        append(sb, "lateGA%", e.ssLateConcededPct());
        append(sb, "2hGF%", e.ssSecondHalfGfSharePct());
        append(sb, "avgGF", e.ssAvgGfMinute());
        append(sb, "avgGA", e.ssAvgGaMinute());
        if (e.ssForm8Ppg() != null || e.ssForm8GfPm() != null) {
            sb.append("|f8=").append(num(e.ssForm8Ppg())).append('/')
                    .append(num(e.ssForm8GfPm())).append('/')
                    .append(num(e.ssForm8GaPm()));
            if (e.ssForm8OppPpg() != null) {
                sb.append('/').append(num(e.ssForm8OppPpg()));
            }
        }
        append(sb, "htPpg", e.htPpg());
        append(sb, "winHt%", e.winningAtHtPct());
        append(sb, "2hPpg", e.ssSecondHalfPpg());
        append(sb, "hPpg", e.homePpg());
        append(sb, "aPpg", e.awayPpg());
        append(sb, "btts%", e.seasonBttsOverall());
        append(sb, "cs%", e.seasonCsOverall());
        append(sb, "o25%", e.over25Overall());
        append(sb, "draw%", e.drawPctOverall());
        if (e.recentXgPerMatch() != null) {
            sb.append("|rxg=").append(num(e.recentXgPerMatch())).append('/')
                    .append(num(e.recentXgaPerMatch()));
            if (e.recentXgSamples() != null) {
                sb.append('/').append(e.recentXgSamples());
            }
        }
        append(sb, "xPts", e.xPts());
        append(sb, "xPtsD", e.xPtsDelta());
        return sb.toString();
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
