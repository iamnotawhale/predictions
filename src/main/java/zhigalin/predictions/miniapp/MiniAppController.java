package zhigalin.predictions.miniapp;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ClientLogRequest;
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
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewResponse;

@RestController
@RequestMapping("/api/miniapp")
public class MiniAppController {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final TelegramWebAppAuthService authService;
    private final MiniAppService miniAppService;
    private final MiniAppProperties miniAppProperties;

    public MiniAppController(
            TelegramWebAppAuthService authService,
            MiniAppService miniAppService,
            MiniAppProperties miniAppProperties
    ) {
        this.authService = authService;
        this.miniAppService = miniAppService;
        this.miniAppProperties = miniAppProperties;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.profile(requireTelegramId(initData));
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
            @RequestParam String homeCode,
            @RequestParam String awayCode
    ) {
        return miniAppService.deletePrediction(requireTelegramId(initData), homeCode, awayCode);
    }

    @ExceptionHandler(MiniAppException.class)
    public ResponseEntity<ActionResponse> handleMiniApp(MiniAppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ActionResponse(false, ex.getMessage()));
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
