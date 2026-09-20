package zhigalin.predictions.miniapp;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.BettingRecommenderRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ClientLogRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CupCompetitionItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CupReviewResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.CrowdMeterResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.H2hItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LiveMatchDetailsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchInsightsResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TeamProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewResponse;
import zhigalin.predictions.service.api.TeamLogoCacheService;

@RestController
@RequestMapping("/api/miniapp")
public class MiniAppController {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final TelegramWebAppAuthService authService;
    private final MiniAppService miniAppService;
    private final MiniAppProperties miniAppProperties;
    private final TeamLogoCacheService teamLogoCacheService;

    public MiniAppController(
            TelegramWebAppAuthService authService,
            MiniAppService miniAppService,
            MiniAppProperties miniAppProperties,
            TeamLogoCacheService teamLogoCacheService
    ) {
        this.authService = authService;
        this.miniAppService = miniAppService;
        this.miniAppProperties = miniAppProperties;
        this.teamLogoCacheService = teamLogoCacheService;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.profile(requireTelegramId(initData));
    }

    @PostMapping(
            value = "/profile/betting-recommender",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ActionResponse setBettingRecommender(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestBody BettingRecommenderRequest request
    ) {
        return miniAppService.setBettingRecommender(requireTelegramId(initData), request.enabled());
    }

    @PostMapping(value = "/admin/betting-recommender/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ActionResponse refreshBettingRecommendations(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(required = false) Integer weekId
    ) {
        return miniAppService.refreshBettingRecommendations(requireTelegramId(initData), weekId);
    }

    @GetMapping("/weeks")
    public List<WeekItem> weeks(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.weeks(requireTelegramId(initData));
    }

    @GetMapping("/weeks/{weekId}/matches")
    public List<MatchItem> weekMatches(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable int weekId
    ) {
        return miniAppService.weekMatches(requireTelegramId(initData), weekId);
    }

    @GetMapping("/weeks/{weekId}/my-predictions")
    public List<MatchItem> myPredictions(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable int weekId
    ) {
        return miniAppService.myPredictions(requireTelegramId(initData), weekId);
    }

    @GetMapping("/match/{homeCode}/{awayCode}")
    public MatchItem match(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String homeCode,
            @PathVariable String awayCode
    ) {
        return miniAppService.match(requireTelegramId(initData), homeCode, awayCode);
    }

    @GetMapping("/match/{homeCode}/{awayCode}/insights")
    public MatchInsightsResponse matchInsights(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String homeCode,
            @PathVariable String awayCode
    ) {
        return miniAppService.matchInsights(requireTelegramId(initData), homeCode, awayCode);
    }

    @GetMapping("/match/{homeCode}/{awayCode}/live-details")
    public LiveMatchDetailsResponse liveMatchDetails(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String homeCode,
            @PathVariable String awayCode
    ) {
        return miniAppService.liveMatchDetails(requireTelegramId(initData), homeCode, awayCode);
    }

    @GetMapping("/leaderboard")
    public LeaderboardResponse leaderboard(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(required = false) Integer weekId
    ) {
        return miniAppService.leaderboard(requireTelegramId(initData), weekId);
    }

    @GetMapping("/standings")
    public List<StandingItem> standings(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.standings(requireTelegramId(initData));
    }

    @GetMapping("/team/{teamCode}")
    public TeamProfileResponse teamProfile(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String teamCode
    ) {
        return miniAppService.teamProfile(requireTelegramId(initData), teamCode);
    }

    @GetMapping("/team/{teamCode}/matches")
    public TeamMatchesResponse teamMatches(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String teamCode
    ) {
        return miniAppService.teamMatches(requireTelegramId(initData), teamCode);
    }

    @GetMapping("/h2h/{homeCode}/{awayCode}")
    public List<H2hItem> h2h(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String homeCode,
            @PathVariable String awayCode
    ) {
        return miniAppService.h2h(requireTelegramId(initData), homeCode, awayCode);
    }

    @GetMapping("/today")
    public TodayMatchesResponse today(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.todayMatches(requireTelegramId(initData));
    }

    @GetMapping("/cups")
    public List<CupCompetitionItem> cups(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.cupCompetitions(requireTelegramId(initData));
    }

    @GetMapping("/cups/{competition}/matches")
    public List<MatchItem> cupMatches(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String competition
    ) {
        return miniAppService.cupMatches(requireTelegramId(initData), competition);
    }

    @GetMapping("/cups/{competition}/review")
    public CupReviewResponse cupReview(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable String competition
    ) {
        return miniAppService.cupReview(requireTelegramId(initData), competition);
    }

    @GetMapping("/cups/match/{publicId}/live-details")
    public LiveMatchDetailsResponse cupLiveDetails(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable int publicId
    ) {
        return miniAppService.liveCupMatchDetails(requireTelegramId(initData), publicId);
    }

    @GetMapping("/match/{matchId}/crowd")
    public CrowdMeterResponse crowd(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable int matchId
    ) {
        return miniAppService.crowdMeter(requireTelegramId(initData), matchId);
    }

    @GetMapping("/weeks/{weekId}/review")
    public WeekReviewResponse weekReview(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @PathVariable int weekId
    ) {
        return miniAppService.weekReview(requireTelegramId(initData), weekId);
    }

    @GetMapping("/chart")
    public PointsChartResponse chart(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.pointsChart(requireTelegramId(initData));
    }

    @PostMapping(
            value = "/predictions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ActionResponse savePrediction(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestBody PredictRequest request
    ) {
        return miniAppService.savePrediction(requireTelegramId(initData), request);
    }

    @PostMapping(
            value = "/client-log",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ActionResponse clientLog(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestBody ClientLogRequest request
    ) {
        String telegramId = resolveTelegramIdForLog(initData);
        String level = safe(request.level(), 12).toUpperCase();
        String event = safe(request.event(), 80);
        String details = safe(request.details(), 400);
        String href = safe(request.href(), 300);
        String ua = safe(request.userAgent(), 200);
        String message = "MiniApp client log: tgId={}, event={}, href={}, details={}, ua={}";

        if ("ERROR".equals(level)) {
            log.error(message, telegramId, event, href, details, ua);
        } else if ("WARN".equals(level)) {
            log.warn(message, telegramId, event, href, details, ua);
        } else {
            log.info(message, telegramId, event, href, details, ua);
        }
        return new ActionResponse(true, "ok");
    }

    @DeleteMapping("/predictions")
    public ActionResponse deletePrediction(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(required = false) String homeCode,
            @RequestParam(required = false) String awayCode,
            @RequestParam(required = false) Integer bonusMatchId
    ) {
        String telegramId = requireTelegramId(initData);
        if (bonusMatchId != null) {
            return miniAppService.deleteBonusPrediction(telegramId, bonusMatchId);
        }
        return miniAppService.deletePrediction(telegramId, homeCode, awayCode);
    }

    /** Public (no Telegram auth) — used as {@code <img src>}; long-lived browser cache. */
    @GetMapping(value = "/logos/{teamId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> teamLogo(@PathVariable int teamId) {
        byte[] bytes = teamLogoCacheService.bytesForTeam(teamId);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }

    @GetMapping(value = "/logos/r/{hash}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> remoteLogo(@PathVariable String hash) {
        byte[] bytes = teamLogoCacheService.bytesForRemoteHash(hash);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }

    @ExceptionHandler(MiniAppException.class)
    public ResponseEntity<ActionResponse> handleMiniApp(MiniAppException ex) {
        log.warn("MiniApp handled error: status={}, message={}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(new ActionResponse(false, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ActionResponse> handleUnknown(Exception ex) {
        log.error("MiniApp unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ActionResponse(false, "Внутренняя ошибка сервера."));
    }

    private String requireTelegramId(String initData) {
        if (miniAppProperties.isDevMode()) {
            String devId = miniAppProperties.getDevTelegramId();
            if (devId != null && !devId.isBlank()) {
                return devId;
            }
        }
        if (initData != null && !initData.isBlank()) {
            String telegramId = authService.parseUserId(initData);
            if (telegramId != null) {
                return telegramId;
            }
        }
        throw new MiniAppException(HttpStatus.UNAUTHORIZED.value(), "Недействительные данные Telegram.");
    }

    private String resolveTelegramIdForLog(String initData) {
        if (miniAppProperties.isDevMode()) {
            String devId = miniAppProperties.getDevTelegramId();
            if (devId != null && !devId.isBlank()) {
                return devId;
            }
        }
        if (initData == null || initData.isBlank()) {
            return "n/a";
        }
        String parsed = authService.parseUserId(initData);
        return parsed == null || parsed.isBlank() ? "unknown" : parsed;
    }

    private static String safe(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
