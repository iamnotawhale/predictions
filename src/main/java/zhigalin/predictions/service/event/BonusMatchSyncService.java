package zhigalin.predictions.service.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Event;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.repository.event.BonusMatchDao;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.api.EspnScoreboardClient;
import zhigalin.predictions.service.notification.HtmlImageRenderer;
import zhigalin.predictions.service.notification.Result;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.TeamCodeMapper;

@Service
public class BonusMatchSyncService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final long CUP_WINDOW_MIN_INTERVAL_MS = 30 * 60 * 1000L;
    private static final int CUP_WINDOW_DAYS = 30;

    @Value("${bot.chatId}")
    private String defaultChatId;

    private final EspnScoreboardClient espnScoreboardClient;
    private final BonusMatchDao bonusMatchDao;
    private final PredictionService predictionService;
    private final ApiClient apiClient;
    private final HtmlImageRenderer htmlImageRenderer;
    private volatile long lastCupWindowSyncMs;

    public BonusMatchSyncService(
            EspnScoreboardClient espnScoreboardClient,
            BonusMatchDao bonusMatchDao,
            PredictionService predictionService,
            ApiClient apiClient,
            HtmlImageRenderer htmlImageRenderer
    ) {
        this.espnScoreboardClient = espnScoreboardClient;
        this.bonusMatchDao = bonusMatchDao;
        this.predictionService = predictionService;
        this.apiClient = apiClient;
        this.htmlImageRenderer = htmlImageRenderer;
    }

    public void syncTodayCups() {
        purgeInvalidBonusMatches();
        java.time.LocalDate today = LocalDateTime.now(AppTimeZones.DISPLAY).toLocalDate();
        java.time.LocalDate until = today.plusDays(CUP_WINDOW_DAYS);
        boolean scanWindow = shouldScanCupWindow();
        for (String league : EspnScoreboardClient.CUP_LEAGUES) {
            try {
                ingestScoreboard(league, espnScoreboardClient.fetchScoreboard(league));
                if (scanWindow) {
                    ingestUpcomingWindow(league, today, until);
                }
            } catch (Exception e) {
                log.warn("Cup sync failed for {}: {}", league, e.getMessage());
            }
        }
        if (scanWindow) {
            lastCupWindowSyncMs = System.currentTimeMillis();
        }
    }

    /**
     * Drop rows without an EPL club link and anything before the current season.
     * Fixes legacy Bayern(MUN)→Man United collisions and last-season FA finals.
     */
    void purgeInvalidBonusMatches() {
        java.time.LocalDate seasonStart = zhigalin.predictions.service.DataInitService.seasonStartDate();
        int removed = bonusMatchDao.deleteBeforeDate(seasonStart.atStartOfDay());
        int removedNonEpl = bonusMatchDao.deleteWithoutEplTeam();
        int removedMismatch = 0;
        int remappedBayern = 0;
        for (BonusMatch m : bonusMatchDao.findAllRaw()) {
            if (isFalseEplLink(m.getHomeName(), m.getHomeTeamId())
                || isFalseEplLink(m.getAwayName(), m.getAwayTeamId())
                || (m.getHomeTeamId() == null && m.getAwayTeamId() == null)) {
                removedMismatch += bonusMatchDao.deleteByPublicId(m.getPublicId());
                continue;
            }
            boolean dirty = false;
            if (looksBayern(m.getHomeName()) && "MUN".equalsIgnoreCase(m.getHomeEspnCode())) {
                m.setHomeEspnCode("BAY");
                dirty = true;
            }
            if (looksBayern(m.getAwayName()) && "MUN".equalsIgnoreCase(m.getAwayEspnCode())) {
                m.setAwayEspnCode("BAY");
                dirty = true;
            }
            if (dirty) {
                bonusMatchDao.upsert(m);
                remappedBayern++;
            }
        }
        if (removed > 0 || removedNonEpl > 0 || removedMismatch > 0 || remappedBayern > 0) {
            log.info("Cup purge: beforeSeason={} withoutEpl={} mismatch={} remappedBayern={}",
                    removed, removedNonEpl, removedMismatch, remappedBayern);
        }
    }

    private static boolean isFalseEplLink(String name, Integer teamId) {
        if (teamId == null || name == null) {
            return false;
        }
        Team linked = DaoUtil.TEAMS.get(teamId);
        if (linked == null || linked.getCode() == null) {
            return false;
        }
        return looksBayern(name) && "MUN".equalsIgnoreCase(linked.getCode());
    }

    private static boolean looksBayern(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        return n.contains("bayern") || n.contains("münchen") || n.contains("munchen");
    }

    private boolean shouldScanCupWindow() {
        long last = lastCupWindowSyncMs;
        return last == 0L || System.currentTimeMillis() - last >= CUP_WINDOW_MIN_INTERVAL_MS;
    }

    private void ingestUpcomingWindow(
            String league,
            java.time.LocalDate from,
            java.time.LocalDate until
    ) {
        Scoreboard ranged = espnScoreboardClient.fetchScoreboard(league, from, until);
        if (ranged != null && ranged.getEvents() != null && !ranged.getEvents().isEmpty()) {
            ingestScoreboard(league, ranged);
            return;
        }
        for (java.time.LocalDate d = from; !d.isAfter(until); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();
            if (d.equals(from) || dow == 2 || dow == 3 || dow == 6 || dow == 7) {
                ingestScoreboard(league, espnScoreboardClient.fetchScoreboard(league, d, d));
            }
        }
    }

    private void ingestScoreboard(String competition, Scoreboard board) {
        if (board == null || board.getEvents() == null) {
            return;
        }
        for (Event event : board.getEvents()) {
            ingest(competition, event);
        }
    }

    private void ingest(String competition, Event event) {
        if (event.getCompetitions() == null || event.getCompetitions().isEmpty()) {
            return;
        }
        // ESPN FA often keeps last campaign on the default board (year=SEASON-1).
        // Align with DataInitService.SEASON / eng.1 / uefa.* (start year of the campaign).
        Integer espnSeasonYear = event.getSeason() != null ? event.getSeason().getYear() : null;
        if (espnSeasonYear != null && espnSeasonYear != zhigalin.predictions.service.DataInitService.SEASON) {
            return;
        }
        var competitionObj = event.getCompetitions().getFirst();
        List<Competitor> competitors = competitionObj.getCompetitors();
        if (competitors == null || competitors.size() < 2) {
            return;
        }
        Competitor home = competitors.stream()
                .filter(c -> "home".equalsIgnoreCase(c.getHomeAway())).findFirst().orElse(null);
        Competitor away = competitors.stream()
                .filter(c -> "away".equalsIgnoreCase(c.getHomeAway())).findFirst().orElse(null);
        if (home == null || away == null) {
            return;
        }
        String homeCode = codeOf(home);
        String awayCode = codeOf(away);
        Team homeTeam = resolveTeam(homeCode);
        Team awayTeam = resolveTeam(awayCode);
        // Only real EPL clubs from our roster — never match by bare code (Bayern ESPN MUN ≠ Man United).
        if (homeTeam == null && awayTeam == null) {
            return;
        }

        java.time.LocalDateTime kickoff = parseKickoff(event.getDate());
        if (kickoff != null && kickoff.toLocalDate().isBefore(zhigalin.predictions.service.DataInitService.seasonStartDate())) {
            return;
        }

        int publicId;
        try {
            publicId = Integer.parseInt(event.getId());
        } catch (Exception e) {
            return;
        }

        BonusMatch existing = bonusMatchDao.findByPublicId(publicId);
        String state = event.getStatus() != null && event.getStatus().getType() != null
                ? event.getStatus().getType().getState()
                : "pre";
        String status = mapStatus(event, state);
        Integer homeScore = parseScore(home.getScore());
        Integer awayScore = parseScore(away.getScore());

        boolean wasFt = existing != null && "ft".equals(existing.getStatus());

        BonusMatch match = BonusMatch.builder()
                .publicId(publicId)
                .competition(competition)
                .espnId(event.getId())
                .homeTeamId(homeTeam != null ? homeTeam.getPublicId() : null)
                .awayTeamId(awayTeam != null ? awayTeam.getPublicId() : null)
                .homeName(nameOf(home))
                .awayName(nameOf(away))
                .homeLogoUrl(logoOf(home))
                .awayLogoUrl(logoOf(away))
                .homeEspnCode(homeCode)
                .awayEspnCode(awayCode)
                .homeTeamScore(homeScore)
                .awayTeamScore(awayScore)
                .status(status)
                .result(resultOf(homeScore, awayScore))
                .localDateTime(kickoff != null ? kickoff : (existing != null ? existing.getLocalDateTime() : null))
                .liveScoreMessageId(existing != null ? existing.getLiveScoreMessageId() : null)
                .finishedAt(existing != null ? existing.getFinishedAt() : null)
                .build();

        boolean becameFt = "ft".equals(status) && !wasFt;
        if (becameFt && match.getFinishedAt() == null) {
            match.setFinishedAt(LocalDateTime.now());
        }

        bonusMatchDao.upsert(match);

        if (becameFt) {
            predictionService.updateByBonusMatch(match);
            sendFullTime(match);
        }
    }

    private void sendFullTime(BonusMatch match) {
        String home = match.getHomeEspnCode() != null ? match.getHomeEspnCode() : "?";
        String away = match.getAwayEspnCode() != null ? match.getAwayEspnCode() : "?";
        String center = nz(match.getHomeTeamScore()) + ":" + nz(match.getAwayTeamScore());
        List<Prediction> predictions = predictionService.getByBonusMatchPublicId(match.getPublicId());
        List<Result> results = predictions.stream()
                .map(p -> {
                    User user = DaoUtil.USERS.get(p.getUserId());
                    if (user == null || user.getLogin() == null) {
                        return null;
                    }
                    String login = user.getLogin();
                    String shortLogin = login.length() >= 3 ? login.substring(0, 3) : login;
                    String predict = (p.getHomeTeamScore() != null ? p.getHomeTeamScore() : "")
                                     + ":"
                                     + (p.getAwayTeamScore() != null ? p.getAwayTeamScore() : "");
                    int points = p.getPoints() == null ? 0 : p.getPoints();
                    return new Result(shortLogin, predict, points);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Result::point).reversed().thenComparing(Result::login))
                .toList();

        String path = htmlImageRenderer.createResultImage(
                HtmlImageRenderer.competitionAccent(match.getCompetition()),
                HtmlImageRenderer.competitionBadge(match.getCompetition()),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getHomeEspnCode(),
                match.getAwayEspnCode(),
                match.getHomeLogoUrl(),
                match.getAwayLogoUrl(),
                center,
                null,
                results
        );

        String caption = "Матч " + home + " " + center + " " + away
                         + " (" + competitionLabel(match.getCompetition()) + ") окончен";
        if (path != null) {
            apiClient.sendPhoto(defaultChatId, caption, path, null);
        } else {
            StringBuilder sb = new StringBuilder(caption).append('\n');
            for (Result r : results) {
                sb.append(r.login()).append(' ').append(r.predict())
                        .append(" → ").append(r.point()).append('\n');
            }
            apiClient.sendMessage(defaultChatId, sb.toString(), null);
        }
        bonusMatchDao.updateLiveScoreMessageId(match.getPublicId(), null);
    }

    private static Team resolveTeam(String code) {
        if (code == null) {
            return null;
        }
        return DaoUtil.TEAMS.values().stream()
                .filter(t -> t.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    private static String mapStatus(Event event, String state) {
        if ("pre".equals(state)) {
            return "ns";
        }
        if ("post".equals(state)) {
            return "ft";
        }
        if (event.getStatus() != null && event.getStatus().getType() != null
            && Boolean.TRUE.equals(event.getStatus().getType().getCompleted())) {
            return "ft";
        }
        String detail = event.getStatus() != null && event.getStatus().getType() != null
                ? event.getStatus().getType().getDetail() : null;
        if (detail != null && detail.toLowerCase().contains("half")) {
            return "ht";
        }
        String clock = event.getStatus() != null ? event.getStatus().getDisplayClock() : null;
        return clock != null && !clock.isBlank() ? clock : "1H";
    }

    private static String competitionLabel(String competition) {
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

    private static String codeOf(Competitor c) {
        if (c.getTeam() != null && c.getTeam().getAbbreviation() != null) {
            return TeamCodeMapper.fromEspnAbbreviation(c.getTeam().getAbbreviation());
        }
        return null;
    }

    private static String nameOf(Competitor c) {
        if (c.getTeam() == null) {
            return null;
        }
        if (c.getTeam().getDisplayName() != null) {
            return c.getTeam().getDisplayName();
        }
        return c.getTeam().getName();
    }

    private static String logoOf(Competitor c) {
        return c.getTeam() != null ? c.getTeam().getLogo() : null;
    }

    private static Integer parseScore(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(score.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseKickoff(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            String normalized = date.replaceAll("(T\\d{2}:\\d{2})Z", "$1:00Z");
            return Instant.parse(normalized).atZone(AppTimeZones.DISPLAY).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static String resultOf(Integer home, Integer away) {
        if (home == null || away == null) {
            return null;
        }
        if (home.equals(away)) {
            return "D";
        }
        return home > away ? "H" : "A";
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
