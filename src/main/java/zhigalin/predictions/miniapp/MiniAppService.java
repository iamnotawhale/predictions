package zhigalin.predictions.miniapp;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.config.DeploymentInfoService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchRecommendationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ExplanationStatRow;
import zhigalin.predictions.recommender.BettingRecommendationService;
import zhigalin.predictions.recommender.model.ExplanationBreakdown;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ChartSeries;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CupCompetitionItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CupReviewResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdMeterResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdScoreBucket;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.H2hItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardEntry;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormationPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LineupPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveMatchDetailsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchEventItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchInsightsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchNewsItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.InjuryItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PlayerStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamFormationItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewResponse;
import zhigalin.predictions.model.event.BonusMatch;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.BonusMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.service.api.InjuryService;
import zhigalin.predictions.service.api.InjuryService.InjuryInfo;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.FlooredPointsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MiniAppService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter NEWS_TS = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final String SPORTS_RU_TEAM_RSS = "https://www.sports.ru/stat/export/rss/taglenta.xml?id=";
    private static final long TEAM_NEWS_CACHE_MS = 120_000L;
    private static final Set<String> CLOSED_MATCH_STATUSES = Set.of(
            "ft", "aet", "pen", "canc", "abd", "awrd", "wo"
    );
    private static final Map<String, Integer> TEAM_NEWS_TAG_IDS = Map.ofEntries(
            Map.entry("ARS", 1685202),
            Map.entry("AST", 1315275),
            Map.entry("BOU", 5643539),
            Map.entry("BRE", 63451855),
            Map.entry("BRI", 5607747),
            Map.entry("CHE", 1046674),
            Map.entry("COV", 3612640),
            Map.entry("CRY", 3006569),
            Map.entry("EVE", 1300662),
            Map.entry("FUL", 1347893),
            Map.entry("HUL", 3096963),
            Map.entry("IPS", 3779605),
            Map.entry("LEE", 2793103),
            Map.entry("LIV", 1046732),
            Map.entry("MCI", 1328029),
            Map.entry("MUN", 1046599),
            Map.entry("NEW", 1062581),
            Map.entry("NOT", 3057313),
            Map.entry("SUN", 2682737),
            Map.entry("TOT", 2682611)
    );

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final HeadToHeadService headToHeadService;
    private final OddsService oddsService;
    private final ApiClient apiClient;
    private final InjuryService injuryService;
    private final ObjectMapper objectMapper;
    private final DeploymentInfoService deploymentInfoService;
    private final BettingRecommendationService bettingRecommendationService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final BonusMatchDao bonusMatchDao;
    private final String adminChatId;
    private final ConcurrentHashMap<String, CachedTeamNews> teamNewsCache = new ConcurrentHashMap<>();

    public MiniAppService(
            UserService userService,
            MatchService matchService,
            PredictionService predictionService,
            HeadToHeadService headToHeadService,
            OddsService oddsService,
            ApiClient apiClient,
            InjuryService injuryService,
            ObjectMapper objectMapper,
            DeploymentInfoService deploymentInfoService,
            BettingRecommendationService bettingRecommendationService,
            UserWeekBonusMatchService userWeekBonusMatchService,
            BonusMatchDao bonusMatchDao,
            @Value("${chatId:}") String adminChatId
    ) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.headToHeadService = headToHeadService;
        this.oddsService = oddsService;
        this.apiClient = apiClient;
        this.injuryService = injuryService;
        this.objectMapper = objectMapper;
        this.deploymentInfoService = deploymentInfoService;
        this.bettingRecommendationService = bettingRecommendationService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.bonusMatchDao = bonusMatchDao;
        this.adminChatId = adminChatId == null ? "" : adminChatId.trim();
    }

    public User requireUser(String telegramId) {
        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            throw new MiniAppException(403, "Пользователь не найден. Обратитесь к администратору бота.");
        }
        return user;
    }

    public ProfileResponse profile(String telegramId) {
        User user = requireUser(telegramId);
        int weekId = DaoUtil.currentWeekId;
        boolean admin = isAdmin(telegramId);
        String dnsHint = admin ? deploymentInfoService.dnsHintForAdmin() : null;
        return new ProfileResponse(
                user.getLogin(),
                weekId,
                DataInitService.SEASON,
                "Тур " + weekId + " · сезон " + DataInitService.SEASON,
                dnsHint,
                user.isBettingRecommenderEnabled(),
                admin
        );
    }

    public ActionResponse setBettingRecommender(String telegramId, boolean enabled) {
        requireUser(telegramId);
        userService.updateBettingRecommenderEnabled(telegramId, enabled);
        if (enabled) {
            bettingRecommendationService.ensureCurrentWeekRecommendations();
        }
        return new ActionResponse(true, enabled ? "Рекомендатор включён" : "Рекомендатор выключен");
    }

    public ActionResponse refreshBettingRecommendations(String telegramId, Integer weekId) {
        requireAdmin(telegramId);
        int targetWeek = weekId != null ? weekId : DaoUtil.currentWeekId;
        if (targetWeek <= 0) {
            throw new MiniAppException(400, "Не задан тур для пересчёта рекомендаций.");
        }
        try {
            int stored = bettingRecommendationService.refreshForWeek(targetWeek);
            return new ActionResponse(
                    true,
                    "Рекомендации обновлены для тура " + targetWeek + " (" + stored + " матчей)"
            );
        } catch (IllegalStateException e) {
            throw new MiniAppException(502, e.getMessage());
        }
    }

    private void requireAdmin(String telegramId) {
        requireUser(telegramId);
        if (!isAdmin(telegramId)) {
            throw new MiniAppException(403, "Доступ только для администратора.");
        }
    }

    private boolean isAdmin(String telegramId) {
        if (adminChatId.isBlank() || "0".equals(adminChatId)) {
            return false;
        }
        return adminChatId.equals(telegramId);
    }

    public List<WeekItem> weeks(String telegramId) {
        requireUser(telegramId);
        Set<Integer> withPredictions = new HashSet<>(predictionService.getPredictableWeeksByUserTelegramId(telegramId));
        List<WeekItem> items = new ArrayList<>();
        for (int week = 1; week <= 38; week++) {
            items.add(new WeekItem(week, withPredictions.contains(week)));
        }
        return items;
    }

    public List<MatchItem> weekMatches(String telegramId, int weekId) {
        User user = requireUser(telegramId);
        userWeekBonusMatchService.ensureAssignedForUser(user.getId(), weekId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        oddsService.ensureFresh(matches);
        Map<Integer, Prediction> predictions = predictionService.predictionsByMatchForUser(
                telegramId,
                matches.stream().map(Match::getPublicId).toList()
        );
        Integer bonusId = userWeekBonusMatchService.findAssigned(user.getId(), weekId).orElse(null);
        return matches.stream()
                .map(match -> {
                    Prediction prediction = predictions.get(match.getPublicId());
                    return toMatchItem(
                            match,
                            telegramId,
                            prediction != null,
                            prediction,
                            null,
                            bonusId != null && bonusId == match.getPublicId()
                    );
                })
                .toList();
    }

    public List<MatchItem> myPredictions(String telegramId, int weekId) {
        requireUser(telegramId);
        List<MatchPrediction> rows = predictionService.getAllWeeklyPredictionsByUserTelegramId(weekId, telegramId);
        Map<Integer, int[]> kickoffs = bettingRecommendationService.kickoffScoresByMatchIds(
                rows.stream().map(mp -> mp.match().getPublicId()).toList()
        );
        return rows.stream()
                .map(mp -> toMatchItem(mp.match(), telegramId, true, mp.prediction(), kickoffs.get(mp.match().getPublicId()), false))
                .toList();
    }

    public MatchItem match(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match != null) {
            oddsService.ensureFresh(List.of(match));
        }
        Prediction prediction = predictionService.getByUserTelegramIdAndTeams(
                telegramId, homeCode.toUpperCase(), awayCode.toUpperCase()
        );
        boolean hasPrediction = prediction != null;
        int[] kickoff = match == null
                ? null
                : bettingRecommendationService.kickoffScore(match.getPublicId()).orElse(null);
        boolean weekBonus = false;
        if (match != null) {
            User user = requireUser(telegramId);
            weekBonus = userWeekBonusMatchService.isWeekBonusMatch(user.getId(), match.getWeekId(), match.getPublicId());
        }
        return toMatchItem(match, telegramId, hasPrediction, prediction, kickoff, weekBonus);
    }

    public MatchInsightsResponse matchInsights(String telegramId, String homeCode, String awayCode) {
        User user = requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match == null) {
            return new MatchInsightsResponse(List.of(), List.of(), List.of(), null, List.of());
        }
        List<FormItem> homeForm = buildRecentForm(match.getHomeTeamId(), 5);
        List<FormItem> awayForm = buildRecentForm(match.getAwayTeamId(), 5);
        List<MatchNewsItem> news = loadMatchNews(match, 4);
        Team homeTeam = DaoUtil.team(match.getHomeTeamId());
        Team awayTeam = DaoUtil.team(match.getAwayTeamId());
        List<InjuryItem> injuries = toInjuryItems(injuryService.forTeams(
                homeTeam != null ? homeTeam.getCode() : null,
                awayTeam != null ? awayTeam.getCode() : null
        ));
        MatchRecommendationResponse recommendation = null;
        if (user.isBettingRecommenderEnabled()) {
            bettingRecommendationService.ensureCurrentWeekRecommendations();
            recommendation = bettingRecommendationService.recommendationForMatch(match.getPublicId())
                    .map(this::toRecommendationResponse)
                    .orElse(null);
        }
        return new MatchInsightsResponse(homeForm, awayForm, news, recommendation, injuries);
    }

    private List<InjuryItem> toInjuryItems(List<InjuryInfo> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return List.of();
        }
        List<InjuryItem> out = new ArrayList<>();
        for (InjuryInfo injury : injuries) {
            out.add(new InjuryItem(
                    injury.teamCode(),
                    injury.playerName(),
                    injury.status() != null ? injury.status() : "",
                    injury.reason() != null ? injury.reason() : ""
            ));
        }
        return List.copyOf(out);
    }

    private MatchRecommendationResponse toRecommendationResponse(MatchRecommendationSnapshot snapshot) {
        var explanation = snapshot.explanation() != null
                ? snapshot.explanation()
                : ExplanationBreakdown.empty();
        List<ExplanationStatRow> rows = explanation.rows() == null ? List.of() : explanation.rows().stream()
                .map(r -> new ExplanationStatRow(r.metric(), r.home(), r.away()))
                .toList();
        List<String> notes = explanation.notes() != null ? explanation.notes() : List.of();
        return new MatchRecommendationResponse(
                snapshot.recommendedHome(),
                snapshot.recommendedAway(),
                snapshot.expectedHomeGoals(),
                snapshot.expectedAwayGoals(),
                snapshot.scoreProbability(),
                rows,
                notes,
                snapshot.explanationLines(),
                snapshot.summary()
        );
    }

    public LiveMatchDetailsResponse liveMatchDetails(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match == null) {
            return emptyLiveDetails();
        }
        boolean live = isLiveStatus(match.getStatus())
                       && match.getHomeTeamScore() != null
                       && match.getAwayTeamScore() != null;
        if (!live) {
            return emptyLiveDetails();
        }
        JsonNode summaryRoot = loadEspnSummaryRoot(match);
        TeamFormationItem homeFormation = loadTeamFormation(summaryRoot, "home", teamCode(match.getHomeTeamId()));
        TeamFormationItem awayFormation = loadTeamFormation(summaryRoot, "away", teamCode(match.getAwayTeamId()));
        List<LineupPlayerItem> homeLineup = toLineupItemsFromFormation(homeFormation);
        List<LineupPlayerItem> awayLineup = toLineupItemsFromFormation(awayFormation);
        if (homeLineup.isEmpty() && awayLineup.isEmpty()) {
            Map<Integer, List<Lineup>> lineups = apiClient.getLineups(match.getPublicId());
            homeLineup = toLineupItems(lineups.get(match.getHomeTeamId()));
            awayLineup = toLineupItems(lineups.get(match.getAwayTeamId()));
        }
        List<MatchEventItem> events = loadLiveEvents(summaryRoot);
        List<MatchStatItem> matchStats = loadLiveStats(summaryRoot);
        return new LiveMatchDetailsResponse(
                true,
                homeLineup,
                awayLineup,
                homeFormation,
                awayFormation,
                events,
                matchStats,
                pitchColorFromFormation(homeFormation, "#ffffff"),
                pitchColorFromFormation(awayFormation, "#c0c0c0"),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getStatus()
        );
    }

    public LiveMatchDetailsResponse liveCupMatchDetails(String telegramId, int bonusPublicId) {
        requireUser(telegramId);
        BonusMatch match = bonusMatchDao.findByPublicId(bonusPublicId);
        if (match == null) {
            return emptyLiveDetails();
        }
        boolean live = isLiveStatus(match.getStatus())
                       && match.getHomeTeamScore() != null
                       && match.getAwayTeamScore() != null;
        if (!live) {
            return emptyLiveDetails();
        }
        JsonNode summaryRoot = loadEspnSummaryRoot(match.getEspnId(), match.getCompetition());
        String homeCode = displayBonusCode(match.getHomeEspnCode(), match.getHomeTeamId(), "HOME");
        String awayCode = displayBonusCode(match.getAwayEspnCode(), match.getAwayTeamId(), "AWAY");
        TeamFormationItem homeFormation = loadTeamFormation(summaryRoot, "home", homeCode);
        TeamFormationItem awayFormation = loadTeamFormation(summaryRoot, "away", awayCode);
        List<LineupPlayerItem> homeLineup = toLineupItemsFromFormation(homeFormation);
        List<LineupPlayerItem> awayLineup = toLineupItemsFromFormation(awayFormation);
        List<MatchEventItem> events = loadLiveEvents(summaryRoot);
        List<MatchStatItem> matchStats = loadLiveStats(summaryRoot);
        return new LiveMatchDetailsResponse(
                true,
                homeLineup,
                awayLineup,
                homeFormation,
                awayFormation,
                events,
                matchStats,
                pitchColorFromFormation(homeFormation, "#ffffff"),
                pitchColorFromFormation(awayFormation, "#c0c0c0"),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getStatus()
        );
    }

    private static LiveMatchDetailsResponse emptyLiveDetails() {
        return new LiveMatchDetailsResponse(
                false, List.of(), List.of(), null, null, List.of(), List.of(), null, null, null, null, null);
    }

    public LeaderboardResponse leaderboard(String telegramId, Integer weekId) {
        requireUser(telegramId);
        List<LeaderboardEntry> entries;
        String title;
        boolean liveActive = false;
        if (weekId != null) {
            Map<String, Integer> points = predictionService.getWeeklyUsersPoints(weekId);
            if (weekId == DaoUtil.currentWeekId) {
                Map<String, Integer> provisional = computeCurrentWeekProvisionalPoints(weekId);
                List<LeaderboardEntry> liveEntries = new ArrayList<>();
                for (User user : DaoUtil.USERS.values()) {
                    String login = user.getLogin();
                    int base = points.getOrDefault(login, 0);
                    int prov = provisional.getOrDefault(login, 0);
                    int delta = prov - base;
                    if (delta != 0) {
                        liveActive = true;
                    }
                    liveEntries.add(new LeaderboardEntry(login, base, prov, delta));
                }
                title = liveActive ? "Очки за " + weekId + " тур (live)" : "Очки за " + weekId + " тур";
                entries = liveEntries.stream()
                        .sorted(Comparator.comparingInt((LeaderboardEntry e) -> e.provisionalPoints() != null ? e.provisionalPoints() : e.points())
                                .reversed()
                                .thenComparing(LeaderboardEntry::login))
                        .toList();
            } else {
                title = "Очки за " + weekId + " тур";
                entries = points.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry::getKey))
                        .map(e -> new LeaderboardEntry(e.getKey(), e.getValue(), null, null))
                        .toList();
            }
        } else {
            int currentWeekId = DaoUtil.currentWeekId;
            Map<String, Integer> seasonPoints = new LinkedHashMap<>(predictionService.getAllPointsByUsers());
            Map<String, Integer> weekStored = predictionService.getWeeklyUsersPoints(currentWeekId);
            Map<String, Integer> weekProvisional = computeCurrentWeekProvisionalPoints(currentWeekId);
            Map<String, Integer> cupLive = computeLiveCupProvisionalPoints();
            Map<String, Integer> seasonProvisional = new LinkedHashMap<>();

            for (User user : DaoUtil.USERS.values()) {
                String login = user.getLogin();
                int base = seasonPoints.getOrDefault(login, 0);
                int storedWeek = weekStored.getOrDefault(login, 0);
                int provisionalWeek = weekProvisional.getOrDefault(login, 0);
                int cupPts = cupLive.getOrDefault(login, 0);
                int liveDelta = provisionalWeek - storedWeek + cupPts;
                if (liveDelta != 0) {
                    liveActive = true;
                }
                seasonProvisional.put(login, base + liveDelta);
                seasonPoints.putIfAbsent(login, base);
            }
            title = liveActive ? "Общий зачёт (live)" : "Общий зачёт";
            entries = seasonProvisional.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(e -> {
                        String login = e.getKey();
                        int base = seasonPoints.getOrDefault(login, 0);
                        int provisional = e.getValue();
                        int delta = provisional - base;
                        return new LeaderboardEntry(login, base, provisional, delta);
                    })
                    .toList();
        }
        return new LeaderboardResponse(entries, weekId, title, liveActive);
    }

    public TodayMatchesResponse todayMatches(String telegramId) {
        User user = requireUser(telegramId);
        List<Match> matches = matchService.findAllByTodayDate();
        oddsService.ensureFresh(matches);
        Map<Integer, Prediction> predictions = predictionService.predictionsByMatchForUser(
                telegramId,
                matches.stream().map(Match::getPublicId).toList()
        );
        List<MatchItem> items = new ArrayList<>(matches.stream()
                .map(match -> {
                    Prediction prediction = predictions.get(match.getPublicId());
                    boolean weekBonus = userWeekBonusMatchService.isWeekBonusMatch(
                            user.getId(), match.getWeekId(), match.getPublicId());
                    return toMatchItem(match, telegramId, prediction != null, prediction, null, weekBonus);
                })
                .toList());
        List<BonusMatch> cups = bonusMatchDao.findAllByDate(java.time.LocalDate.now());
        if (!cups.isEmpty()) {
            Map<Integer, Prediction> cupPreds = predictionService.predictionsByBonusMatchForUser(
                    telegramId, cups.stream().map(BonusMatch::getPublicId).toList());
            for (BonusMatch cup : cups) {
                items.add(toBonusMatchItem(cup, cupPreds.get(cup.getPublicId())));
            }
            items.sort(Comparator
                    .comparing((MatchItem m) -> m.kickoff() == null ? "" : m.kickoff())
                    .thenComparingInt(MatchItem::publicId));
        }
        boolean hasLive = items.stream().anyMatch(m -> isLiveStatus(m.status()));
        return new TodayMatchesResponse(items, hasLive);
    }

    public CrowdMeterResponse crowdMeter(String telegramId, int matchPublicId) {
        requireUser(telegramId);
        Match match = matchService.findByPublicId(matchPublicId);
        if (match == null) {
            throw new MiniAppException(404, "Матч не найден");
        }
        List<Prediction> predictions = predictionService.getByMatchPublicId(matchPublicId).stream()
                .filter(p -> p.getHomeTeamScore() != null && p.getAwayTeamScore() != null)
                .toList();
        int total = predictions.size();
        if (total == 0) {
            return new CrowdMeterResponse(matchPublicId, 0, 0, 0, 0, List.of());
        }
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        Map<String, Integer> scoreCounts = new LinkedHashMap<>();
        for (Prediction p : predictions) {
            int hs = p.getHomeTeamScore();
            int as = p.getAwayTeamScore();
            if (hs > as) {
                homeWins++;
            } else if (hs < as) {
                awayWins++;
            } else {
                draws++;
            }
            String key = hs + ":" + as;
            scoreCounts.merge(key, 1, Integer::sum);
        }
        List<CrowdScoreBucket> top = scoreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> new CrowdScoreBucket(e.getKey(), e.getValue(), percent(e.getValue(), total)))
                .toList();
        return new CrowdMeterResponse(
                matchPublicId,
                total,
                percent(homeWins, total),
                percent(draws, total),
                percent(awayWins, total),
                top
        );
    }

    public WeekReviewResponse weekReview(String telegramId, int weekId) {
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        Map<Integer, Prediction> predictions = predictionService.getAllWeeklyPredictionsByUserTelegramId(weekId, telegramId)
                .stream()
                .collect(Collectors.toMap(
                        mp -> mp.match().getPublicId(),
                        MatchPrediction::prediction,
                        (a, b) -> a
                ));
        Map<Integer, int[]> kickoffs = bettingRecommendationService.kickoffScoresByMatchIds(
                matches.stream().map(Match::getPublicId).toList()
        );
        List<WeekReviewItem> items = new ArrayList<>();
        int total = 0;
        for (Match match : matches) {
            Prediction prediction = predictions.get(match.getPublicId());
            boolean hasPrediction = prediction != null
                    && prediction.getHomeTeamScore() != null
                    && prediction.getAwayTeamScore() != null;
            Integer pts = resolveWeekReviewPoints(match, prediction);
            if (pts != null) {
                total += pts;
            }
            int[] kickoff = kickoffs.get(match.getPublicId());
            items.add(new WeekReviewItem(
                    match.getPublicId(),
                    teamCode(match.getHomeTeamId()),
                    teamCode(match.getAwayTeamId()),
                    match.getStatus(),
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    hasPrediction ? prediction.getHomeTeamScore() : null,
                    hasPrediction ? prediction.getAwayTeamScore() : null,
                    pts,
                    hasPrediction,
                    kickoff != null ? kickoff[0] : null,
                    kickoff != null ? kickoff[1] : null
            ));
        }
        return new WeekReviewResponse(weekId, total, items);
    }

    private Integer resolveWeekReviewPoints(Match match, Prediction prediction) {
        boolean finished = isFinishedStatus(match.getStatus());
        boolean liveLike = isLiveStatus(match.getStatus())
                           || (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null
                               && !finished && !"ns".equalsIgnoreCase(String.valueOf(match.getStatus())));
        if (!finished && !liveLike) {
            return null;
        }
        if (finished && prediction != null && prediction.getPoints() != null) {
            return prediction.getPoints();
        }
        if (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null) {
            return PredictionService.computePoints(
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    prediction != null ? prediction.getHomeTeamScore() : null,
                    prediction != null ? prediction.getAwayTeamScore() : null
            );
        }
        return null;
    }

    public PointsChartResponse pointsChart(String telegramId) {
        requireUser(telegramId);
        Map<String, Map<Integer, Integer>> raw = predictionService.getAllUsersCumulativePoints().entrySet().stream()
                .sorted((e1, e2) -> {
                    int max1 = e1.getValue().values().stream().max(Integer::compareTo).orElse(0);
                    int max2 = e2.getValue().values().stream().max(Integer::compareTo).orElse(0);
                    return Integer.compare(max2, max1);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        Set<Integer> weekSet = new TreeSet<>();
        raw.values().forEach(m -> weekSet.addAll(m.keySet()));
        List<Integer> weeks = new ArrayList<>(weekSet);

        List<ChartSeries> series = new ArrayList<>();
        int limit = Math.min(6, raw.size());
        int i = 0;
        for (Map.Entry<String, Map<Integer, Integer>> entry : raw.entrySet()) {
            if (i++ >= limit) {
                break;
            }
            String login = entry.getKey();
            String label = login.length() >= 3 ? login.substring(0, 3).toUpperCase() : login.toUpperCase();
            List<Integer> points = weeks.stream()
                    .map(entry.getValue()::get)
                    .toList();
            series.add(new ChartSeries(login, label, points));
        }
        return new PointsChartResponse(weeks, series);
    }

    public List<StandingItem> standings(String telegramId) {
        requireUser(telegramId);
        List<Standing> liveTable = matchService.getStandings();
        Map<Integer, Integer> basePlaceByTeam = new LinkedHashMap<>();
        int basePlace = 1;
        for (Standing standing : matchService.getStandingsFinishedOnly()) {
            basePlaceByTeam.put(standing.getTeamId(), basePlace++);
        }

        Map<Integer, Match> liveMatchByTeam = new LinkedHashMap<>();
        for (Match match : matchService.findOnlineMatches()) {
            if (!isLiveStatus(match.getStatus())) {
                continue;
            }
            if (match.getHomeTeamScore() == null || match.getAwayTeamScore() == null) {
                continue;
            }
            liveMatchByTeam.put(match.getHomeTeamId(), match);
            liveMatchByTeam.put(match.getAwayTeamId(), match);
        }
        boolean hasLive = !liveMatchByTeam.isEmpty();

        List<StandingItem> items = new ArrayList<>();
        int place = 1;
        for (Standing standing : liveTable) {
            Team team = DaoUtil.TEAMS.get(standing.getTeamId());
            int currentPlace = place++;
            int previousPlace = basePlaceByTeam.getOrDefault(standing.getTeamId(), currentPlace);
            int placeDelta = hasLive ? previousPlace - currentPlace : 0;
            Match liveMatch = liveMatchByTeam.get(standing.getTeamId());
            String liveScore = null;
            String liveResult = null;
            if (liveMatch != null) {
                liveScore = liveScoreForTeam(liveMatch, standing.getTeamId());
                liveResult = liveResultForTeam(liveMatch, standing.getTeamId());
            }
            items.add(new StandingItem(
                    currentPlace,
                    team.getCode(),
                    team.getName(),
                    teamLogoPath(standing.getTeamId()),
                    standing.getGames(),
                    standing.getWon(),
                    standing.getDrawn(),
                    standing.getLost(),
                    standing.getGoalsFor(),
                    standing.getGoalsAgainst(),
                    standing.getPoints(),
                    placeDelta,
                    liveScore,
                    liveResult
            ));
        }
        return items;
    }

    static String liveScoreForTeam(Match match, int teamId) {
        int home = match.getHomeTeamScore() == null ? 0 : match.getHomeTeamScore();
        int away = match.getAwayTeamScore() == null ? 0 : match.getAwayTeamScore();
        boolean isHome = match.getHomeTeamId() == teamId;
        int mine = isHome ? home : away;
        int theirs = isHome ? away : home;
        return mine + "-" + theirs;
    }

    static String liveResultForTeam(Match match, int teamId) {
        int home = match.getHomeTeamScore() == null ? 0 : match.getHomeTeamScore();
        int away = match.getAwayTeamScore() == null ? 0 : match.getAwayTeamScore();
        boolean isHome = match.getHomeTeamId() == teamId;
        int mine = isHome ? home : away;
        int theirs = isHome ? away : home;
        if (mine > theirs) {
            return "W";
        }
        if (mine < theirs) {
            return "L";
        }
        return "D";
    }

    public TeamMatchesResponse teamMatches(String telegramId, String teamCode) {
        requireUser(telegramId);
        String normalizedCode = teamCode.toUpperCase();
        Team team = DaoUtil.TEAMS.values().stream()
                .filter(t -> normalizedCode.equals(t.getCode()))
                .findFirst()
                .orElseThrow(() -> new MiniAppException(404, "Команда не найдена"));

        List<TeamMatchItem> lastMatches = matchService.findLastFinishedByTeamId(team.getPublicId(), 5).stream()
                .map(this::toTeamMatchItem)
                .toList();

        List<TeamMatchItem> upcomingMatches = matchService.findNextByTeamId(team.getPublicId(), 5).stream()
                .map(this::toTeamMatchItem)
                .toList();

        return new TeamMatchesResponse(team.getCode(), team.getName(), lastMatches, upcomingMatches);
    }

    public List<H2hItem> h2h(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        List<HeadToHead> items = headToHeadService.findAllByTwoTeamsCode(homeCode.toUpperCase(), awayCode.toUpperCase());
        return items.stream()
                .map(h -> new H2hItem(
                        h.getLeagueName(),
                        h.getLocalDateTime() != null ? h.getLocalDateTime().format(KICKOFF) : "",
                        teamCode(h.getHomeTeamId()),
                        teamCode(h.getAwayTeamId()),
                        h.getHomeTeamScore(),
                        h.getAwayTeamScore()
                ))
                .toList();
    }

    public ActionResponse savePrediction(String telegramId, PredictRequest request) {
        requireUser(telegramId);
        if (request.homeScore() < 0 || request.homeScore() > 5 || request.awayScore() < 0 || request.awayScore() > 5) {
            return new ActionResponse(false, "Счёт должен быть от 0 до 5.");
        }
        if (request.bonusMatchId() != null) {
            BonusMatch bonus = bonusMatchDao.findByPublicId(request.bonusMatchId());
            if (!canPredictBonus(bonus)) {
                return new ActionResponse(false, "Время для прогноза истекло (принимаются до начала матча + 5 мин).");
            }
            predictionService.saveBonus(telegramId, bonus.getPublicId(), request.homeScore(), request.awayScore());
            return new ActionResponse(true, "Прогноз на бонус-матч сохранён", request.homeScore(), request.awayScore());
        }
        String home = request.homeCode().toUpperCase();
        String away = request.awayCode().toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!canPredict(match)) {
            return new ActionResponse(false, "Время для прогноза истекло (принимаются до начала матча + 5 мин).");
        }
        boolean exists = predictionService.isExist(telegramId, match.getPublicId());
        predictionService.save(telegramId, home, away, request.homeScore(), request.awayScore());
        return new ActionResponse(
                true,
                exists ? "Прогноз обновлён" : "Прогноз сохранён",
                request.homeScore(),
                request.awayScore()
        );
    }

    public ActionResponse deletePrediction(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        String home = homeCode.toUpperCase();
        String away = awayCode.toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!canPredict(match)) {
            return new ActionResponse(false, "Время для удаления прогноза истекло.");
        }
        predictionService.deleteByUserTelegramIdAndTeams(telegramId, home, away);
        return new ActionResponse(true, "Прогноз удалён");
    }

    public ActionResponse deleteBonusPrediction(String telegramId, int bonusMatchId) {
        requireUser(telegramId);
        BonusMatch bonus = bonusMatchDao.findByPublicId(bonusMatchId);
        if (!canPredictBonus(bonus)) {
            return new ActionResponse(false, "Время для удаления прогноза истекло.");
        }
        predictionService.deleteBonusByUserTelegramId(telegramId, bonusMatchId);
        return new ActionResponse(true, "Прогноз удалён");
    }

    private MatchItem toMatchItem(
            Match match,
            String telegramId,
            boolean hasPrediction,
            Prediction prediction,
            int[] kickoffScore,
            boolean weekBonus
    ) {
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        OddsService.Odd odd = oddsService.getOdd(match.getPublicId());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = match.getLocalDateTime() == null ? null : match.getLocalDateTime().plusMinutes(5);
        Long predictSecondsLeft = null;
        if (until != null && canPredict(match)) {
            predictSecondsLeft = java.time.Duration.between(now, until).getSeconds();
            if (predictSecondsLeft < 0) {
                predictSecondsLeft = 0L;
            }
        }
        Long kickoffSecondsLeft = null;
        if (match.getLocalDateTime() != null && isNotStartedStatus(match.getStatus())) {
            kickoffSecondsLeft = java.time.Duration.between(now, match.getLocalDateTime()).getSeconds();
        }
        return new MatchItem(
                match.getPublicId(),
                match.getWeekId(),
                home.getCode(),
                home.getName(),
                teamLogoPath(match.getHomeTeamId()),
                away.getCode(),
                away.getName(),
                teamLogoPath(match.getAwayTeamId()),
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : "",
                canPredict(match),
                hasPrediction,
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null,
                prediction != null ? prediction.getPoints() : null,
                odd != null ? odd.home() : null,
                odd != null ? odd.draw() : null,
                odd != null ? odd.away() : null,
                until != null ? until.format(KICKOFF) : null,
                predictSecondsLeft,
                kickoffSecondsLeft,
                kickoffScore != null ? kickoffScore[0] : null,
                kickoffScore != null ? kickoffScore[1] : null,
                weekBonus,
                false,
                null,
                null
        );
    }

    private MatchItem toBonusMatchItem(BonusMatch match, Prediction prediction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = match.getLocalDateTime() == null ? null : match.getLocalDateTime().plusMinutes(5);
        Long predictSecondsLeft = null;
        if (until != null && canPredictBonus(match)) {
            predictSecondsLeft = java.time.Duration.between(now, until).getSeconds();
            if (predictSecondsLeft < 0) {
                predictSecondsLeft = 0L;
            }
        }
        Long kickoffSecondsLeft = null;
        if (match.getLocalDateTime() != null && isNotStartedStatus(match.getStatus())) {
            kickoffSecondsLeft = java.time.Duration.between(now, match.getLocalDateTime()).getSeconds();
        }
        String homeCode = displayBonusCode(match.getHomeEspnCode(), match.getHomeTeamId(), "?");
        String awayCode = displayBonusCode(match.getAwayEspnCode(), match.getAwayTeamId(), "?");
        String homeLogo = match.getHomeTeamId() != null
                ? teamLogoPath(match.getHomeTeamId())
                : (match.getHomeLogoUrl() != null ? match.getHomeLogoUrl() : "");
        String awayLogo = match.getAwayTeamId() != null
                ? teamLogoPath(match.getAwayTeamId())
                : (match.getAwayLogoUrl() != null ? match.getAwayLogoUrl() : "");
        return new MatchItem(
                match.getPublicId(),
                0,
                homeCode,
                match.getHomeName() != null ? match.getHomeName() : homeCode,
                homeLogo,
                awayCode,
                match.getAwayName() != null ? match.getAwayName() : awayCode,
                awayLogo,
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : "",
                canPredictBonus(match),
                prediction != null,
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null,
                prediction != null ? prediction.getPoints() : null,
                null,
                null,
                null,
                until != null ? until.format(KICKOFF) : null,
                predictSecondsLeft,
                kickoffSecondsLeft,
                null,
                null,
                false,
                true,
                match.getCompetition(),
                match.getPublicId()
        );
    }

    private static boolean canPredictBonus(BonusMatch match) {
        if (match == null || match.getLocalDateTime() == null) {
            return false;
        }
        String status = match.getStatus();
        if (status != null && CLOSED_MATCH_STATUSES.contains(status.toLowerCase())) {
            return false;
        }
        return LocalDateTime.now().isBefore(match.getLocalDateTime().plusMinutes(5));
    }

    private static boolean isNotStartedStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return Set.of("ns", "pst", "tbd").contains(status.toLowerCase());
    }

    private static boolean isLiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.toLowerCase();
        return !Set.of("ns", "ft", "aet", "pen", "pst", "canc", "abd", "awrd", "wo").contains(s);
    }

    private static boolean isFinishedStatus(String status) {
        return status != null && CLOSED_MATCH_STATUSES.contains(status.toLowerCase());
    }

    private static int percent(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    /**
     * Provisional cup points for in-play (non-FT) bonus matches — added to live Общий зачёт.
     * Finished cup FT points are already in floored season totals.
     */
    private Map<String, Integer> computeLiveCupProvisionalPoints() {
        Map<String, Integer> sums = new LinkedHashMap<>();
        for (User user : DaoUtil.USERS.values()) {
            sums.put(user.getLogin(), 0);
        }
        List<BonusMatch> liveCups = bonusMatchDao.findOnline();
        for (BonusMatch match : liveCups) {
            if (match.getHomeTeamScore() == null || match.getAwayTeamScore() == null) {
                continue;
            }
            Map<Integer, Prediction> byUser = predictionService.getByBonusMatchPublicId(match.getPublicId()).stream()
                    .collect(Collectors.toMap(Prediction::getUserId, p -> p, (a, b) -> a));
            for (User user : DaoUtil.USERS.values()) {
                Prediction p = byUser.get(user.getId());
                int pts = PredictionService.computePoints(
                        ScoringMode.CUP,
                        match.getHomeTeamScore(),
                        match.getAwayTeamScore(),
                        p != null ? p.getHomeTeamScore() : null,
                        p != null ? p.getAwayTeamScore() : null
                );
                sums.merge(user.getLogin(), pts, Integer::sum);
            }
        }
        return sums;
    }

    private static final List<String> CUP_COMPETITION_ORDER = List.of(
            "eng.fa", "eng.league_cup", "uefa.champions", "uefa.europa", "uefa.europa.conf"
    );

    public List<CupCompetitionItem> cupCompetitions(String telegramId) {
        requireUser(telegramId);
        List<BonusMatch> all = bonusMatchDao.findAllOrdered();
        Map<String, List<BonusMatch>> byComp = new LinkedHashMap<>();
        for (String c : CUP_COMPETITION_ORDER) {
            byComp.put(c, new ArrayList<>());
        }
        for (BonusMatch m : all) {
            if (m.getCompetition() == null) {
                continue;
            }
            byComp.computeIfAbsent(m.getCompetition(), k -> new ArrayList<>()).add(m);
        }
        List<Integer> ids = all.stream().map(BonusMatch::getPublicId).toList();
        Map<Integer, Prediction> myPreds = predictionService.predictionsByBonusMatchForUser(telegramId, ids);
        List<CupCompetitionItem> items = new ArrayList<>();
        for (Map.Entry<String, List<BonusMatch>> e : byComp.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            boolean has = e.getValue().stream().anyMatch(m -> {
                Prediction p = myPreds.get(m.getPublicId());
                return p != null && p.getHomeTeamScore() != null && p.getAwayTeamScore() != null;
            });
            items.add(new CupCompetitionItem(
                    e.getKey(),
                    competitionLabel(e.getKey()),
                    has,
                    e.getValue().size()
            ));
        }
        return items;
    }

    public List<MatchItem> cupMatches(String telegramId, String competition) {
        requireUser(telegramId);
        List<BonusMatch> matches = bonusMatchDao.findByCompetition(competition);
        List<Integer> ids = matches.stream().map(BonusMatch::getPublicId).toList();
        Map<Integer, Prediction> preds = predictionService.predictionsByBonusMatchForUser(telegramId, ids);
        return matches.stream()
                .sorted(BonusMatch.BY_KICKOFF_THEN_PUBLIC_ID)
                .map(m -> toBonusMatchItem(m, preds.get(m.getPublicId())))
                .toList();
    }

    public CupReviewResponse cupReview(String telegramId, String competition) {
        requireUser(telegramId);
        List<BonusMatch> matches = bonusMatchDao.findByCompetition(competition);
        List<Integer> ids = matches.stream().map(BonusMatch::getPublicId).toList();
        Map<Integer, Prediction> preds = predictionService.predictionsByBonusMatchForUser(telegramId, ids);
        List<WeekReviewItem> items = new ArrayList<>();
        int total = 0;
        for (BonusMatch match : matches.stream().sorted(BonusMatch.BY_KICKOFF_THEN_PUBLIC_ID).toList()) {
            Prediction prediction = preds.get(match.getPublicId());
            boolean hasPrediction = prediction != null
                    && prediction.getHomeTeamScore() != null
                    && prediction.getAwayTeamScore() != null;
            Integer pts = resolveCupReviewPoints(match, prediction);
            if (pts != null) {
                total += pts;
            }
            String home = displayBonusCode(match.getHomeEspnCode(), match.getHomeTeamId(), "?");
            String away = displayBonusCode(match.getAwayEspnCode(), match.getAwayTeamId(), "?");
            items.add(new WeekReviewItem(
                    match.getPublicId(),
                    home,
                    away,
                    match.getStatus(),
                    match.getHomeTeamScore(),
                    match.getAwayTeamScore(),
                    hasPrediction ? prediction.getHomeTeamScore() : null,
                    hasPrediction ? prediction.getAwayTeamScore() : null,
                    pts,
                    hasPrediction,
                    null,
                    null
            ));
        }
        return new CupReviewResponse(competition, competitionLabel(competition), total, items);
    }

    private Integer resolveCupReviewPoints(BonusMatch match, Prediction prediction) {
        boolean finished = isFinishedStatus(match.getStatus());
        boolean liveLike = isLiveStatus(match.getStatus())
                           || (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null
                               && !finished && !"ns".equalsIgnoreCase(String.valueOf(match.getStatus())));
        if (!finished && !liveLike) {
            return null;
        }
        if (finished && prediction != null && prediction.getPoints() != null) {
            return prediction.getPoints();
        }
        if (match.getHomeTeamScore() == null || match.getAwayTeamScore() == null) {
            return null;
        }
        return PredictionService.computePoints(
                ScoringMode.CUP,
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null
        );
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

    private Map<String, Integer> computeCurrentWeekProvisionalPoints(int weekId) {
        Map<String, List<Integer>> ordered = new LinkedHashMap<>();
        for (User user : DaoUtil.USERS.values()) {
            ordered.put(user.getLogin(), new ArrayList<>());
        }
        Map<Integer, Map<Integer, Prediction>> predictionsByMatch = predictionService.findAllByWeekId(weekId).stream()
                .collect(Collectors.groupingBy(
                        mp -> mp.match().getPublicId(),
                        Collectors.toMap(
                                mp -> mp.prediction().getUserId(),
                                MatchPrediction::prediction,
                                (left, right) -> left
                        )
                ));
        List<Match> weekMatches = matchService.findAllByWeekId(weekId).stream()
                .sorted(Match.BY_KICKOFF_THEN_PUBLIC_ID)
                .toList();
        userWeekBonusMatchService.ensureAssignedForWeek(weekId);
        for (Match match : weekMatches) {
            Map<Integer, Prediction> byUser = predictionsByMatch.getOrDefault(match.getPublicId(), Map.of());
            boolean finished = isFinishedStatus(match.getStatus());
            boolean liveLike = isLiveStatus(match.getStatus())
                               || (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null
                                   && !finished && !"ns".equalsIgnoreCase(String.valueOf(match.getStatus())));
            if (!finished && !liveLike) {
                continue;
            }
            for (User user : DaoUtil.USERS.values()) {
                Prediction p = byUser.get(user.getId());
                ScoringMode mode = userWeekBonusMatchService.isWeekBonusMatch(
                        user.getId(), weekId, match.getPublicId())
                        ? ScoringMode.EPL_WEEK_BONUS
                        : ScoringMode.EPL;
                int pts;
                if (finished && p != null && p.getPoints() != null) {
                    pts = p.getPoints();
                } else if (match.getHomeTeamScore() != null && match.getAwayTeamScore() != null) {
                    pts = PredictionService.computePoints(
                            mode,
                            match.getHomeTeamScore(),
                            match.getAwayTeamScore(),
                            p != null ? p.getHomeTeamScore() : null,
                            p != null ? p.getAwayTeamScore() : null
                    );
                } else {
                    continue;
                }
                ordered.get(user.getLogin()).add(pts);
            }
        }
        return FlooredPointsService.floorProvisional(ordered);
    }

    private static boolean canPredict(Match match) {
        if (match == null || match.getLocalDateTime() == null) {
            return false;
        }
        String status = match.getStatus();
        if (status != null && CLOSED_MATCH_STATUSES.contains(status.toLowerCase())) {
            return false;
        }
        return LocalDateTime.now().isBefore(match.getLocalDateTime().plusMinutes(5));
    }

    private static String teamCode(int teamId) {
        return DaoUtil.TEAMS.get(teamId).getCode();
    }

    /** Prefer stored abbr (ingest already maps ESPN MUN→BAY); fall back to linked EPL team. */
    private static String displayBonusCode(String espnCode, Integer teamId, String fallback) {
        if (espnCode != null && !espnCode.isBlank()) {
            return espnCode;
        }
        if (teamId != null) {
            Team t = DaoUtil.TEAMS.get(teamId);
            if (t != null && t.getCode() != null && !t.getCode().isBlank()) {
                return t.getCode();
            }
        }
        return fallback;
    }

    private static String teamLogoPath(int teamId) {
        return "/img/teams/" + teamId + ".webp";
    }

    private TeamMatchItem toTeamMatchItem(Match match) {
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        return new TeamMatchItem(
                match.getPublicId(),
                match.getWeekId(),
                home.getCode(),
                home.getName(),
                teamLogoPath(match.getHomeTeamId()),
                away.getCode(),
                away.getName(),
                teamLogoPath(match.getAwayTeamId()),
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : ""
        );
    }

    private List<LineupPlayerItem> toLineupItems(List<Lineup> lineup) {
        if (lineup == null || lineup.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> order = Map.of("G", 1, "D", 2, "M", 3, "F", 4);
        return lineup.stream()
                .filter(item -> item != null && item.getPlayer() != null)
                .sorted(Comparator.comparingInt(item -> order.getOrDefault(item.getPlayer().getPos(), 99)))
                .map(item -> new LineupPlayerItem(
                        item.getPlayer().getNumber(),
                        item.getPlayer().getName(),
                        item.getPlayer().getPos()
                ))
                .toList();
    }

    private static List<LineupPlayerItem> toLineupItemsFromFormation(TeamFormationItem formation) {
        if (formation == null || formation.starters() == null || formation.starters().isEmpty()) {
            return List.of();
        }
        return formation.starters().stream()
                .map(p -> new LineupPlayerItem(p.number(), p.name(), p.position()))
                .toList();
    }

    private TeamFormationItem loadTeamFormation(JsonNode root, String side, String teamCode) {
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

    private FormationPlayerItem toFormationPlayer(JsonNode athleteNode) {
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

    private JsonNode loadEspnSummaryRoot(Match match) {
        if (match == null) {
            return null;
        }
        return loadEspnSummaryRoot(match.getEspnId(), "eng.1");
    }

    private JsonNode loadEspnSummaryRoot(String espnId, String competition) {
        if (espnId == null || espnId.isBlank()) {
            return null;
        }
        String league = (competition == null || competition.isBlank()) ? "eng.1" : competition;
        try {
            return apiClient.fetchEspnSummary(espnId, league);
        } catch (Exception e) {
            log.warn("MiniApp summary fetch failed: league={}, espnId={}, error={}",
                    league, espnId, e.getMessage());
            return null;
        }
    }

    private List<MatchEventItem> loadLiveEvents(JsonNode root) {
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

    private String extractPrimaryParticipantName(JsonNode play) {
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

    private List<MatchStatItem> loadLiveStats(JsonNode root) {
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

    private MatchStatItem stat(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                pickStatValue(home, primaryKey, fallbackKey),
                pickStatValue(away, primaryKey, fallbackKey)
        );
    }

    private MatchStatItem statPercent(String primaryKey, String fallbackKey, String label, Map<String, String> home, Map<String, String> away) {
        return new MatchStatItem(
                primaryKey,
                label,
                formatPercent(pickStatValue(home, primaryKey, fallbackKey)),
                formatPercent(pickStatValue(away, primaryKey, fallbackKey))
        );
    }

    private String pickStatValue(Map<String, String> map, String primaryKey, String fallbackKey) {
        String value = map.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = fallbackKey != null ? map.get(fallbackKey) : null;
        }
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String formatPercent(String value) {
        if ("—".equals(value)) {
            return value;
        }
        if (value.endsWith("%")) {
            return value;
        }
        return value + "%";
    }

    private Map<String, String> extractStatsMap(JsonNode statsNode) {
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

    private boolean shouldSkipCommentaryItem(String text, String type, String minute) {
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

    private static String pitchColorFromFormation(TeamFormationItem formation, String fallback) {
        if (formation != null && formation.kitColor() != null && !formation.kitColor().isBlank()) {
            return formation.kitColor();
        }
        return fallback;
    }

    private List<FormItem> buildRecentForm(int teamId, int limit) {
        return matchService.findLastFinishedByTeamId(teamId, limit).stream()
                .map(match -> toFormItem(match, teamId))
                .toList();
    }

    private FormItem toFormItem(Match match, int teamId) {
        boolean teamIsHome = match.getHomeTeamId() == teamId;
        int ownScore = teamIsHome ? match.getHomeTeamScore() : match.getAwayTeamScore();
        int opponentScore = teamIsHome ? match.getAwayTeamScore() : match.getHomeTeamScore();
        int opponentId = teamIsHome ? match.getAwayTeamId() : match.getHomeTeamId();
        Team opponent = DaoUtil.TEAMS.get(opponentId);
        String outcome = ownScore > opponentScore ? "W" : ownScore < opponentScore ? "L" : "D";
        return new FormItem(
                outcome,
                ownScore,
                opponentScore,
                opponent != null ? opponent.getCode() : "?",
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : "",
                teamIsHome
        );
    }

    private List<MatchNewsItem> loadMatchNews(Match match, int limit) {
        if (match == null) {
            return List.of();
        }
        try {
            List<MatchNewsItem> news = new ArrayList<>();
            Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
            Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
            if (home != null) {
                news.addAll(loadTeamNews(home.getCode(), Math.max(limit, 3)));
            }
            if (away != null) {
                news.addAll(loadTeamNews(away.getCode(), Math.max(limit, 3)));
            }
            news = news.stream()
                    .filter(item -> item.title() != null && !item.title().isBlank())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    MatchNewsItem::url,
                                    item -> item,
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ),
                            map -> new ArrayList<>(map.values())
                    ));
            news.sort(Comparator.comparing(MatchNewsItem::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (news.size() > limit) {
                return news.subList(0, limit);
            }
            return news;
        } catch (Exception e) {
            log.warn("MiniApp match news load failed: matchId={}, error={}",
                    match.getPublicId(), e.getMessage());
            return List.of();
        }
    }

    private List<MatchNewsItem> loadTeamNews(String teamCode, int limit) {
        Integer tagId = TEAM_NEWS_TAG_IDS.get(teamCode);
        if (tagId == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CachedTeamNews cached = teamNewsCache.get(teamCode);
        if (cached != null && now - cached.cachedAtMs() <= TEAM_NEWS_CACHE_MS) {
            return cached.news();
        }
        try {
            URL source = new URL(SPORTS_RU_TEAM_RSS + tagId);
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(source));
            List<MatchNewsItem> items = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (items.size() >= limit) {
                    break;
                }
                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String url = entry.getLink() == null ? "" : entry.getLink().trim();
                if (title.isBlank() || url.isBlank()) {
                    continue;
                }
                String published = "";
                if (entry.getPublishedDate() != null) {
                    published = LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.of(AppTimeZones.DISPLAY.getId()))
                            .format(NEWS_TS);
                }
                items.add(new MatchNewsItem(title, url, published));
            }
            teamNewsCache.put(teamCode, new CachedTeamNews(now, items));
            return items;
        } catch (Exception e) {
            log.warn("MiniApp team news load failed: teamCode={}, tagId={}, error={}",
                    teamCode, tagId, e.getMessage());
            return List.of();
        }
    }

    private record CachedTeamNews(long cachedAtMs, List<MatchNewsItem> news) {
    }
}
