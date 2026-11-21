package zhigalin.predictions.service;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Event;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.recommender.BettingRecommendationService;
import zhigalin.predictions.recommender.MatchNarrativeService;
import zhigalin.predictions.service.event.CupMatchSyncService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.api.EspnScoreboardClient;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.EspnLinescores;
import zhigalin.predictions.util.EspnStatus;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.TeamCodeMapper;

@Service
public class DataInitService {
    private final WeekService weekService;
    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;
    private final ApiClient apiClient;
    private final BettingRecommendationService bettingRecommendationService;
    private final MatchNarrativeService matchNarrativeService;
    private final EspnScoreboardClient espnScoreboardClient;
    private final CupMatchSyncService cupMatchSyncService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final PredictionService predictionService;
    private final OddsService oddsService;

    public static final int SEASON = 2026;

    /** English / ESPN season start (1 Aug of SEASON), e.g. 2026 → 2026-08-01 for 2026/27. */
    public static java.time.LocalDate seasonStartDate() {
        return java.time.LocalDate.of(SEASON, 8, 1);
    }
    private static final long LIVE_INTERVAL_MS = 30_000L;
    private static final long IDLE_INTERVAL_MS = 120_000L;
    /** Keep polling ESPN this long after FT to pick up score corrections. */
    private static final int SCORE_CORRECTION_WINDOW_HOURS = 2;
    private static final long NEWS_CACHE_MS = 120_000L;
    private final Logger serverLogger = LoggerFactory.getLogger("server");

    private volatile long lastDataInitAtMs = 0L;
    private volatile long dataInitIntervalMs = LIVE_INTERVAL_MS;
    private volatile CachedLeagueNews cachedLeagueNews;

    public DataInitService(WeekService weekService, MatchService matchService,
                           HeadToHeadService headToHeadService, NotificationService notificationService,
                           PanicSender panicSender, ApiClient apiClient,
                           BettingRecommendationService bettingRecommendationService,
                           MatchNarrativeService matchNarrativeService,
                           EspnScoreboardClient espnScoreboardClient,
                           PredictionService predictionService,
                           OddsService oddsService,
                           CupMatchSyncService cupMatchSyncService,
                           UserWeekBonusMatchService userWeekBonusMatchService
    ) {
        this.weekService = weekService;
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.notificationService = notificationService;
        this.panicSender = panicSender;
        this.apiClient = apiClient;
        this.bettingRecommendationService = bettingRecommendationService;
        this.matchNarrativeService = matchNarrativeService;
        this.espnScoreboardClient = espnScoreboardClient;
        this.predictionService = predictionService;
        this.oddsService = oddsService;
        this.cupMatchSyncService = cupMatchSyncService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    private void start() {
        long now = System.currentTimeMillis();
        if (now - lastDataInitAtMs < dataInitIntervalMs) {
            return;
        }
        lastDataInitAtMs = now;
        serverLogger.info("Data init start (interval={}s)", dataInitIntervalMs / 1000);
        try {
            matchUpdateFromESPN();
        } catch (Exception e) {
            serverLogger.error("matchUpdateFromESPN error: {}", e.getMessage(), e);
            panicSender.sendPanic("matchUpdateFromESPN", e);
        }
        try {
            refreshUpcomingOdds();
        } catch (Exception e) {
            serverLogger.error("refreshUpcomingOdds error: {}", e.getMessage(), e);
            panicSender.sendPanic("refreshUpcomingOdds", e);
        }
        try {
            cupMatchSyncService.syncTodayCups();
        } catch (Exception e) {
            serverLogger.error("syncTodayCups error: {}", e.getMessage(), e);
            panicSender.sendPanic("syncTodayCups", e);
        }
        try {
            userWeekBonusMatchService.purgePrematureFutureWeeks(DaoUtil.currentWeekId);
        } catch (Exception e) {
            serverLogger.warn("purge future week bonuses: {}", e.getMessage());
        }
        try {
            notificationService.checkReminders();
        } catch (Exception e) {
            serverLogger.error("checkReminders error: {}", e.getMessage(), e);
            panicSender.sendPanic("checkReminders", e);
        }
        boolean busy = !matchService.findOnlineMatches().isEmpty()
                       || !matchService.findAllNearest(90).isEmpty();
        dataInitIntervalMs = busy ? LIVE_INTERVAL_MS : IDLE_INTERVAL_MS;
    }

    @Scheduled(cron = "0 50 8 * * *")
    private void matchUpdate() throws JsonProcessingException {
        matchDateTimeStatusUpdate();
    }

    public void syncMatchTimesFromApi() throws JsonProcessingException {
        matchDateTimeStatusUpdate();
    }

    private void matchUpdateFromESPN() throws JsonProcessingException {
        List<Match> currentWeekMatches = matchService.findAllByCurrentWeek();
        if (!currentWeekMatches.isEmpty() && currentWeekMatches.stream()
                .allMatch(m -> Objects.equals(m.getStatus(), "ft")
                               || Objects.equals(m.getStatus(), "pst"))) {
            notificationService.sendWeeklyResults();
            weekService.updateCurrent();
            onCurrentWeekRollover();
        }

        boolean hasOnlineMatches = !matchService.findOnlineMatches().isEmpty();
        boolean hasPostponedMatches = matchService.hasPostponedMatches();
        boolean hasRecentlyFinished = matchService.hasRecentlyFinishedMatches(SCORE_CORRECTION_WINDOW_HOURS);

        if (!hasOnlineMatches && !hasPostponedMatches && !hasRecentlyFinished) return;

        Scoreboard scoreboard = espnScoreboardClient.fetchScoreboard();
        if (scoreboard == null || scoreboard.getEvents() == null) {
            return;
        }
        List<Event> events = scoreboard.getEvents();

        for (Event event : events) {
            String state = event.getStatus().getType().getState();
            String[] teams = event.getShortName().split(" @ ");
            String homeTeam = realTeamCode(teams[1]);
            String awayTeam = realTeamCode(teams[0]);

            if (EspnStatus.isPostponed(event.getStatus())) {
                markPostponed(matchService.findByTeamCodes(homeTeam, awayTeam), event.getId(), homeTeam, awayTeam);
            } else if (state.equals("pre")) {
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }
                boolean updated = false;
                if (event.getId() != null && !event.getId().isBlank() && !event.getId().equals(match.getEspnId())) {
                    match.setEspnId(event.getId());
                    updated = true;
                }
                if ("pst".equals(match.getStatus())) {
                    String dateStr = event.getDate().replaceAll("(T\\d{2}:\\d{2})Z", "$1:00Z");
                    LocalDateTime espnDate = Instant.parse(dateStr)
                            .atZone(AppTimeZones.DISPLAY)
                            .toLocalDateTime();
                    match.setStatus("ns");
                    match.setStartsAt(espnDate);
                    updated = true;
                    serverLogger.info("Match {}-{} rescheduled: pst -> ns, date={}", homeTeam, awayTeam, espnDate);
                }
                if (updated) {
                    matchService.update(match);
                }
            } else if (state.equals("in")) {
                String status = normalizeEspnLiveStatus(event);
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }

                Integer homeScore = findScore(event, "home");
                Integer awayScore = findScore(event, "away");
                Integer prevHome = match.getHomeScore();
                Integer prevAway = match.getAwayScore();
                int prevTotal = (prevHome == null ? 0 : prevHome) + (prevAway == null ? 0 : prevAway);
                int nextTotal = homeScore + awayScore;

                match.setHomeScore(homeScore);
                match.setAwayScore(awayScore);
                match.setStatus(status);
                match.setEspnId(event.getId());
                match.setResult(findResult(homeScore, awayScore));

                matchService.update(match);
                bettingRecommendationService.freezeAtKickoffIfNeeded(match.getId());
                if (nextTotal != prevTotal) {
                    notificationService.sendLiveScoreUpdate(match, prevHome, prevAway);
                }
            } else if (state.equals("post")) {
                String status = "ft";
                Match match = matchService.findByTeamCodes(homeTeam, awayTeam);
                if (match == null) {
                    continue;
                }
                apiClient.evictLineups(match.getId());

                Integer homeScore = findScore(event, "home");
                Integer awayScore = findScore(event, "away");
                Integer prevHome = match.getHomeScore();
                Integer prevAway = match.getAwayScore();
                Integer prevHtHome = match.getHtHome();
                Integer prevHtAway = match.getHtAway();
                int prevTotal = (prevHome == null ? 0 : prevHome) + (prevAway == null ? 0 : prevAway);
                int nextTotal = homeScore + awayScore;
                match.setHomeScore(homeScore);
                match.setAwayScore(awayScore);
                applyHalfTimeScore(match, event);
                boolean htFilled = (prevHtHome == null || prevHtAway == null)
                        && match.getHtHome() != null
                        && match.getHtAway() != null;

                boolean becameFinished = !status.equals(match.getStatus());
                boolean scoreCorrected = !becameFinished
                                         && (!Objects.equals(prevHome, homeScore) || !Objects.equals(prevAway, awayScore));
                if (becameFinished) {
                    match.setStatus(status);
                    match.setResult(findResult(homeScore, awayScore));
                    // A replayed fixture must not keep the timestamp of an earlier (postponed) run.
                    match.setFinishedAt(LocalDateTime.now());
                    matchService.update(match);
                    try {
                        // Ensure points even if LISTEN→sendFullTime is missed (NULL points → UI shows 0).
                        predictionService.updateByMatch(match);
                    } catch (Exception e) {
                        serverLogger.error("updateByMatch after ESPN post failed for {}-{}: {}",
                                homeTeam, awayTeam, e.getMessage(), e);
                        panicSender.sendPanic("updateByMatch after ESPN post", e);
                    }
                } else if (scoreCorrected) {
                    match.setResult(findResult(homeScore, awayScore));
                    matchService.update(match);
                    serverLogger.info("Score corrected after FT: {}-{} {}:{} -> {}:{}, recalculating points",
                            homeTeam, awayTeam, prevHome, prevAway, homeScore, awayScore);
                    try {
                        predictionService.updateByMatch(match);
                    } catch (Exception e) {
                        serverLogger.error("updateByMatch after score correction failed for {}-{}: {}",
                                homeTeam, awayTeam, e.getMessage(), e);
                        panicSender.sendPanic("updateByMatch after score correction", e);
                    }
                } else if (htFilled) {
                    matchService.update(match);
                }
                bettingRecommendationService.freezeAtKickoffIfNeeded(match.getId());
                if (becameFinished) {
                    // Drop live GIF/text; FT result card arrives via LISTEN→sendFullTime.
                    notificationService.removeLiveScoreNoise(match);
                } else if (nextTotal != prevTotal) {
                    notificationService.sendLiveScoreUpdate(match, prevHome, prevAway);
                }
                headToHeadService.saveFromFinishedMatch(match);
            }
        }
    }

    /** Postponed/cancelled fixtures are replayed later: no score, no points until they finish for real. */
    private void markPostponed(Match match, String espnId, String homeTeam, String awayTeam) {
        if (match == null || "pst".equals(match.getStatus())) {
            return;
        }
        // A stale postponed event must not reopen a fixture that ESPN already tracks under another id.
        if (match.getEspnId() != null && espnId != null && !match.getEspnId().equals(espnId)) {
            return;
        }
        serverLogger.info("Match {}-{} postponed by ESPN: {} -> pst", homeTeam, awayTeam, match.getStatus());
        match.setHomeScore(null);
        match.setAwayScore(null);
        match.setHtHome(null);
        match.setHtAway(null);
        match.setResult(null);
        boolean wasFinished = "ft".equals(match.getStatus());
        match.setStatus("pst");
        matchService.update(match);
        if (wasFinished) {
            matchService.resetLastProcessedAt(match.getId());
        }
    }

    private String normalizeEspnLiveStatus(Event event) {
        if (event == null || event.getStatus() == null) {
            return null;
        }
        zhigalin.predictions.model.v2.Type type = event.getStatus().getType();
        String displayClock = event.getStatus().getDisplayClock();
        if (type == null) {
            return displayClock;
        }
        if (Boolean.TRUE.equals(type.getCompleted()) || "post".equalsIgnoreCase(type.getState())) {
            return "ft";
        }
        String detail = type.getDetail();
        String shortDetail = type.getShortDetail();
        String description = type.getDescription();
        if (containsHalftime(detail) || containsHalftime(shortDetail) || containsHalftime(description)) {
            return "ht";
        }
        return displayClock;
    }

    private boolean containsHalftime(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("halftime")
                || normalized.contains("half-time")
                || normalized.equals("ht")
                || normalized.startsWith("ht ")
                || normalized.endsWith(" ht");
    }

    /** After {@link WeekService#updateCurrent()}: refresh DaoUtil week, AI tips, personal bonus draws. */
    private void onCurrentWeekRollover() {
        var currentWeek = weekService.findCurrentWeek();
        if (currentWeek == null) {
            return;
        }
        DaoUtil.currentWeekId = currentWeek.getId();
        try {
            userWeekBonusMatchService.assignForNewCurrentWeek(currentWeek.getId());
            serverLogger.info("Week bonus matches assigned for week {}", currentWeek.getId());
        } catch (Exception e) {
            serverLogger.warn("Week bonus assign after rollover failed: {}", e.getMessage());
        }
        try {
            bettingRecommendationService.refreshForWeek(currentWeek.getId());
        } catch (Exception e) {
            serverLogger.warn("Betting recommender refresh after week rollover failed: {}", e.getMessage());
        }
        try {
            int weekId = currentWeek.getId();
            if (matchNarrativeService.isConfigured() && matchNarrativeService.tryMarkScheduled(weekId)) {
                matchNarrativeService.generateForWeekAsync(weekId);
            } else if (!matchNarrativeService.isConfigured()) {
                serverLogger.info("Groq narrative skipped after rollover: GROQ_API_KEY missing");
            }
        } catch (Exception e) {
            serverLogger.warn("Groq narrative schedule after week rollover failed: {}", e.getMessage());
        }
    }

    /** Pull 1X2 / match OU / team totals for ns fixtures as soon as ESPN lists them. */
    private void refreshUpcomingOdds() {
        List<Match> upcoming = matchService.findAllByCurrentWeek().stream()
                .filter(m -> "ns".equals(m.getStatus()) || "pst".equals(m.getStatus()))
                .toList();
        if (upcoming.isEmpty()) {
            return;
        }
        oddsService.ensureFresh(upcoming);
    }

    private String realTeamCode(String teamCode) {
        return TeamCodeMapper.toInternalCode(teamCode);
    }

    private Integer findScore(Event event, String awayHome) {
        List<Competitor> competitors = event.getCompetitions().getFirst().getCompetitors();
        Competitor competitor = competitors.stream()
                .filter(c -> c.getHomeAway().equals(awayHome))
                .findFirst()
                .orElseThrow();

        return Integer.parseInt(competitor.getScore());
    }

    private void applyHalfTimeScore(Match match, Event event) {
        EspnLinescores.HtScore ht = EspnLinescores.fromEvent(event);
        String espnId = match.getEspnId() != null && !match.getEspnId().isBlank()
                ? match.getEspnId()
                : (event != null ? event.getId() : null);
        if (ht == null && espnId != null && !espnId.isBlank()) {
            JsonNode summary = apiClient.fetchEspnSummary(espnId);
            ht = EspnLinescores.fromSummary(summary);
        }
        if (ht == null) {
            return;
        }
        match.setHtHome(ht.home());
        match.setHtAway(ht.away());
        if ((match.getEspnId() == null || match.getEspnId().isBlank()) && espnId != null) {
            match.setEspnId(espnId);
        }
    }

    private String findResult(Integer homeScore, Integer awayScore) {
        String result;
        if (homeScore.equals(awayScore)) {
            result = "D";
        } else {
            result = homeScore > awayScore ? "H" : "A";
        }
        return result;
    }


    private void matchDateTimeStatusUpdate() {
        LocalDate today = LocalDate.now(AppTimeZones.DISPLAY);
        LocalDate windowEnd = today.plusDays(13);
        List<Match> upcoming = matchService.findAllByUpcomingDays(14).stream()
                .filter(m -> "ns".equals(m.getStatus()) || "pst".equals(m.getStatus()))
                .toList();
        if (upcoming.isEmpty()) {
            serverLogger.info("No ns/pst matches in next 14 days for kickoff sync");
            return;
        }
        Scoreboard scoreboard = espnScoreboardClient.fetchScoreboard(today, windowEnd);
        if (scoreboard == null || scoreboard.getEvents() == null || scoreboard.getEvents().isEmpty()) {
            serverLogger.warn("ESPN scoreboard empty for kickoff sync {}..{}", today, windowEnd);
            return;
        }
        int synced = 0;
        for (Event event : scoreboard.getEvents()) {
            if (event.getShortName() == null || !event.getShortName().contains(" @ ")) {
                continue;
            }
            String[] teams = event.getShortName().split(" @ ");
            if (teams.length != 2) {
                continue;
            }
            String homeCode = realTeamCode(teams[1]);
            String awayCode = realTeamCode(teams[0]);
            Match match = resolveUpcomingMatch(upcoming, event.getId(), homeCode, awayCode);
            if (match == null) {
                continue;
            }
            Integer homeScoreBefore = match.getHomeScore();
            Integer awayScoreBefore = match.getAwayScore();
            String espnBefore = match.getEspnId();
            int idBefore = match.getId();
            if (EspnStatus.isPostponed(event.getStatus())) {
                markPostponed(match, event.getId(), homeCode, awayCode);
                synced++;
                continue;
            }
            String state = event.getStatus() != null && event.getStatus().getType() != null
                    ? event.getStatus().getType().getState()
                    : "";
            if (!"pre".equals(state)) {
                continue;
            }
            if (match.getEspnId() != null && !match.getEspnId().isBlank()
                    && event.getId() != null && !event.getId().isBlank()
                    && !match.getEspnId().equals(event.getId())) {
                continue;
            }
            boolean updated = false;
            if (event.getId() != null && !event.getId().isBlank()
                    && (match.getEspnId() == null || match.getEspnId().isBlank())) {
                match.setEspnId(event.getId());
                updated = true;
            }
            if (event.getDate() != null && !event.getDate().isBlank()) {
                String dateStr = event.getDate().replaceAll("(T\\d{2}:\\d{2})Z", "$1:00Z");
                LocalDateTime espnDate = Instant.parse(dateStr)
                        .atZone(AppTimeZones.DISPLAY)
                        .toLocalDateTime();
                if (!espnDate.equals(match.getStartsAt())) {
                    match.setStartsAt(espnDate);
                    updated = true;
                }
            }
            if ("pst".equals(match.getStatus())) {
                match.setStatus("ns");
                updated = true;
            }
            if (!updated) {
                continue;
            }
            if (match.getId() != idBefore
                    || !Objects.equals(match.getHomeScore(), homeScoreBefore)
                    || !Objects.equals(match.getAwayScore(), awayScoreBefore)
                    || (espnBefore != null && !espnBefore.isBlank()
                        && !espnBefore.equals(match.getEspnId()))) {
                serverLogger.error("Kickoff sync refused unsafe mutation for match {}", idBefore);
                continue;
            }
            matchService.update(match);
            synced++;
        }
        serverLogger.info(
                "Synced {} match kickoffs from ESPN within {}..{} (timezone={})",
                synced, today, windowEnd, AppTimeZones.DISPLAY
        );
    }

    static Match resolveUpcomingMatch(
            List<Match> upcoming, String espnId, String homeCode, String awayCode
    ) {
        if (upcoming == null || upcoming.isEmpty()) {
            return null;
        }
        if (espnId != null && !espnId.isBlank()) {
            for (Match match : upcoming) {
                if (espnId.equals(match.getEspnId())) {
                    return match;
                }
            }
        }
        Integer homeId = null;
        Integer awayId = null;
        for (Team team : DaoUtil.TEAMS.values()) {
            if (homeId == null && homeCode.equals(team.getCode())) {
                homeId = team.getId();
            }
            if (awayId == null && awayCode.equals(team.getCode())) {
                awayId = team.getId();
            }
            if (homeId != null && awayId != null) {
                break;
            }
        }
        if (homeId == null || awayId == null) {
            return null;
        }
        for (Match match : upcoming) {
            if (homeId.equals(match.getHomeTeamId()) && awayId.equals(match.getAwayTeamId())
                    && ("ns".equals(match.getStatus()) || "pst".equals(match.getStatus()))) {
                return match;
            }
        }
        return null;
    }

    public List<News> newsInit() throws IOException, ParseException, FeedException {
        long now = System.currentTimeMillis();
        CachedLeagueNews cached = cachedLeagueNews;
        if (cached != null && now - cached.fetchedAtMs() < NEWS_CACHE_MS) {
            return cached.news();
        }
        List<News> news = fetchLeagueNews();
        cachedLeagueNews = new CachedLeagueNews(List.copyOf(news), now);
        return news;
    }

    private List<News> fetchLeagueNews() throws IOException, ParseException, FeedException {
        List<News> news = new LinkedList<>();
        String title;
        String link;
        LocalDateTime dateTime;
        DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
        URL feedSource = new URL("https://www.sports.ru/stat/export/rss/taglenta.xml?id=1363805");
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(feedSource));
        List<SyndEntry> res = feed.getEntries().stream().limit(30L).toList();
        for (SyndEntry re : res) {
            link = re.getLink().replace("\n", "");
            title = re.getTitle().replace("\n", "")
                    .lines().filter(s -> !s.contains("?")).collect(Collectors.joining());
            dateTime = formatter.parse(re.getPublishedDate().toString())
                    .toInstant()
                    .atZone(AppTimeZones.DISPLAY)
                    .toLocalDateTime();
            if (!title.isEmpty()) {
                news.add(News.builder().title(title).link(link).localDateTime(dateTime).build());
            }
        }
        return news;
    }

    private record CachedLeagueNews(List<News> news, long fetchedAtMs) {
    }

}
