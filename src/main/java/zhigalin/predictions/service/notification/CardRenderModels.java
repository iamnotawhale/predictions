package zhigalin.predictions.service.notification;

import java.util.List;

/** View-models for HTML notification cards. */
public final class CardRenderModels {

    private CardRenderModels() {}

    public record TodayCard(List<TodaySection> sections) {}

    public record TodaySection(String title, List<TodayRow> rows) {}

    public record TodayRow(
            String homeCode,
            String awayCode,
            String homeLogoSrc,
            String awayLogoSrc,
            String kickoff
    ) {}

    public record ReminderCard(
            String accent,
            String badge,
            String homeCode,
            String awayCode,
            String homeLogoSrc,
            String awayLogoSrc,
            String kickoff,
            String homeOdd,
            String drawOdd,
            String awayOdd,
            List<FormChip> homeForm,
            List<FormChip> awayForm,
            List<H2hChip> h2h
    ) {}

    /** One last-5 entry: opponent crest + score from this team's perspective. */
    public record FormChip(String opponentLogoSrc, String score) {}

    /** One H2H entry: both crests + score. */
    public record H2hChip(String homeLogoSrc, String awayLogoSrc, String score) {}

    @Deprecated
    public record FormDot(String result, boolean homeSide) {}

    public record ResultCard(
            String accent,
            String badge,
            String homeCode,
            String awayCode,
            String homeLogoSrc,
            String awayLogoSrc,
            String score,
            String aiKickoffScore,
            List<ResultLine> results
    ) {}

    public record ResultLine(String login, String predict, int points) {}

    public record WeeklyCard(int weekId, List<WeeklyRow> rows) {}

    public record WeeklyRow(int place, String login, int points) {}

    public record YourPredictCard(
            String homeCode,
            String awayCode,
            String homeLogoSrc,
            String awayLogoSrc,
            String weekLabel,
            String kickoff,
            String predictScore
    ) {}
}
