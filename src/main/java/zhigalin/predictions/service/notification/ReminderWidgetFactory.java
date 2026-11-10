package zhigalin.predictions.service.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.recommender.BettingRecommendationService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.util.DaoUtil;

@Component
public class ReminderWidgetFactory {

    private static final int FORM_LIMIT = 5;
    private static final int H2H_LIMIT = 10;
    private static final int LINEUP_LIMIT = 11;
    private static final DateTimeFormatter FORM_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final Map<String, Integer> POS_ORDER = Map.of("G", 1, "D", 2, "M", 3, "F", 4);
    private static final ConcurrentHashMap<String, LineupSnapshot> LAST_LINEUPS = new ConcurrentHashMap<>();

    public record LineupSnapshot(
            Map<Integer, List<Lineup>> byTeam,
            String homeFormation,
            String awayFormation
    ) {}

    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final BettingRecommendationService bettingRecommendationService;
    private final OddsService oddsService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;

    public ReminderWidgetFactory(
            MatchService matchService,
            HeadToHeadService headToHeadService,
            BettingRecommendationService bettingRecommendationService,
            OddsService oddsService,
            UserWeekBonusMatchService userWeekBonusMatchService
    ) {
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.bettingRecommendationService = bettingRecommendationService;
        this.oddsService = oddsService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
    }

    public record Payload(String html, String chatData) {}

    public Payload build(
            Match match,
            int userId,
            long minutesLeft,
            String kickoff,
            String predictedScore,
            Map<Integer, List<Lineup>> lineups
    ) {
        return build(match, userId, minutesLeft, kickoff, predictedScore, lineups, null, null);
    }

    public Payload build(
            Match match,
            int userId,
            long minutesLeft,
            String kickoff,
            String predictedScore,
            Map<Integer, List<Lineup>> lineups,
            String homeFormation,
            String awayFormation
    ) {
        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalArgumentException("missing teams for match " + match.getId());
        }
        boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                userId, match.getWeekId(), match.getId());
        String tip = bettingRecommendationService.recommendationForMatch(match.getId())
                .map(s -> s.recommendedHome() + ":" + s.recommendedAway())
                .orElse(null);
        String h2h = formatH2h(home, away);
        String formHome = formatForm(match.getHomeTeamId());
        String formAway = formatForm(match.getAwayTeamId());
        Integer th = tablePlace(match.getHomeTeamId());
        Integer ta = tablePlace(match.getAwayTeamId());
        String lh = formatLineup(lineups == null ? null : lineups.get(match.getHomeTeamId()));
        String la = formatLineup(lineups == null ? null : lineups.get(match.getAwayTeamId()));
        String chatData = ReminderWidgetHtml.chatDataJson(
                home.getCode(),
                away.getCode(),
                kickoff,
                minutesLeft,
                weekBonus,
                match.getId(),
                formatOddsSlash(match.getId()),
                predictedScore,
                tip,
                h2h,
                formHome,
                formAway,
                th,
                ta,
                lh,
                la,
                homeFormation,
                awayFormation
        );
        String html = ReminderWidgetHtml.htmlForTeams(
                home.getCode(), away.getCode(), home.getId(), away.getId());
        return new Payload(html, chatData);
    }

    public static LineupSnapshot lastLineups(String chatId, int messageId) {
        if (chatId == null || chatId.isBlank() || messageId <= 0) {
            return null;
        }
        return LAST_LINEUPS.get(cacheKey(chatId, messageId));
    }

    public static void clearLineupsCache() {
        LAST_LINEUPS.clear();
    }

    public static boolean hasPlayers(Map<Integer, List<Lineup>> lineups) {
        if (lineups == null || lineups.isEmpty()) {
            return false;
        }
        for (List<Lineup> rows : lineups.values()) {
            if (rows != null && !rows.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static LineupSnapshot resolveLineups(
            Map<Integer, List<Lineup>> fetched,
            String homeFormation,
            String awayFormation,
            LineupSnapshot cached
    ) {
        Map<Integer, List<Lineup>> out = new HashMap<>();
        if (cached != null && cached.byTeam() != null) {
            for (Map.Entry<Integer, List<Lineup>> e : cached.byTeam().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                out.put(e.getKey(), List.copyOf(e.getValue()));
            }
        }
        if (fetched != null) {
            for (Map.Entry<Integer, List<Lineup>> e : fetched.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                out.put(e.getKey(), List.copyOf(e.getValue()));
            }
        }
        String hf = blank(homeFormation) ? (cached == null ? null : cached.homeFormation()) : homeFormation;
        String af = blank(awayFormation) ? (cached == null ? null : cached.awayFormation()) : awayFormation;
        return new LineupSnapshot(Map.copyOf(out), hf, af);
    }

    public static void rememberLineups(
            String chatId,
            int messageId,
            Map<Integer, List<Lineup>> lineups,
            String homeFormation,
            String awayFormation
    ) {
        if (chatId == null || chatId.isBlank() || messageId <= 0 || !hasPlayers(lineups)) {
            return;
        }
        Map<Integer, List<Lineup>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Lineup>> e : lineups.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        if (copy.isEmpty()) {
            return;
        }
        LAST_LINEUPS.put(
                cacheKey(chatId, messageId),
                new LineupSnapshot(Map.copyOf(copy), homeFormation, awayFormation));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String cacheKey(String chatId, int messageId) {
        return chatId + ":" + messageId;
    }

    static String formatLineup(List<Lineup> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Comparator<Lineup> byPlaceThenPos = Comparator
                .comparingInt((Lineup l) -> {
                    int place = l.getPlayer() == null ? 0 : l.getPlayer().getFormationPlace();
                    return place > 0 ? place : 100;
                })
                .thenComparingInt(l -> POS_ORDER.getOrDefault(
                        l.getPlayer() == null || l.getPlayer().getPos() == null
                                ? ""
                                : l.getPlayer().getPos(),
                        99));
        StringJoiner joiner = new StringJoiner("|");
        int n = 0;
        for (Lineup row : rows.stream().sorted(byPlaceThenPos).toList()) {
            if (n >= LINEUP_LIMIT) {
                break;
            }
            var player = row.getPlayer();
            String surname = ReminderCaption.surname(player);
            if (surname == null || surname.isBlank()) {
                continue;
            }
            int number = player.getNumber();
            int place = player.getFormationPlace();
            StringBuilder token = new StringBuilder();
            if (number > 0) {
                token.append(number).append('.');
            }
            token.append(surname);
            if (place > 0) {
                token.append('@').append(place);
            }
            joiner.add(token.toString());
            n++;
        }
        String s = joiner.toString();
        return s.isBlank() ? null : s;
    }

    private String formatForm(int teamId) {
        List<Match> last = matchService.findLastFinishedByTeamId(teamId, FORM_LIMIT);
        if (last == null || last.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Match m : last) {
            if (m.getHomeScore() == null || m.getAwayScore() == null) {
                continue;
            }
            boolean atHome = m.getHomeTeamId() == teamId;
            int homeScore = m.getHomeScore();
            int awayScore = m.getAwayScore();
            int gf = atHome ? homeScore : awayScore;
            int ga = atHome ? awayScore : homeScore;
            int oppId = atHome ? m.getAwayTeamId() : m.getHomeTeamId();
            Team opp = DaoUtil.team(oppId);
            String oppCode = opp != null && opp.getCode() != null && !opp.getCode().isBlank()
                    ? opp.getCode()
                    : "???";
            LocalDateTime when = m.getStartsAt() != null ? m.getStartsAt() : m.getFinishedAt();
            joiner.add(formToken(resultLetter(gf, ga), homeScore, awayScore, oppCode, atHome, when));
        }
        String s = joiner.toString();
        return s.isBlank() ? null : s;
    }

    private String formatH2h(Team home, Team away) {
        List<HeadToHead> rows = headToHeadService.findForRecommender(home.getId(), away.getId());
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("|");
        int n = 0;
        for (HeadToHead row : rows) {
            if (n >= H2H_LIMIT) {
                break;
            }
            int playedHome = row.getHomeScore();
            int playedAway = row.getAwayScore();
            int gf = playedHome;
            int ga = playedAway;
            if (row.getHomeTeamId() != home.getId()) {
                gf = playedAway;
                ga = playedHome;
            }
            Team rowHome = DaoUtil.team(row.getHomeTeamId());
            Team rowAway = DaoUtil.team(row.getAwayTeamId());
            String hc = rowHome != null && rowHome.getCode() != null && !rowHome.getCode().isBlank()
                    ? rowHome.getCode() : "???";
            String ac = rowAway != null && rowAway.getCode() != null && !rowAway.getCode().isBlank()
                    ? rowAway.getCode() : "???";
            joiner.add(h2hToken(
                    resultLetter(gf, ga),
                    playedHome,
                    playedAway,
                    hc,
                    ac,
                    competitionCode(row.getLeagueName()),
                    row.getStartsAt()));
            n++;
        }
        String s = joiner.toString();
        return s.isBlank() ? null : s;
    }

    static char resultLetter(int gf, int ga) {
        return gf > ga ? 'W' : (gf < ga ? 'L' : 'D');
    }

    static String formToken(
            char result, int homeScore, int awayScore, String oppCode, boolean atHome, LocalDateTime when) {
        StringBuilder b = new StringBuilder();
        b.append(result).append(homeScore).append('-').append(awayScore)
                .append('@').append(oppCode != null ? oppCode : "???")
                .append('@').append(atHome ? 'H' : 'A');
        if (when != null) {
            b.append('@').append(FORM_DATE.format(when));
        }
        return b.toString();
    }

    static String h2hToken(
            char result,
            int homeScore,
            int awayScore,
            String homeCode,
            String awayCode,
            String competition,
            LocalDateTime when) {
        StringBuilder b = new StringBuilder();
        b.append(result).append(homeScore).append(':').append(awayScore)
                .append('@').append(homeCode != null ? homeCode : "???")
                .append('@').append(awayCode != null ? awayCode : "???");
        if (competition != null && !competition.isBlank()) {
            b.append('@').append(competition);
        }
        String season = seasonLabel(when);
        if (season != null) {
            b.append('@').append(season);
        }
        return b.toString();
    }

    static String seasonLabel(LocalDateTime when) {
        if (when == null) {
            return null;
        }
        int y = when.getYear() % 100;
        int month = when.getMonthValue();
        int day = when.getDayOfMonth();
        boolean fromAug = month > 8 || (month == 8 && day >= 1);
        if (fromAug) {
            return String.format("%02d/%02d", y, (y + 1) % 100);
        }
        int prev = y == 0 ? 99 : y - 1;
        return String.format("%02d/%02d", prev, y);
    }

    static String competitionCode(String leagueName) {
        if (leagueName == null || leagueName.isBlank()) {
            return null;
        }
        String s = leagueName.trim().toUpperCase();
        if (s.startsWith("PL") || s.startsWith("PREMIER")) {
            return "EPL";
        }
        if (s.startsWith("FA")) {
            return "FA";
        }
        if (s.startsWith("LC") || s.contains("LEAGUE CUP") || s.contains("EFL CUP")) {
            return "LC";
        }
        if (s.startsWith("CH ") || s.equals("CH") || s.startsWith("CHAMPIONSHIP")) {
            return "CH";
        }
        if (s.startsWith("UCL") || s.contains("CHAMPIONS")) {
            return "UCL";
        }
        if (s.startsWith("UECL") || s.contains("CONFERENCE")) {
            return "UECL";
        }
        if (s.startsWith("UEL") || s.contains("EUROPA")) {
            return "UEL";
        }
        int sp = s.indexOf(' ');
        String head = sp > 0 ? s.substring(0, sp) : s;
        String clean = head.replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > 6 ? clean.substring(0, 6) : clean;
    }

    private Integer tablePlace(int teamId) {
        List<Standing> table = matchService.getStandingsFinishedOnly();
        if (table == null || table.isEmpty()) {
            return null;
        }
        int place = 1;
        for (Standing s : table) {
            if (s.getTeamId() == teamId) {
                return place;
            }
            place++;
        }
        return null;
    }

    private String formatOddsSlash(int matchId) {
        OddsService.Odd odd = oddsService.getOdd(matchId);
        if (odd == null) {
            return null;
        }
        return trimOdd(odd.home()) + "/" + trimOdd(odd.draw()) + "/" + trimOdd(odd.away());
    }

    private static String trimOdd(double value) {
        String s = String.valueOf(value);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }
}
