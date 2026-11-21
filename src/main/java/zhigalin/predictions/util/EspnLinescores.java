package zhigalin.predictions.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Competitor.LineScore;
import zhigalin.predictions.model.v2.Event;

public final class EspnLinescores {

    private EspnLinescores() {
    }

    public static HtScore fromEvent(Event event) {
        if (event == null || event.getCompetitions() == null || event.getCompetitions().isEmpty()) {
            return null;
        }
        return fromCompetitors(event.getCompetitions().getFirst().getCompetitors());
    }

    public static HtScore fromCompetitors(List<Competitor> competitors) {
        if (competitors == null || competitors.size() < 2) {
            return null;
        }
        Integer home = null;
        Integer away = null;
        for (Competitor competitor : competitors) {
            if (competitor == null || competitor.getHomeAway() == null) {
                continue;
            }
            Integer firstPeriod = firstPeriodGoals(competitor.getLinescores());
            if (firstPeriod == null) {
                continue;
            }
            if ("home".equalsIgnoreCase(competitor.getHomeAway())) {
                home = firstPeriod;
            } else if ("away".equalsIgnoreCase(competitor.getHomeAway())) {
                away = firstPeriod;
            }
        }
        if (home == null || away == null) {
            return null;
        }
        return new HtScore(home, away);
    }

    public static HtScore fromSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode competitors = root.path("header").path("competitions").path(0).path("competitors");
        if (!competitors.isArray() || competitors.size() < 2) {
            return null;
        }
        Integer home = null;
        Integer away = null;
        for (JsonNode competitor : competitors) {
            String homeAway = competitor.path("homeAway").asText(null);
            Integer firstPeriod = firstPeriodGoals(competitor.path("linescores"));
            if (homeAway == null || firstPeriod == null) {
                continue;
            }
            if ("home".equalsIgnoreCase(homeAway)) {
                home = firstPeriod;
            } else if ("away".equalsIgnoreCase(homeAway)) {
                away = firstPeriod;
            }
        }
        if (home == null || away == null) {
            return null;
        }
        return new HtScore(home, away);
    }

    private static Integer firstPeriodGoals(List<LineScore> linescores) {
        if (linescores == null || linescores.isEmpty()) {
            return null;
        }
        return parseGoals(linescores.getFirst());
    }

    private static Integer firstPeriodGoals(JsonNode linescores) {
        if (linescores == null || !linescores.isArray() || linescores.isEmpty()) {
            return null;
        }
        JsonNode first = linescores.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (first.has("value") && first.get("value").isNumber()) {
            return first.get("value").asInt();
        }
        String display = first.path("displayValue").asText(null);
        return parseInt(display);
    }

    private static Integer parseGoals(LineScore lineScore) {
        if (lineScore == null) {
            return null;
        }
        if (lineScore.getValue() != null) {
            return (int) Math.round(lineScore.getValue());
        }
        return parseInt(lineScore.getDisplayValue());
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record HtScore(int home, int away) {
    }
}
