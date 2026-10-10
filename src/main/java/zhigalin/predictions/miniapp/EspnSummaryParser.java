package zhigalin.predictions.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormationPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LineupPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchEventItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PlayerStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamFormationItem;

/** Pure parsers turning an ESPN match-summary JSON tree into Mini App DTOs. */
final class EspnSummaryParser {
    private static final Logger log = LoggerFactory.getLogger("server");

    private EspnSummaryParser() {
    }

    static List<LineupPlayerItem> toLineupItemsFromFormation(TeamFormationItem formation) {
        if (formation == null || formation.starters() == null || formation.starters().isEmpty()) {
            return List.of();
        }
        return formation.starters().stream()
                .map(p -> new LineupPlayerItem(p.number(), p.name(), p.position()))
                .toList();
    }

    static TeamFormationItem loadTeamFormation(JsonNode root, String side, String teamCode) {
        if (root == null) {
            return null;
        }
        JsonNode rosters = root.path("rosters");
        if (!rosters.isArray()) {
            return null;
        }
        for (JsonNode rosterNode : rosters) {
            if (!side.equalsIgnoreCase(rosterNode.path("homeAway").asText(""))) {
                continue;
            }
            String formation = rosterNode.path("formation").asText("").trim();
            String kitColor = rosterNode.path("uniform").path("color").asText("").trim();
            if (!kitColor.isBlank() && !kitColor.startsWith("#")) {
                kitColor = "#" + kitColor;
            }
            List<FormationPlayerItem> starters = new ArrayList<>();
            List<FormationPlayerItem> bench = new ArrayList<>();
            JsonNode roster = rosterNode.path("roster");
            if (roster.isArray()) {
                for (JsonNode athleteNode : roster) {
                    FormationPlayerItem player = toFormationPlayer(athleteNode);
                    if (player == null) {
                        continue;
                    }
                    if (player.starter()) {
                        starters.add(player);
                    } else {
                        bench.add(player);
                    }
                }
            }
            starters.sort(Comparator.comparingInt(FormationPlayerItem::formationPlace));
            if (starters.isEmpty() && formation.isBlank()) {
                return null;
            }
            return new TeamFormationItem(side, teamCode, formation, kitColor, starters, bench);
        }
        return null;
    }

    private static FormationPlayerItem toFormationPlayer(JsonNode athleteNode) {
        JsonNode athlete = athleteNode.path("athlete");
        String name = athlete.path("displayName").asText("").trim();
        if (name.isBlank()) {
            return null;
        }
        String lastName = athlete.path("lastName").asText("").trim();
        if (lastName.isBlank()) {
            lastName = shortLastName(name);
        }
        String shortName = athlete.path("shortName").asText("").trim();
        if (shortName.isBlank()) {
            shortName = lastName;
        }
        List<PlayerStatItem> stats = new ArrayList<>();
        JsonNode statsNode = athleteNode.path("stats");
        if (statsNode.isArray()) {
            for (JsonNode stat : statsNode) {
                String value = stat.path("displayValue").asText("").trim();
                if (value.isBlank()) {
                    continue;
                }
                stats.add(new PlayerStatItem(
                        stat.path("name").asText(""),
                        stat.path("displayName").asText(""),
                        stat.path("abbreviation").asText(""),
                        value
                ));
            }
        }
        String subPartnerId = "";
        String subPartnerName = "";
        if (athleteNode.path("subbedOut").asBoolean(false)) {
            subPartnerId = athleteNode.path("subbedOutFor").path("athlete").path("id").asText("").trim();
            subPartnerName = athleteNode.path("subbedOutFor").path("athlete").path("displayName").asText("").trim();
        } else if (athleteNode.path("subbedIn").asBoolean(false)) {
            subPartnerId = athleteNode.path("subbedInFor").path("athlete").path("id").asText("").trim();
            subPartnerName = athleteNode.path("subbedInFor").path("athlete").path("displayName").asText("").trim();
        }
        return new FormationPlayerItem(
                athlete.path("id").asText(""),
                athleteNode.path("jersey").asInt(0),
                name,
                shortName,
                lastName,
                athleteNode.path("position").path("abbreviation").asText("").trim(),
                athleteNode.path("position").path("displayName").asText("").trim(),
                athleteNode.path("formationPlace").asInt(0),
                athleteNode.path("starter").asBoolean(false),
                athleteNode.path("subbedOut").asBoolean(false),
                athleteNode.path("subbedIn").asBoolean(false),
                subPartnerId,
                subPartnerName,
                jerseyImageUrl(athlete.path("jerseyImages")),
                intStat(stats, "totalGoals"),
                intStat(stats, "goalAssists"),
                intStat(stats, "yellowCards"),
                intStat(stats, "redCards"),
                stats
        );
    }

    private static final int JERSEY_THUMB_PX = 128;

    private static String jerseyImageUrl(JsonNode images) {
        if (!images.isArray()) {
            return null;
        }
        String dark = null;
        String any = null;
        for (JsonNode image : images) {
            String href = image.path("href").asText("").trim();
            if (href.isBlank()) {
                continue;
            }
            any = href;
            JsonNode rel = image.path("rel");
            if (rel.isArray()) {
                for (JsonNode r : rel) {
                    if ("dark".equalsIgnoreCase(r.asText(""))) {
                        dark = href;
                    }
                }
            }
            if (href.contains("darkMode=true")) {
                dark = href;
            }
        }
        return withJerseyThumbSize(dark != null ? dark : any);
    }

    /**
     * ESPN stitcher serves full 1440px jerseys (~200KB+); request a small thumb for formation dots.
     */
    static String withJerseyThumbSize(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String trimmed = href.trim();
        if (trimmed.contains("width=") || trimmed.contains("height=")) {
            return trimmed;
        }
        char sep = trimmed.indexOf('?') >= 0 ? '&' : '?';
        return trimmed + sep + "width=" + JERSEY_THUMB_PX + "&height=" + JERSEY_THUMB_PX;
    }

    private static Integer intStat(List<PlayerStatItem> stats, String name) {
        for (PlayerStatItem stat : stats) {
            if (name.equals(stat.name())) {
                try {
                    return Integer.parseInt(stat.value());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String shortLastName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 0 ? fullName : parts[parts.length - 1];
    }

    static List<MatchEventItem> loadLiveEvents(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        try {
            JsonNode commentary = root.path("commentary");
            if (!commentary.isArray() || commentary.isEmpty()) {
                return List.of();
            }
            List<LiveCommentaryEvent> parsed = new ArrayList<>();
            for (int i = 0; i < commentary.size(); i++) {
                JsonNode item = commentary.get(i);
                String text = item.path("text").asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                String minute = item.path("time").path("displayValue").asText("").trim();
                String type = item.path("play").path("type").path("type").asText("").trim();
                if (type.isBlank()) {
                    type = "comment";
                }
                if (shouldSkipCommentaryItem(text, type, minute)) {
                    continue;
                }
                double timeValue = item.path("time").path("value").asDouble(-1d);
                long sequence = item.path("sequence").asLong(i);
                int period = item.path("play").path("period").path("number").asInt(0);
                Double fieldX = item.path("play").path("fieldPositionX").isNumber()
                        ? item.path("play").path("fieldPositionX").asDouble()
                        : null;
                Double fieldY = item.path("play").path("fieldPositionY").isNumber()
                        ? item.path("play").path("fieldPositionY").asDouble()
                        : null;
                Double field2X = item.path("play").path("fieldPosition2X").isNumber()
                        ? item.path("play").path("fieldPosition2X").asDouble()
                        : null;
                Double field2Y = item.path("play").path("fieldPosition2Y").isNumber()
                        ? item.path("play").path("fieldPosition2Y").asDouble()
                        : null;
                Double goalPositionY = item.path("play").path("goalPositionY").isNumber()
                        ? item.path("play").path("goalPositionY").asDouble()
                        : null;
                String teamName = item.path("play").path("team").path("displayName").asText("").trim();
                String shortText = item.path("play").path("shortText").asText("").trim();
                String playerName = extractPrimaryParticipantName(item.path("play"));
                parsed.add(new LiveCommentaryEvent(
                        period, timeValue, sequence, minute, text, type,
                        fieldX, fieldY, field2X, field2Y, goalPositionY, teamName, shortText, playerName
                ));
            }
            if (parsed.isEmpty()) {
                return List.of();
            }
            parsed.sort(Comparator
                    .comparingInt(LiveCommentaryEvent::period)
                    .thenComparingDouble(LiveCommentaryEvent::timeValue)
                    .thenComparingLong(LiveCommentaryEvent::sequence)
                    .reversed());
            return parsed.stream()
                    .limit(22)
                    .map(item -> new MatchEventItem(
                            item.minute(),
                            item.text(),
                            item.type(),
                            item.period() > 0 ? item.period() : null,
                            item.fieldX(),
                            item.fieldY(),
                            item.field2X(),
                            item.field2Y(),
                            item.goalPositionY(),
                            item.teamName(),
                            item.shortText(),
                            item.playerName()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("MiniApp live commentary parse failed: error={}", e.getMessage());
            return List.of();
        }
    }

    private static String extractPrimaryParticipantName(JsonNode play) {
        JsonNode participants = play.path("participants");
        if (!participants.isArray() || participants.isEmpty()) {
            return "";
        }
        String displayName = participants.get(0).path("athlete").path("displayName").asText("").trim();
        if (!displayName.isBlank()) {
            return displayName;
        }
        String lastName = participants.get(0).path("athlete").path("lastName").asText("").trim();
        return lastName;
    }

    static List<MatchStatItem> loadLiveStats(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        JsonNode teams = root.path("boxscore").path("teams");
        if (!teams.isArray() || teams.size() < 2) {
            return List.of();
        }
        Map<String, String> homeStats = extractStatsMap(teams.get(0).path("statistics"));
        Map<String, String> awayStats = extractStatsMap(teams.get(1).path("statistics"));
        if (homeStats.isEmpty() && awayStats.isEmpty()) {
            return List.of();
        }
        return List.of(
                statPercent("possessionPct", "possession", "Владение", homeStats, awayStats),
                stat("totalShots", "shotsTotal", "Удары", homeStats, awayStats),
                stat("shotsOnTarget", null, "В створ", homeStats, awayStats),
                stat("foulsCommitted", null, "Фолы", homeStats, awayStats),
                stat("offsides", null, "Офсайды", homeStats, awayStats),
                stat("wonCorners", "cornerKicks", "Угловые", homeStats, awayStats),
                stat("yellowCards", null, "ЖК", homeStats, awayStats),
                stat("redCards", null, "КК", homeStats, awayStats)
        );
    }

    private static MatchStatItem stat(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                pickStatValue(home, primaryKey, fallbackKey),
                pickStatValue(away, primaryKey, fallbackKey)
        );
    }

    private static MatchStatItem statPercent(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                formatPercent(pickStatValue(home, primaryKey, fallbackKey)),
                formatPercent(pickStatValue(away, primaryKey, fallbackKey))
        );
    }

    private static String pickStatValue(Map<String, String> map, String primaryKey, String fallbackKey) {
        String value = map.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = fallbackKey != null ? map.get(fallbackKey) : null;
        }
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private static String formatPercent(String value) {
        if ("—".equals(value)) {
            return value;
        }
        if (value.endsWith("%")) {
            return value;
        }
        return value + "%";
    }

    private static Map<String, String> extractStatsMap(JsonNode statsNode) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!statsNode.isArray()) {
            return map;
        }
        for (int i = 0; i < statsNode.size(); i++) {
            JsonNode item = statsNode.get(i);
            String name = item.path("name").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            String value = item.path("displayValue").asText("").trim();
            if (value.isBlank()) {
                value = item.path("value").asText("").trim();
            }
            if (!value.isBlank()) {
                map.put(name, value);
            }
        }
        return map;
    }

    private static boolean shouldSkipCommentaryItem(String text, String type, String minute) {
        String normalizedText = text.toLowerCase();
        String normalizedType = type.toLowerCase();
        if (normalizedText.contains("lineups are announced")) {
            return true;
        }
        return minute.isBlank()
                && (normalizedType.contains("period") || normalizedType.contains("kickoff"));
    }

    private record LiveCommentaryEvent(
            int period,
            double timeValue,
            long sequence,
            String minute,
            String text,
            String type,
            Double fieldX,
            Double fieldY,
            Double field2X,
            Double field2Y,
            Double goalPositionY,
            String teamName,
            String shortText,
            String playerName
    ) {
    }

    static String pitchColorFromFormation(TeamFormationItem formation, String fallback) {
        if (formation != null && formation.kitColor() != null && !formation.kitColor().isBlank()) {
            return formation.kitColor();
        }
        return fallback;
    }
}
