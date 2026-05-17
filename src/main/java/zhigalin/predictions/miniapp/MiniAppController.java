package zhigalin.predictions.miniapp;

import java.util.List;
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
import zhigalin.predictions.miniapp.dto.MiniAppDtos.LeaderboardResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PointsChartResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.PredictRequest;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ProfileResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.StandingItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.TodayMatchesResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekItem;

@RestController
@RequestMapping("/api/miniapp")
public class MiniAppController {

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

    @GetMapping("/today")
    public TodayMatchesResponse today(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData
    ) {
        return miniAppService.todayMatches(requireTelegramId(initData));
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
        if (initData != null && !initData.isBlank()) {
            String telegramId = authService.parseUserId(initData);
            if (telegramId != null) {
                return telegramId;
            }
        }
        if (miniAppProperties.isDevMode()) {
            String devId = miniAppProperties.getDevTelegramId();
            if (devId != null && !devId.isBlank()) {
                return devId;
            }
        }
        throw new MiniAppException(HttpStatus.UNAUTHORIZED.value(), "Недействительные данные Telegram.");
    }
}
