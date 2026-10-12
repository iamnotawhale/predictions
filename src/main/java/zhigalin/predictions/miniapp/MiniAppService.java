package zhigalin.predictions.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import zhigalin.predictions.recommender.RecommendationInsight;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import zhigalin.predictions.config.DeploymentInfoService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileTeamPredictionsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchRecommendationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ExplanationStatRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ScoreDistributionDto;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TopScoreDto;
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
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LineupPlayerItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveMatchDetailsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchEventItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchStatItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchInsightsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchNewsItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.InjuryItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileStatsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamFormationItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamPlayerLeaderItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamProfileResponse;
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
import zhigalin.predictions.service.api.EspnTeamRosterService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService;
import zhigalin.predictions.service.api.EspnTeamSeasonStatsService.TeamSeasonExtras;
import zhigalin.predictions.service.api.InjuryService;
import zhigalin.predictions.service.api.InjuryService.InjuryInfo;
import zhigalin.predictions.service.api.TeamLogoCacheService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamSeasonStats;
import zhigalin.predictions.recommender.FootyStatsStatsDao;
import zhigalin.predictions.recommender.model.FootyStatsExtendedMetrics;
import zhigalin.predictions.recommender.model.FootyStatsTeamSnapshot;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.UserWeekBonusMatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.predict.FlooredPointsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.UserPredictionStatsService;
import zhigalin.predictions.service.predict.ScoringMode;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MiniAppService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;
    private final HeadToHeadService headToHeadService;
    private final OddsService oddsService;
    private final ApiClient apiClient;
    private final InjuryService injuryService;
    private final TeamLogoCacheService teamLogoCacheService;
    private final EspnTeamRosterService espnTeamRosterService;
    private final EspnTeamSeasonStatsService espnTeamSeasonStatsService;
    private final FootyStatsStatsDao footyStatsStatsDao;
    private final ObjectMapper objectMapper;
    private final DeploymentInfoService deploymentInfoService;
    private final BettingRecommendationService bettingRecommendationService;
    private final UserWeekBonusMatchService userWeekBonusMatchService;
    private final UserPredictionStatsService userPredictionStatsService;
    private final BonusMatchDao bonusMatchDao;
    private final String adminChatId;
    private final TeamNewsService teamNewsService;

    public MiniAppService(
            UserService userService,
            MatchService matchService,
            PredictionService predictionService,
            HeadToHeadService headToHeadService,
            OddsService oddsService,
            ApiClient apiClient,
            InjuryService injuryService,
            TeamLogoCacheService teamLogoCacheService,
            EspnTeamRosterService espnTeamRosterService,
            EspnTeamSeasonStatsService espnTeamSeasonStatsService,
            FootyStatsStatsDao footyStatsStatsDao,
            ObjectMapper objectMapper,
            DeploymentInfoService deploymentInfoService,
            BettingRecommendationService bettingRecommendationService,
            UserWeekBonusMatchService userWeekBonusMatchService,
            UserPredictionStatsService userPredictionStatsService,
            BonusMatchDao bonusMatchDao,
            TeamNewsService teamNewsService,
            @Value("${chatId:}") String adminChatId
    ) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.headToHeadService = headToHeadService;
        this.oddsService = oddsService;
        this.apiClient = apiClient;
        this.injuryService = injuryService;
        this.teamLogoCacheService = teamLogoCacheService;
        this.espnTeamRosterService = espnTeamRosterService;
        this.espnTeamSeasonStatsService = espnTeamSeasonStatsService;
        this.footyStatsStatsDao = footyStatsStatsDao;
        this.objectMapper = objectMapper;
        this.deploymentInfoService = deploymentInfoService;
        this.bettingRecommendationService = bettingRecommendationService;
        this.userWeekBonusMatchService = userWeekBonusMatchService;
        this.userPredictionStatsService = userPredictionStatsService;
        this.bonusMatchDao = bonusMatchDao;
        this.teamNewsService = teamNewsService;
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

    public ProfileStatsResponse profileStats(String telegramId) {
        return userPredictionStatsService.build(requireUser(telegramId));
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
        List<Match> matches = matchService.findAllByWeekId(weekId);
        oddsService.refreshAsyncIfStale(matches);
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
            oddsService.refreshAsyncIfStale(List.of(match));
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
            return new MatchInsightsResponse(List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of());
        }
        List<FormItem> homeForm = buildRecentForm(match.getHomeTeamId(), 5);
        List<FormItem> awayForm = buildRecentForm(match.getAwayTeamId(), 5);
        List<MatchNewsItem> news = teamNewsService.loadMatchNews(match, 4);
        Team homeTeam = DaoUtil.team(match.getHomeTeamId());
        Team awayTeam = DaoUtil.team(match.getAwayTeamId());
        List<InjuryItem> injuries = toInjuryItems(injuryService.forTeams(
                homeTeam != null ? homeTeam.getCode() : null,
                awayTeam != null ? awayTeam.getCode() : null
        ));
        List<LineupPlayerItem> homeLineup = List.of();
        List<LineupPlayerItem> awayLineup = List.of();
        Map<Integer, List<Lineup>> lineups = apiClient.getLineupsFromEspnSummary(
                match.getEspnId(),
                match.getHomeTeamId(),
                match.getAwayTeamId()
        );
        if (lineups.isEmpty()) {
            lineups = apiClient.getLineups(match.getPublicId());
        }
        if (!lineups.isEmpty()) {
            homeLineup = toLineupItems(lineups.get(match.getHomeTeamId()));
            awayLineup = toLineupItems(lineups.get(match.getAwayTeamId()));
        }
        MatchRecommendationResponse recommendation = null;
        if (user.isBettingRecommenderEnabled()) {
            bettingRecommendationService.ensureCurrentWeekRecommendations();
            recommendation = bettingRecommendationService.recommendationForMatch(match.getPublicId())
                    .map(this::toRecommendationResponse)
                    .orElse(null);
        }
        return new MatchInsightsResponse(
                homeForm, awayForm, news, recommendation, injuries, homeLineup, awayLineup);
    }

    private List<InjuryItem> toInjuryItems(List<InjuryInfo> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return List.of();
        }
        return injuries.stream()
                .map(i -> new InjuryItem(
                        i.teamCode(),
                        i.playerName(),
                        i.kind() != null ? i.kind() : InjuryService.KIND_OTHER
                ))
                .toList();
    }

    private MatchRecommendationResponse toRecommendationResponse(MatchRecommendationSnapshot snapshot) {
        var explanation = snapshot.explanation() != null
                ? snapshot.explanation()
                : ExplanationBreakdown.empty();
        List<ExplanationStatRow> rows = explanation.rows() == null ? List.of() : explanation.rows().stream()
                .map(r -> new ExplanationStatRow(r.metric(), r.home(), r.away()))
                .toList();
        List<String> notes = explanation.notes() != null ? explanation.notes() : List.of();
        ScoreDistributionDto distribution = toDistributionDto(explanation.distribution());
        return new MatchRecommendationResponse(
                snapshot.recommendedHome(),
                snapshot.recommendedAway(),
                snapshot.expectedHomeGoals(),
                snapshot.expectedAwayGoals(),
                snapshot.scoreProbability(),
                rows,
                notes,
                snapshot.explanationLines(),
                snapshot.summary(),
                distribution,
                distribution == null ? null : RecommendationInsight.build(
                        toRawMatrix(distribution.matrix()),
                        snapshot.recommendedHome(),
                        snapshot.recommendedAway(),
                        snapshot.expectedHomeGoals(),
                        snapshot.expectedAwayGoals(),
                        notes.stream().anyMatch(n -> n != null && n.startsWith("Основа прогноза"))),
                snapshot.computedAt() != null ? snapshot.computedAt().toEpochMilli() : null,
                snapshot.kickoffFrozenAt() != null ? snapshot.kickoffFrozenAt().toEpochMilli() : null,
                bettingRecommendationService.seasonTrackRecord()
        );
    }

    private static ScoreDistributionDto toDistributionDto(
            zhigalin.predictions.recommender.model.ScoreDistribution distribution
    ) {
        if (distribution == null || !distribution.hasMatrix()) {
            return null;
        }
        // Recompute markets from matrix so older explanation_json without over05 stays correct.
        double[][] raw = toRawMatrix(distribution.matrix());
        var fresh = zhigalin.predictions.recommender.model.ScoreDistribution.fromNormalizedMatrix(raw);
        List<TopScoreDto> tops = fresh.topScores() == null ? List.of() : fresh.topScores().stream()
                .map(t -> new TopScoreDto(t.home(), t.away(), t.probability()))
                .toList();
        return new ScoreDistributionDto(
                fresh.matrix(),
                fresh.homeWin(),
                fresh.draw(),
                fresh.awayWin(),
                fresh.btts(),
                fresh.over05(),
                fresh.over15(),
                fresh.over25(),
                fresh.over35(),
                tops
        );
    }

    private static double[][] toRawMatrix(List<List<Double>> matrix) {
        double[][] raw = new double[matrix.size()][];
        for (int h = 0; h < matrix.size(); h++) {
            List<Double> row = matrix.get(h);
            raw[h] = new double[row.size()];
            for (int a = 0; a < row.size(); a++) {
                raw[h][a] = row.get(a) != null ? row.get(a) : 0.0;
            }
        }
        return raw;
    }

    public LiveMatchDetailsResponse liveMatchDetails(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        if (match == null) {
            return emptyLiveDetails();
        }
        boolean live = MatchStatus.isLive(match.getStatus())
                       && match.getHomeTeamScore() != null
                       && match.getAwayTeamScore() != null;
        if (!live) {
            return emptyLiveDetails();
        }
        JsonNode summaryRoot = loadEspnSummaryRoot(match);
        TeamFormationItem homeFormation = EspnSummaryParser.loadTeamFormation(summaryRoot, "home", teamCode(match.getHomeTeamId()));
        TeamFormationItem awayFormation = EspnSummaryParser.loadTeamFormation(summaryRoot, "away", teamCode(match.getAwayTeamId()));
        List<LineupPlayerItem> homeLineup = EspnSummaryParser.toLineupItemsFromFormation(homeFormation);
        List<LineupPlayerItem> awayLineup = EspnSummaryParser.toLineupItemsFromFormation(awayFormation);
        if (homeLineup.isEmpty() && awayLineup.isEmpty()) {
            Map<Integer, List<Lineup>> lineups = apiClient.getLineups(match.getPublicId());
            homeLineup = toLineupItems(lineups.get(match.getHomeTeamId()));
            awayLineup = toLineupItems(lineups.get(match.getAwayTeamId()));
        }
        List<MatchEventItem> events = EspnSummaryParser.loadLiveEvents(summaryRoot);
        List<MatchStatItem> matchStats = EspnSummaryParser.loadLiveStats(summaryRoot);
        return new LiveMatchDetailsResponse(
                true,
                homeLineup,
                awayLineup,
                homeFormation,
                awayFormation,
                events,
                matchStats,
                EspnSummaryParser.pitchColorFromFormation(homeFormation, "#ffffff"),
                EspnSummaryParser.pitchColorFromFormation(awayFormation, "#c0c0c0"),
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
        boolean live = MatchStatus.isLive(match.getStatus())
                       && match.getHomeTeamScore() != null
                       && match.getAwayTeamScore() != null;
        if (!live) {
            return emptyLiveDetails();
        }
        JsonNode summaryRoot = loadEspnSummaryRoot(match.getEspnId(), match.getCompetition());
        String homeCode = displayBonusCode(match.getHomeEspnCode(), match.getHomeTeamId(), "HOME");
        String awayCode = displayBonusCode(match.getAwayEspnCode(), match.getAwayTeamId(), "AWAY");
        TeamFormationItem homeFormation = EspnSummaryParser.loadTeamFormation(summaryRoot, "home", homeCode);
        TeamFormationItem awayFormation = EspnSummaryParser.loadTeamFormation(summaryRoot, "away", awayCode);
        List<LineupPlayerItem> homeLineup = EspnSummaryParser.toLineupItemsFromFormation(homeFormation);
        List<LineupPlayerItem> awayLineup = EspnSummaryParser.toLineupItemsFromFormation(awayFormation);
        List<MatchEventItem> events = EspnSummaryParser.loadLiveEvents(summaryRoot);
        List<MatchStatItem> matchStats = EspnSummaryParser.loadLiveStats(summaryRoot);
        return new LiveMatchDetailsResponse(
                true,
                homeLineup,
                awayLineup,
                homeFormation,
                awayFormation,
                events,
                matchStats,
                EspnSummaryParser.pitchColorFromFormation(homeFormation, "#ffffff"),
                EspnSummaryParser.pitchColorFromFormation(awayFormation, "#c0c0c0"),
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
                liveActive = hasLiveEplInWeek(weekId);
                Map<String, Integer> provisional = computeCurrentWeekProvisionalPoints(weekId);
                List<LeaderboardEntry> liveEntries = new ArrayList<>();
                for (User user : DaoUtil.USERS.values()) {
                    String login = user.getLogin();
                    int base = points.getOrDefault(login, 0);
                    int prov = provisional.getOrDefault(login, 0);
                    int delta = prov - base;
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
            liveActive = hasLiveEplInWeek(currentWeekId) || hasLiveCupMatches();
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
        oddsService.refreshAsyncIfStale(matches);
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
        boolean hasLive = items.stream().anyMatch(m -> MatchStatus.isLive(m.status()));
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
                .map(e -> new CrowdScoreBucket(e.getKey(), e.getValue(), MiniAppFormat.percent(e.getValue(), total)))
                .toList();
        return new CrowdMeterResponse(
                matchPublicId,
                total,
                MiniAppFormat.percent(homeWins, total),
                MiniAppFormat.percent(draws, total),
                MiniAppFormat.percent(awayWins, total),
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
            Integer pts = PointsResolver.reviewPoints(match, prediction);
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

        Map<Integer, Match> liveMatchByTeam = LiveTable.liveMatchByTeam(matchService.findOnlineMatches());
        boolean hasLive = !liveMatchByTeam.isEmpty();

        List<StandingItem> items = new ArrayList<>();
        int place = 1;
        for (Standing standing : liveTable) {
            Team team = DaoUtil.TEAMS.get(standing.getTeamId());
            int currentPlace = place++;
            int previousPlace = basePlaceByTeam.getOrDefault(standing.getTeamId(), currentPlace);
            int placeDelta = LiveTable.placeDelta(hasLive, previousPlace, currentPlace);
            Match liveMatch = liveMatchByTeam.get(standing.getTeamId());
            String liveScore = null;
            String liveResult = null;
            if (liveMatch != null) {
                liveScore = LiveTable.scoreForTeam(liveMatch, standing.getTeamId());
                liveResult = LiveTable.resultForTeam(liveMatch, standing.getTeamId());
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

    public TeamMatchesResponse teamMatches(String telegramId, String teamCode) {
        TeamProfileResponse profile = teamProfile(telegramId, teamCode);
        return new TeamMatchesResponse(
                profile.teamCode(),
                profile.teamName(),
                profile.lastMatches(),
                profile.upcomingMatches()
        );
    }

    public ProfileTeamPredictionsResponse profileTeamPredictions(String telegramId, String teamCode) {
        return userPredictionStatsService.teamPredictions(requireUser(telegramId), requireTeam(teamCode));
    }

    private static Team requireTeam(String teamCode) {
        String normalizedCode = teamCode.toUpperCase();
        return DaoUtil.TEAMS.values().stream()
                .filter(t -> normalizedCode.equals(t.getCode()))
                .findFirst()
                .orElseThrow(() -> new MiniAppException(404, "Команда не найдена"));
    }

    public TeamProfileResponse teamProfile(String telegramId, String teamCode) {
        requireUser(telegramId);
        Team team = requireTeam(teamCode);

        List<TeamMatchItem> lastMatches = matchService.findLastFinishedByTeamId(team.getPublicId(), 5).stream()
                .map(this::toTeamMatchItem)
                .toList();
        List<TeamMatchItem> upcomingMatches = matchService.findNextByTeamId(team.getPublicId(), 5).stream()
                .map(this::toTeamMatchItem)
                .toList();
        List<FormItem> form = buildRecentForm(team.getPublicId(), 5);

        Integer place = null;
        Integer played = null;
        Integer won = null;
        Integer drawn = null;
        Integer lost = null;
        Integer goalsFor = null;
        Integer goalsAgainst = null;
        Integer points = null;
        int idx = 1;
        for (Standing standing : matchService.getStandings()) {
            if (standing.getTeamId() == team.getPublicId()) {
                place = idx;
                played = standing.getGames();
                won = standing.getWon();
                drawn = standing.getDrawn();
                lost = standing.getLost();
                goalsFor = standing.getGoalsFor();
                goalsAgainst = standing.getGoalsAgainst();
                points = standing.getPoints();
                break;
            }
            idx++;
        }

        List<TeamPlayerLeaderItem> leaders = espnTeamRosterService.leadersForInternalCode(team.getCode(), 8).stream()
                .map(p -> new TeamPlayerLeaderItem(
                        p.name(),
                        p.position(),
                        p.appearances(),
                        p.goals(),
                        p.assists(),
                        p.saves()
                ))
                .toList();

        TeamSeasonStats seasonStats = buildTeamSeasonStats(team.getCode());

        return new TeamProfileResponse(
                team.getCode(),
                team.getName(),
                teamLogoPath(team.getPublicId()),
                place,
                played,
                won,
                drawn,
                lost,
                goalsFor,
                goalsAgainst,
                points,
                form,
                seasonStats,
                leaders,
                lastMatches,
                upcomingMatches
        );
    }

    private TeamSeasonStats buildTeamSeasonStats(String teamCode) {
        Optional<FootyStatsTeamSnapshot> fsOpt = footyStatsStatsDao.findTeamStats(DaoUtil.currentWeekId, teamCode);
        if (fsOpt.isEmpty()) {
            fsOpt = footyStatsStatsDao.findLatestTeamStats(teamCode);
        }
        FootyStatsTeamSnapshot fs = fsOpt.orElse(null);
        FootyStatsExtendedMetrics ext = fs != null ? fs.extendedOrEmpty() : FootyStatsExtendedMetrics.empty();
        TeamSeasonExtras espn = espnTeamSeasonStatsService.forInternalCode(teamCode);

        Double xg = MiniAppFormat.firstNonNull(espn.avgXg(), fs != null ? fs.xgOverall() : null);
        Double xga = MiniAppFormat.firstNonNull(espn.avgXga(), fs != null ? fs.xgaOverall() : null);
        Double scoredPm = MiniAppFormat.firstNonNull(ext.seasonScoredPerMatch(), fs != null ? fs.scoredOverall() : null);
        Double concededPm = MiniAppFormat.firstNonNull(ext.seasonConcededPerMatch(), fs != null ? fs.concededOverall() : null);
        Integer cleanSheets = espn.cleanSheets();
        Double csPct = ext.seasonCsOverall();

        TeamSeasonStats stats = new TeamSeasonStats(
                MiniAppFormat.round1(xg),
                MiniAppFormat.round1(xga),
                MiniAppFormat.round1(scoredPm),
                MiniAppFormat.round1(concededPm),
                MiniAppFormat.roundPct(ext.seasonBttsOverall()),
                MiniAppFormat.roundPct(csPct),
                MiniAppFormat.roundPct(ext.over25Overall()),
                MiniAppFormat.round1(ext.homePpg()),
                MiniAppFormat.round1(ext.awayPpg()),
                MiniAppFormat.round1(espn.possessionPct()),
                MiniAppFormat.round1(espn.shotsPerMatch()),
                MiniAppFormat.round1(espn.shotsOnTargetPerMatch()),
                cleanSheets
        );
        if (stats.isEmpty()) {
            return null;
        }
        return stats;
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
            if (!MatchStatus.canPredictBonus(bonus)) {
                return new ActionResponse(false, "Время для прогноза истекло (принимаются до начала матча + 5 мин).");
            }
            predictionService.saveBonus(telegramId, bonus.getPublicId(), request.homeScore(), request.awayScore());
            return new ActionResponse(true, "Прогноз на бонус-матч сохранён", request.homeScore(), request.awayScore());
        }
        String home = request.homeCode().toUpperCase();
        String away = request.awayCode().toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!MatchStatus.canPredict(match)) {
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
        if (!MatchStatus.canPredict(match)) {
            return new ActionResponse(false, "Время для удаления прогноза истекло.");
        }
        predictionService.deleteByUserTelegramIdAndTeams(telegramId, home, away);
        return new ActionResponse(true, "Прогноз удалён");
    }

    public ActionResponse deleteBonusPrediction(String telegramId, int bonusMatchId) {
        requireUser(telegramId);
        BonusMatch bonus = bonusMatchDao.findByPublicId(bonusMatchId);
        if (!MatchStatus.canPredictBonus(bonus)) {
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
        if (until != null && MatchStatus.canPredict(match)) {
            predictSecondsLeft = java.time.Duration.between(now, until).getSeconds();
            if (predictSecondsLeft < 0) {
                predictSecondsLeft = 0L;
            }
        }
        Long kickoffSecondsLeft = null;
        if (match.getLocalDateTime() != null && MatchStatus.isNotStarted(match.getStatus())) {
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
                MatchStatus.canPredict(match),
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
        if (until != null && MatchStatus.canPredictBonus(match)) {
            predictSecondsLeft = java.time.Duration.between(now, until).getSeconds();
            if (predictSecondsLeft < 0) {
                predictSecondsLeft = 0L;
            }
        }
        Long kickoffSecondsLeft = null;
        if (match.getLocalDateTime() != null && MatchStatus.isNotStarted(match.getStatus())) {
            kickoffSecondsLeft = java.time.Duration.between(now, match.getLocalDateTime()).getSeconds();
        }
        String homeCode = displayBonusCode(match.getHomeEspnCode(), match.getHomeTeamId(), "?");
        String awayCode = displayBonusCode(match.getAwayEspnCode(), match.getAwayTeamId(), "?");
        String homeLogo = match.getHomeTeamId() != null
                ? teamLogoPath(match.getHomeTeamId())
                : teamLogoCacheService.pathForRemote(match.getHomeLogoUrl());
        String awayLogo = match.getAwayTeamId() != null
                ? teamLogoPath(match.getAwayTeamId())
                : teamLogoCacheService.pathForRemote(match.getAwayLogoUrl());
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
                MatchStatus.canPredictBonus(match),
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

    private boolean hasLiveEplInWeek(int weekId) {
        return matchService.findAllByWeekId(weekId).stream().anyMatch(MatchStatus::isLiveLike);
    }

    private boolean hasLiveCupMatches() {
        return !bonusMatchDao.findOnline().isEmpty();
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
                    MiniAppFormat.competitionLabel(e.getKey()),
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
            Integer pts = PointsResolver.cupReviewPoints(match, prediction);
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
        return new CupReviewResponse(competition, MiniAppFormat.competitionLabel(competition), total, items);
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
        for (Match match : weekMatches) {
            Map<Integer, Prediction> byUser = predictionsByMatch.getOrDefault(match.getPublicId(), Map.of());
            boolean finished = MatchStatus.isFinished(match.getStatus());
            boolean liveLike = MatchStatus.isLiveLike(match);
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
        return FlooredPointsService.sumProvisional(ordered);
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

    private String teamLogoPath(int teamId) {
        return teamLogoCacheService.pathForTeam(teamId);
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

}
