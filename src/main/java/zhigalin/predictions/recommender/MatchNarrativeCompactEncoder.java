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

        if (homeTeam != null) {
            lines.add(formLine(homeTeam));
        }
        if (awayTeam != null) {
            lines.add(formLine(awayTeam));
        }

        String homeFacts = venueFacts(homeName, true, homeTeam);
        String awayFacts = venueFacts(awayName, false, awayTeam);
        if (homeFacts != null || awayFacts != null) {
            lines.add("FACTS");
            if (homeFacts != null) {
                lines.add(homeFacts);
            }
            if (awayFacts != null) {
                lines.add(awayFacts);
            }
        }

        return String.join("\n", lines);
    }

    static String venueFacts(String teamName, boolean homeVenue, TeamStatsSnapshot team) {
        if (team == null || teamName == null || teamName.isBlank()) {
            return null;
        }
        ExtendedTeamMetrics e = team.extendedOrEmpty();
        Double xg = homeVenue ? team.xgHome() : team.xgAway();
        double gf = homeVenue ? team.scoredHome() : team.scoredAway();
        double ga = homeVenue ? team.concededHome() : team.concededAway();
        Double ppg = homeVenue ? e.homePpg() : e.awayPpg();
        boolean hasSample = PoissonScoreModel.hasVenueSample(gf, ga);
        if (xg == null && ppg == null && !hasSample) {
            return null;
        }
        String where = homeVenue ? "дома" : "в гостях";
        StringBuilder sb = new StringBuilder();
        sb.append(esc(teamName)).append(' ').append(where).append(':');
        boolean first = true;
        if (xg != null) {
            sb.append(" xG ").append(num(xg));
            first = false;
        }
        if (ppg != null) {
            sb.append(first ? ' ' : ", ").append(num(ppg)).append(" очков за матч");
            first = false;
        }
        if (hasSample) {
            sb.append(first ? ' ' : ", ").append(num(gf)).append(" голов за матч");
        }
        sb.append('.');
        return sb.toString();
    }

    private static String formLine(TeamStatsSnapshot team) {
        ExtendedTeamMetrics e = team.extendedOrEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("T.").append(team.teamCode()).append("|form=overall");
        sb.append("|gfPm=").append(num(team.scoredOverall()));
        sb.append("|gaPm=").append(num(team.concededOverall()));
        if (e.ssForm8Ppg() != null || e.ssForm8GfPm() != null) {
            sb.append("|f8Ppg=").append(num(e.ssForm8Ppg()));
            sb.append("|f8GfPm=").append(num(e.ssForm8GfPm()));
            sb.append("|f8GaPm=").append(num(e.ssForm8GaPm()));
        }
        append(sb, "o25%", e.over25Overall());
        append(sb, "btts%", e.seasonBttsOverall());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String key, Double value) {
        if (value == null) {
            return;
        }
        sb.append('|').append(key).append('=').append(num(value));
    }

    private static String num(Double value) {
        if (value == null) {
            return "-";
        }
        return num(value.doubleValue());
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
