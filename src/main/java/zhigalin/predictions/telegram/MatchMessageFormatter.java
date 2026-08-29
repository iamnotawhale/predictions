package zhigalin.predictions.telegram;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;

public final class MatchMessageFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    public enum Style {
        TODAY,
        TOUR,
        UPCOMING
    }

    private MatchMessageFormatter() {}

    /**
     * Appends home/away/score/status fragment (no surrounding backticks).
     * Returns false if teams are missing.
     */
    public static boolean appendMatchBody(StringBuilder builder, Match match, Style style) {
        Team homeTeam = resolveTeam(match.getHomeTeamId());
        Team awayTeam = resolveTeam(match.getAwayTeamId());
        if (homeTeam == null || awayTeam == null) {
            return false;
        }
        builder.append(homeTeam.getCode()).append(" ");
        switch (style) {
            case TODAY -> appendToday(builder, match, awayTeam);
            case TOUR -> appendTour(builder, match, awayTeam);
            case UPCOMING -> appendUpcoming(builder, match, awayTeam);
        }
        return true;
    }

    private static Team resolveTeam(int teamId) {
        Team team = DaoUtil.team(teamId);
        return team != null ? team : DaoUtil.TEAMS.get(teamId);
    }

    private static void appendToday(StringBuilder builder, Match match, Team awayTeam) {
        if (!Objects.equals(match.getStatus(), "ns") && !Objects.equals(match.getStatus(), "pst")) {
            builder.append(match.getHomeTeamScore()).append(" - ")
                    .append(match.getAwayTeamScore()).append(" ")
                    .append(awayTeam.getCode()).append(" ")
                    .append(match.getStatus()).append(" ");
        } else if (Objects.equals(match.getStatus(), "pst")) {
            builder.append("- ").append(awayTeam.getCode())
                    .append(" ⏰ ").append(match.getStatus());
        } else {
            builder.append("- ").append(awayTeam.getCode())
                    .append(" ⏱ ").append(match.getLocalDateTime().toLocalTime());
        }
    }

    private static void appendTour(StringBuilder builder, Match match, Team awayTeam) {
        if (Objects.equals(match.getStatus(), "ft")) {
            builder.append(match.getHomeTeamScore())
                    .append(" - ")
                    .append(match.getAwayTeamScore())
                    .append(" ")
                    .append(awayTeam.getCode());
        } else if (Objects.equals(match.getStatus(), "ns") || Objects.equals(match.getStatus(), "pst")) {
            builder.append("- ")
                    .append(awayTeam.getCode()).append(" ")
                    .append("\uD83D\uDDD3 ")
                    .append(match.getLocalDateTime().format(DATE_TIME));
        } else {
            builder.append(match.getHomeTeamScore())
                    .append(" - ")
                    .append(match.getAwayTeamScore())
                    .append(" ")
                    .append(awayTeam.getCode())
                    .append(" ")
                    .append(match.getStatus());
        }
    }

    private static void appendUpcoming(StringBuilder builder, Match match, Team awayTeam) {
        builder.append("- ").append(awayTeam.getCode());
        if (Objects.equals(match.getStatus(), "pst")) {
            builder.append(" ⏰ ").append(match.getStatus());
        } else {
            builder.append(" ⏱ ").append(match.getLocalDateTime().format(DATE_TIME));
        }
    }
}
