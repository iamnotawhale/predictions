package zhigalin.predictions.service.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DraftKings team goal totals from ESPN propBets ({@code Team Total Goals}).
 * Main line = target where the two quoted prices are closest (typically 1.5).
 */
@Component
public class EspnTeamTotalsClient {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final String PROP_BETS_URL =
            "https://sports.core.api.espn.com/v2/sports/soccer/leagues/eng.1/events/%s/competitions/%s/odds/100/propBets";
    private static final int MAX_PAGES = 12;

    private final ObjectMapper mapper;

    public EspnTeamTotalsClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record TeamTotals(Double homeLine, Double awayLine) {
    }

    public TeamTotals fetchMainLines(String eventId, String homeEspnTeamId, String awayEspnTeamId) {
        if (blank(eventId) || blank(homeEspnTeamId) || blank(awayEspnTeamId)) {
            return null;
        }
        Map<String, List<Double>> oddsByTargetHome = new HashMap<>();
        Map<String, List<Double>> oddsByTargetAway = new HashMap<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                String url = String.format(PROP_BETS_URL, eventId, eventId) + "?lang=en&region=us&page=" + page;
                HttpResponse<String> response = Unirest.get(url).asString();
                if (response.getStatus() != 200 || response.getBody() == null || response.getBody().isBlank()) {
                    break;
                }
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode items = root.get("items");
                if (items == null || !items.isArray() || items.isEmpty()) {
                    break;
                }
                for (JsonNode item : items) {
                    JsonNode type = item.get("type");
                    if (type == null || !"Team Total Goals".equals(text(type.get("name")))) {
                        continue;
                    }
                    String teamId = teamIdFromRef(item.get("team"));
                    if (teamId == null) {
                        continue;
                    }
                    JsonNode current = item.get("current");
                    if (current == null) {
                        continue;
                    }
                    Double target = decimal(current.get("target"), "value");
                    Double over = decimal(current.get("over"), "decimal");
                    if (over == null) {
                        over = decimal(current.get("over"), "value");
                    }
                    if (target == null || over == null) {
                        continue;
                    }
                    String key = String.format(java.util.Locale.US, "%.1f", target);
                    if (teamId.equals(homeEspnTeamId)) {
                        oddsByTargetHome.computeIfAbsent(key, k -> new ArrayList<>()).add(over);
                    } else if (teamId.equals(awayEspnTeamId)) {
                        oddsByTargetAway.computeIfAbsent(key, k -> new ArrayList<>()).add(over);
                    }
                }
                int pageCount = root.path("pageCount").asInt(1);
                if (page >= pageCount) {
                    break;
                }
                if (pickMainLine(oddsByTargetHome) != null && pickMainLine(oddsByTargetAway) != null) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("ESPN team totals fetch failed for event {}: {}", eventId, e.getMessage());
            return null;
        }
        Double home = pickMainLine(oddsByTargetHome);
        Double away = pickMainLine(oddsByTargetAway);
        if (home == null && away == null) {
            return null;
        }
        return new TeamTotals(home, away);
    }

    /** Visible for tests: choose the most balanced over/under pair's target. */
    static Double pickMainLine(Map<String, List<Double>> oddsByTarget) {
        if (oddsByTarget == null || oddsByTarget.isEmpty()) {
            return null;
        }
        String bestKey = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, List<Double>> entry : oddsByTarget.entrySet()) {
            List<Double> odds = entry.getValue();
            if (odds == null || odds.isEmpty()) {
                continue;
            }
            double score;
            if (odds.size() >= 2) {
                double a = odds.get(0);
                double b = odds.get(1);
                for (int i = 2; i < odds.size(); i++) {
                    // keep the tightest pair among quotes for this line
                    double x = odds.get(i);
                    if (Math.abs(a - b) > Math.abs(a - x)) {
                        b = x;
                    } else if (Math.abs(a - b) > Math.abs(b - x)) {
                        a = x;
                    }
                }
                score = Math.abs(a - b);
            } else {
                score = Math.abs(odds.getFirst() - 2.0) + 1.0;
            }
            if (score < bestScore) {
                bestScore = score;
                bestKey = entry.getKey();
            }
        }
        if (bestKey == null) {
            return null;
        }
        try {
            return Double.parseDouble(bestKey);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String teamIdFromRef(JsonNode teamNode) {
        if (teamNode == null) {
            return null;
        }
        String ref = text(teamNode.get("$ref"));
        if (blank(ref)) {
            return text(teamNode.get("id"));
        }
        String path = ref.split("\\?")[0];
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static Double decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
