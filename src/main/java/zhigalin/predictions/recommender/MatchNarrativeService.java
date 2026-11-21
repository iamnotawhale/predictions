package zhigalin.predictions.recommender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.recommender.model.MatchRecommendationSnapshot;
import zhigalin.predictions.recommender.model.TeamStatsSnapshot;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class MatchNarrativeService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");
    private static final long GROQ_PAUSE_MS = 700L;

    private final SoccerStatsMatchPreviewScraper previewScraper;
    private final GroqNarrativeClient groqNarrativeClient;
    private final BettingRecommendationService bettingRecommendationService;
    private final RecommenderStatsDao statsDao;
    private final MatchService matchService;
    private final WeekService weekService;
    private final Set<Integer> scheduledWeeks = ConcurrentHashMap.newKeySet();

    public MatchNarrativeService(
            SoccerStatsMatchPreviewScraper previewScraper,
            GroqNarrativeClient groqNarrativeClient,
            BettingRecommendationService bettingRecommendationService,
            RecommenderStatsDao statsDao,
            MatchService matchService,
            WeekService weekService
    ) {
        this.previewScraper = previewScraper;
        this.groqNarrativeClient = groqNarrativeClient;
        this.bettingRecommendationService = bettingRecommendationService;
        this.statsDao = statsDao;
        this.matchService = matchService;
        this.weekService = weekService;
    }

    public boolean tryMarkScheduled(int weekId) {
        return scheduledWeeks.add(weekId);
    }

    public boolean isConfigured() {
        return groqNarrativeClient.isConfigured();
    }

    @Async
    public void generateForWeekAsync(int weekId) {
        try {
            generateForWeek(weekId);
        } catch (Exception e) {
            log.warn("Groq narrative job failed for week {}: {}", weekId, e.getMessage());
        } finally {
            scheduledWeeks.remove(weekId);
        }
    }

    void generateForWeek(int weekId) {
        int round = resolveRound(weekId);
        List<SoccerStatsMatchPreviewScraper.FixturePair> fixtures = previewScraper.fetchMatchlistRound(round);
        Map<String, SoccerStatsMatchPreviewScraper.FixturePair> fixtureByKey = fixtures.stream()
                .collect(Collectors.toMap(
                        f -> f.homeCode() + "-" + f.awayCode(),
                        f -> f,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, SoccerStatsMatchPreviewScraper.PreviewLine> previews = previewScraper.fetchHomeAwayPreviews();
        Map<String, SoccerStatsMatchPreviewScraper.PreviewLine> overall = previewScraper.fetchOverallPreviews();

        Map<String, TeamStatsSnapshot> teamStats = statsDao.findTeamStats(weekId).stream()
                .collect(Collectors.toMap(TeamStatsSnapshot::teamCode, t -> t, (a, b) -> a));

        List<Match> matches = matchService.findAllByWeekId(weekId);
        Map<Integer, MatchRecommendationSnapshot> tips = statsDao.findRecommendationsForWeek(weekId).stream()
                .collect(Collectors.toMap(
                        MatchRecommendationSnapshot::matchId,
                        r -> r,
                        (a, b) -> a
                ));

        int written = 0;
        int candidates = 0;
        for (Match match : matches) {
            if (!isUpcoming(match)) {
                continue;
            }
            MatchRecommendationSnapshot tip = tips.get(match.getId());
            if (tip == null) {
                continue;
            }
            String homeCode = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
            String awayCode = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
            String key = homeCode + "-" + awayCode;
            SoccerStatsMatchPreviewScraper.PreviewLine homeAway = previews.get(key);
            SoccerStatsMatchPreviewScraper.PreviewLine overallLine = overall.get(key);
            if (homeAway == null && overallLine == null
                    && teamStats.get(homeCode) == null && teamStats.get(awayCode) == null) {
                continue;
            }
            candidates++;
            String prompt = buildCompactPrompt(
                    homeCode,
                    awayCode,
                    tip,
                    fixtureByKey.get(key),
                    homeAway,
                    overallLine,
                    teamStats.get(homeCode),
                    teamStats.get(awayCode)
            );
            if (prompt == null || prompt.isBlank()) {
                continue;
            }
            GroqNarrativeClient.Result result = groqNarrativeClient.generate(prompt);
            if (result.rateLimited()) {
                break;
            }
            if (result.text() == null || result.text().isBlank()) {
                pauseGroq();
                continue;
            }
            boolean ok = bettingRecommendationService.applyGroqAnalysisIfTipUnchanged(
                    match.getId(),
                    weekId,
                    tip.recommendedHome(),
                    tip.recommendedAway(),
                    result.text()
            );
            if (ok) {
                written++;
            }
            pauseGroq();
        }
        log.info(
                "Groq narrative complete for week {} round {}: {} written / {} upcoming candidates / {} matches",
                weekId,
                round,
                written,
                candidates,
                matches.size()
        );
    }

    private static boolean isUpcoming(Match match) {
        String status = match.getStatus();
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase();
        return "ns".equals(s) || "pst".equals(s) || "tbd".equals(s);
    }

    private int resolveRound(int weekId) {
        Week week = weekService.findById(weekId);
        if (week != null && week.getName() != null) {
            Matcher m = DIGITS.matcher(week.getName());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return weekId;
    }

    String buildCompactPrompt(
            String homeCode,
            String awayCode,
            MatchRecommendationSnapshot tip,
            SoccerStatsMatchPreviewScraper.FixturePair fixture,
            SoccerStatsMatchPreviewScraper.PreviewLine homeAwayPreview,
            SoccerStatsMatchPreviewScraper.PreviewLine overallPreview,
            TeamStatsSnapshot homeTeam,
            TeamStatsSnapshot awayTeam
    ) {
        return MatchNarrativeCompactEncoder.encode(
                homeCode,
                awayCode,
                tip,
                fixture,
                homeAwayPreview,
                overallPreview,
                homeTeam,
                awayTeam
        );
    }

    private static void pauseGroq() {
        try {
            Thread.sleep(GROQ_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
