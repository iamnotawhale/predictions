package zhigalin.predictions.miniapp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ChartSeries;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardEntry;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MiniAppService {

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;

    public MiniAppService(UserService userService, MatchService matchService, PredictionService predictionService) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
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
        return new ProfileResponse(user.getLogin(), DaoUtil.currentWeekId);
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
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByWeekId(weekId);
        Set<Integer> withPrediction = new HashSet<>(
                matchService.predictableMatchesByUserTelegramIdAndWeekId(telegramId, weekId)
        );
        return matches.stream()
                .map(match -> toMatchItem(match, telegramId, withPrediction.contains(match.getPublicId())))
                .toList();
    }

    public List<MatchItem> myPredictions(String telegramId, int weekId) {
        requireUser(telegramId);
        return predictionService.getAllWeeklyPredictionsByUserTelegramId(weekId, telegramId).stream()
                .map(mp -> toMatchItem(mp.match(), telegramId, true, mp.prediction()))
                .toList();
    }

    public MatchItem match(String telegramId, String homeCode, String awayCode) {
        requireUser(telegramId);
        Match match = matchService.findByTeamCodes(homeCode.toUpperCase(), awayCode.toUpperCase());
        Prediction prediction = predictionService.getByUserTelegramIdAndTeams(
                telegramId, homeCode.toUpperCase(), awayCode.toUpperCase()
        );
        boolean hasPrediction = prediction != null;
        return toMatchItem(match, telegramId, hasPrediction, prediction);
    }

    public LeaderboardResponse leaderboard(String telegramId, Integer weekId) {
        requireUser(telegramId);
        Map<String, Integer> points;
        String title;
        if (weekId != null) {
            points = predictionService.getWeeklyUsersPoints(weekId);
            title = "Очки за " + weekId + " тур";
        } else {
            points = predictionService.getAllPointsByUsers();
            title = "Общий зачёт";
        }
        List<LeaderboardEntry> entries = points.entrySet().stream()
                .map(e -> new LeaderboardEntry(e.getKey(), e.getValue()))
                .toList();
        return new LeaderboardResponse(entries, weekId, title);
    }

    public TodayMatchesResponse todayMatches(String telegramId) {
        requireUser(telegramId);
        List<Match> matches = matchService.findAllByTodayDate();
        Set<Integer> withPrediction = new HashSet<>(
                matchService.predictableTodayMatchesByUserTelegramIdAndWeekId(telegramId)
        );
        List<MatchItem> items = matches.stream()
                .map(match -> toMatchItem(match, telegramId, withPrediction.contains(match.getPublicId())))
                .toList();
        return new TodayMatchesResponse(items);
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
                    .map(w -> entry.getValue().getOrDefault(w, null))
                    .map(v -> v == null ? -1 : v)
                    .toList();
            series.add(new ChartSeries(login, label, points));
        }
        return new PointsChartResponse(weeks, series);
    }

    public List<StandingItem> standings(String telegramId) {
        requireUser(telegramId);
        List<Standing> standings = matchService.getStandings();
        List<StandingItem> items = new ArrayList<>();
        int place = 1;
        for (Standing standing : standings) {
            Team team = DaoUtil.TEAMS.get(standing.getTeamId());
            items.add(new StandingItem(
                    place++,
                    team.getCode(),
                    team.getName(),
                    standing.getGames(),
                    standing.getWon(),
                    standing.getDrawn(),
                    standing.getLost(),
                    standing.getGoalsFor(),
                    standing.getGoalsAgainst(),
                    standing.getPoints()
            ));
        }
        return items;
    }

    public ActionResponse savePrediction(String telegramId, PredictRequest request) {
        requireUser(telegramId);
        String home = request.homeCode().toUpperCase();
        String away = request.awayCode().toUpperCase();
        Match match = matchService.findByTeamCodes(home, away);
        if (!canPredict(match)) {
            return new ActionResponse(false, "Время для прогноза истекло. Матч уже начался.");
        }
        if (request.homeScore() < 0 || request.homeScore() > 5 || request.awayScore() < 0 || request.awayScore() > 5) {
            return new ActionResponse(false, "Счёт должен быть от 0 до 5.");
        }
        boolean exists = predictionService.isExist(telegramId, match.getPublicId());
        predictionService.save(telegramId, home, away, request.homeScore(), request.awayScore());
        return new ActionResponse(true, exists ? "Прогноз обновлён" : "Прогноз сохранён");
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

    private MatchItem toMatchItem(Match match, String telegramId, boolean hasPrediction) {
        Prediction prediction = hasPrediction
                ? predictionService.getByUserTelegramIdAndTeams(
                        telegramId,
                        teamCode(match.getHomeTeamId()),
                        teamCode(match.getAwayTeamId())
                )
                : null;
        return toMatchItem(match, telegramId, hasPrediction, prediction);
    }

    private MatchItem toMatchItem(Match match, String telegramId, boolean hasPrediction, Prediction prediction) {
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        return new MatchItem(
                match.getPublicId(),
                match.getWeekId(),
                home.getCode(),
                home.getName(),
                away.getCode(),
                away.getName(),
                match.getStatus(),
                match.getHomeTeamScore(),
                match.getAwayTeamScore(),
                match.getLocalDateTime() != null ? match.getLocalDateTime().format(KICKOFF) : "",
                canPredict(match),
                hasPrediction,
                prediction != null ? prediction.getHomeTeamScore() : null,
                prediction != null ? prediction.getAwayTeamScore() : null,
                prediction != null ? prediction.getPoints() : null
        );
    }

    private static boolean canPredict(Match match) {
        if (match == null || match.getLocalDateTime() == null) {
            return false;
        }
        boolean notStarted = Objects.equals(match.getStatus(), "ns") || Objects.equals(match.getStatus(), "pst");
        return notStarted && LocalDateTime.now().isBefore(match.getLocalDateTime().plusMinutes(5));
    }

    private static String teamCode(int teamId) {
        return DaoUtil.TEAMS.get(teamId).getCode();
    }
}
