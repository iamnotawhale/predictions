package zhigalin.predictions.service.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import zhigalin.predictions.service.notification.ImageRenderer;
import zhigalin.predictions.service.notification.Result;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.TeamCodeMapper;

@Service
public class BonusMatchSyncService {

    private static final Logger log = LoggerFactory.getLogger("server");

    @Value("${bot.chatId}")
    private String defaultChatId;

    private final EspnScoreboardClient espnScoreboardClient;
    private final BonusMatchDao bonusMatchDao;
    private final PredictionService predictionService;
    private final ApiClient apiClient;
    private final ImageRenderer imageRenderer;

    public BonusMatchSyncService(
            EspnScoreboardClient espnScoreboardClient,
            BonusMatchDao bonusMatchDao,
            PredictionService predictionService,
            ApiClient apiClient,
            ImageRenderer imageRenderer
    ) {
        this.espnScoreboardClient = espnScoreboardClient;
        this.bonusMatchDao = bonusMatchDao;
        this.predictionService = predictionService;
        this.apiClient = apiClient;
        this.imageRenderer = imageRenderer;
    }

    public void syncTodayCups() {
        Set<String> eplCodes = DaoUtil.TEAMS.values().stream()
                .map(Team::getCode)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        for (String league : EspnScoreboardClient.CUP_LEAGUES) {
            try {
                Scoreboard board = espnScoreboardClient.fetchScoreboard(league);
                if (board == null || board.getEvents() == null) {
                    continue;
                }
                for (Event event : board.getEvents()) {
                    ingest(league, event, eplCodes);
                }
            } catch (Exception e) {
                log.warn("Cup sync failed for {}: {}", league, e.getMessage());
            }
        }
    }

    private void ingest(String competition, Event event, Set<String> eplCodes) {
        if (event.getCompetitions() == null || event.getCompetitions().isEmpty()) {
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
        boolean involvesEpl = (homeCode != null && eplCodes.contains(homeCode.toUpperCase()))
                || (awayCode != null && eplCodes.contains(awayCode.toUpperCase()));
        if (!involvesEpl) {
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
        LocalDateTime kickoff = parseKickoff(event.getDate());

        Team homeTeam = resolveTeam(homeCode);
        Team awayTeam = resolveTeam(awayCode);

        Integer prevHome = existing != null ? existing.getHomeTeamScore() : null;
        Integer prevAway = existing != null ? existing.getAwayTeamScore() : null;
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

        int prevTotal = nz(prevHome) + nz(prevAway);
        int nextTotal = nz(homeScore) + nz(awayScore);
        if (nextTotal != prevTotal && (isLive(status) || "ft".equals(status))) {
            sendLive(match, prevHome, prevAway);
        }
        if (becameFt) {
            predictionService.updateByBonusMatch(match);
            sendFullTime(match);
        }
    }

    private void sendLive(BonusMatch match, Integer prevHome, Integer prevAway) {
        String home = match.getHomeEspnCode() != null ? match.getHomeEspnCode() : "?";
        String away = match.getAwayEspnCode() != null ? match.getAwayEspnCode() : "?";
        String text = home + " " + nz(match.getHomeTeamScore()) + ":" + nz(match.getAwayTeamScore())
                      + " " + away + "\n" + competitionLabel(match.getCompetition());
        if (prevHome != null && prevAway != null) {
            text += "\n(было " + prevHome + ":" + prevAway + ")";
        }
        Integer existing = match.getLiveScoreMessageId();
        boolean delivered = false;
        if (existing != null) {
            delivered = apiClient.editMessageText(defaultChatId, existing, text, null);
        }
        if (!delivered) {
            Integer sent = apiClient.sendMessageAndGetId(defaultChatId, text, null);
            if (sent != null) {
                bonusMatchDao.updateLiveScoreMessageId(match.getPublicId(), sent);
            }
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

        String path = imageRenderer.createCupResultImage(
                match.getCompetition(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getHomeEspnCode(),
                match.getAwayEspnCode(),
                match.getHomeLogoUrl(),
                match.getAwayLogoUrl(),
                center,
                results
        );

        String caption = "Бонус-матч окончен: " + home + " " + center + " " + away
                         + " (" + competitionLabel(match.getCompetition()) + ")";
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

    private static boolean isLive(String status) {
        return status != null && !Set.of("ns", "ft", "pst", "aet", "pen").contains(status.toLowerCase());
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
            return TeamCodeMapper.toInternalCode(c.getTeam().getAbbreviation());
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
