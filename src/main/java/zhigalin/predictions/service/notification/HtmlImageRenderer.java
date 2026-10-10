package zhigalin.predictions.service.notification;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.odds.OddsService.Odd;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.EspnTeamLogos;
import zhigalin.predictions.util.TeamCodeMapper;

import static zhigalin.predictions.service.notification.CardRenderModels.*;

/**
 * HTML → PNG notification cards (miniapp / architect visual language) via headless Chromium.
 */
@Service
public class HtmlImageRenderer {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1080;
    private static final long RENDER_TIMEOUT_MS = 45_000L;

    private final PanicSender panicSender;
    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final OddsService oddsService;
    private final Semaphore renderGate = new Semaphore(1);

    @Value("${notification.cards.chromeExecutable:}")
    private String chromeExecutable;

    private volatile Playwright playwright;
    private volatile Browser browser;

    public HtmlImageRenderer(
            PanicSender panicSender,
            MatchService matchService,
            HeadToHeadService headToHeadService,
            OddsService oddsService
    ) {
        this.panicSender = panicSender;
        this.matchService = matchService;
        this.headToHeadService = headToHeadService;
        this.oddsService = oddsService;
    }

    @PreDestroy
    void shutdown() {
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
        }
    }

    public String createTodayMatchesImage(List<MatchRecord> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return renderHtml(CardHtml.buildTodayHtml(toTodayCard(list)), "epl");
    }

    public String createReminderImage(int matchPublicId, int homeTeamId, int awayTeamId, String kickoff) {
        return createReminderImage(matchPublicId, homeTeamId, awayTeamId, kickoff, false);
    }

    public String createReminderImage(
            int matchPublicId, int homeTeamId, int awayTeamId, String kickoff, boolean weekBonus
    ) {
        ReminderCard card = buildReminderCard(matchPublicId, homeTeamId, awayTeamId, kickoff, weekBonus);
        return renderHtml(CardHtml.buildReminderHtml(card), card.accent());
    }

    public String createResultImage(
            String accent,
            String badge,
            Integer homeTeamId,
            Integer awayTeamId,
            String homeCode,
            String awayCode,
            String homeLogoUrl,
            String awayLogoUrl,
            String score,
            String aiKickoffScore,
            List<Result> results
    ) {
        ResultCard card = new ResultCard(
                accent != null ? accent : "epl",
                badge != null ? badge : "FULL TIME",
                nzCode(homeCode, homeTeamId),
                nzCode(awayCode, awayTeamId),
                CardLogos.resolveLogoSrc(homeTeamId, homeLogoUrl),
                CardLogos.resolveLogoSrc(awayTeamId, awayLogoUrl),
                score != null ? score : "-:-",
                aiKickoffScore,
                toResultLines(results)
        );
        return renderHtml(CardHtml.buildResultHtml(card), card.accent());
    }

    public String createEplResultImage(
            int matchPublicId,
            int homeTeamId,
            int awayTeamId,
            String score,
            List<Result> results,
            String aiKickoffScore
    ) {
        Team home = DaoUtil.TEAMS.get(homeTeamId);
        Team away = DaoUtil.TEAMS.get(awayTeamId);
        return createResultImage(
                "epl",
                "EPL · FULL TIME",
                homeTeamId,
                awayTeamId,
                home != null ? home.getCode() : "?",
                away != null ? away.getCode() : "?",
                null,
                null,
                score,
                aiKickoffScore,
                results
        );
    }

    public String createWeeklyImage(int weekId, Map<String, Integer> usersPoints) {
        if (usersPoints == null || usersPoints.isEmpty()) {
            return null;
        }
        List<WeeklyRow> rows = new ArrayList<>();
        int place = 1;
        for (Map.Entry<String, Integer> e : usersPoints.entrySet()) {
            String login = e.getKey() != null ? e.getKey() : "?";
            String shortLogin = login.length() >= 3 ? login.substring(0, 3) : login;
            rows.add(new WeeklyRow(place++, shortLogin.toUpperCase(Locale.ROOT), e.getValue() != null ? e.getValue() : 0));
        }
        return renderHtml(CardHtml.buildWeeklyHtml(new WeeklyCard(weekId, rows)), "epl");
    }

    public String createYourPredictImage(Match match, String predictScore) {
        if (match == null) {
            return null;
        }
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        String kickoff = match.getLocalDateTime() != null
                ? match.getLocalDateTime().toLocalTime().toString().substring(0, 5)
                : "--:--";
        YourPredictCard card = new YourPredictCard(
                home != null ? home.getCode() : "?",
                away != null ? away.getCode() : "?",
                CardLogos.resolveLogoSrc(match.getHomeTeamId(), null),
                CardLogos.resolveLogoSrc(match.getAwayTeamId(), null),
                "WEEK " + match.getWeekId(),
                kickoff,
                predictScore != null ? predictScore : "-:-"
        );
        return renderHtml(CardHtml.buildYourPredictHtml(card), "epl");
    }

    /** Embed a chart PNG (already rendered) into the shared card chrome. */
    public String createChartImage(byte[] chartPngBytes) {
        if (chartPngBytes == null || chartPngBytes.length == 0) {
            return null;
        }
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(chartPngBytes);
        return renderHtml(CardHtml.buildChartHtml(dataUri), "epl");
    }

    private ReminderCard buildReminderCard(
            int matchPublicId, int homeTeamId, int awayTeamId, String kickoff, boolean weekBonus
    ) {
        Team home = DaoUtil.TEAMS.get(homeTeamId);
        Team away = DaoUtil.TEAMS.get(awayTeamId);
        Odd odd = oddsService.getOdd(matchPublicId);
        if (odd == null) {
            Match matchForOdds = matchService.findByTeamIds(homeTeamId, awayTeamId);
            if (matchForOdds != null) {
                oddsService.ensureFresh(List.of(matchForOdds));
            }
            odd = oddsService.getOdd(matchPublicId);
        }
        List<FormChip> homeForm = formChips(matchService.findLast5MatchesByTeamId(homeTeamId), homeTeamId);
        List<FormChip> awayForm = formChips(matchService.findLast5MatchesByTeamId(awayTeamId), awayTeamId);
        List<H2hChip> h2hChips = new ArrayList<>();
        if (home != null && away != null) {
            List<HeadToHead> h2h = headToHeadService.findAllByTwoTeamsCode(home.getCode(), away.getCode());
            for (int i = 0; i < Math.min(8, h2h != null ? h2h.size() : 0); i++) {
                HeadToHead row = h2h.get(i);
                h2hChips.add(new H2hChip(
                        CardLogos.resolveLogoSrc(row.getHomeTeamId(), null),
                        CardLogos.resolveLogoSrc(row.getAwayTeamId(), null),
                        row.getHomeTeamScore() + ":" + row.getAwayTeamScore()
                ));
            }
        }
        return new ReminderCard(
                "epl",
                "PREDICT",
                home != null ? home.getCode() : "?",
                away != null ? away.getCode() : "?",
                CardLogos.resolveLogoSrc(homeTeamId, null),
                CardLogos.resolveLogoSrc(awayTeamId, null),
                kickoff != null ? kickoff : "--:--",
                odd != null ? formatOdd(odd.home()) : "—",
                odd != null ? formatOdd(odd.draw()) : "—",
                odd != null ? formatOdd(odd.away()) : "—",
                homeForm,
                awayForm,
                h2hChips,
                weekBonus
        );
    }

    private static List<FormChip> formChips(List<Match> matches, int teamId) {
        List<FormChip> chips = new ArrayList<>();
        if (matches == null) {
            return chips;
        }
        for (Match m : matches) {
            if (m.getHomeTeamScore() == null || m.getAwayTeamScore() == null) {
                continue;
            }
            boolean isHome = m.getHomeTeamId() == teamId;
            int opponentId = isHome ? m.getAwayTeamId() : m.getHomeTeamId();
            String score = isHome
                    ? m.getHomeTeamScore() + ":" + m.getAwayTeamScore()
                    : m.getAwayTeamScore() + ":" + m.getHomeTeamScore();
            chips.add(new FormChip(CardLogos.resolveLogoSrc(opponentId, null), score));
            if (chips.size() >= 5) {
                break;
            }
        }
        return chips;
    }

    private static List<ResultLine> toResultLines(List<Result> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .limit(8)
                .map(r -> new ResultLine(r.login(), r.predict(), r.point(), r.weekBonus()))
                .toList();
    }

    static TodayCard toTodayCard(List<MatchRecord> list) {
        List<MatchRecord> epl = list.stream()
                .filter(m -> !m.isCup())
                .sorted(Comparator.comparing(MatchRecord::localDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(MatchRecord::publicId))
                .toList();
        List<MatchRecord> cups = list.stream()
                .filter(MatchRecord::isCup)
                .sorted(Comparator.comparing(MatchRecord::localDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(MatchRecord::publicId))
                .toList();

        List<TodaySection> sections = new ArrayList<>();
        epl.stream()
                .collect(Collectors.groupingBy(MatchRecord::weekId, LinkedHashMap::new, Collectors.toList()))
                .forEach((weekId, rows) -> sections.add(new TodaySection(
                        "WEEK " + (weekId != null ? weekId : "?"),
                        rows.stream().map(HtmlImageRenderer::toTodayRow).toList()
                )));
        cups.stream()
                .collect(Collectors.groupingBy(MatchRecord::competition, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.comparing(
                        e -> e.getValue().getFirst().localDateTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(e -> sections.add(new TodaySection(
                        cupSectionTitle(e.getKey()),
                        e.getValue().stream().map(HtmlImageRenderer::toTodayRow).toList()
                )));
        return new TodayCard(sections);
    }

    private static TodayRow toTodayRow(MatchRecord mr) {
        String kickoff = mr.localDateTime() != null
                ? mr.localDateTime().toLocalTime().toString().substring(0, 5)
                : "--:--";
        return new TodayRow(
                displayCode(mr, true),
                displayCode(mr, false),
                CardLogos.resolveLogoSrc(mr.homeTeamId(), mr.homeLogoUrl()),
                CardLogos.resolveLogoSrc(mr.awayTeamId(), mr.awayLogoUrl()),
                kickoff
        );
    }

    private static String displayCode(MatchRecord mr, boolean home) {
        if (mr.isCup()) {
            String code = home ? mr.homeCode() : mr.awayCode();
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        Integer id = home ? mr.homeTeamId() : mr.awayTeamId();
        Team t = id != null ? DaoUtil.TEAMS.get(id) : null;
        return t != null && t.getCode() != null ? t.getCode() : "?";
    }

    static String cupSectionTitle(String competition) {
        if (competition == null) {
            return "CUP";
        }
        return switch (competition) {
            case "eng.fa" -> "FA CUP";
            case "eng.league_cup" -> "CARABAO";
            case "uefa.champions" -> "UCL";
            case "uefa.europa" -> "UEL";
            case "uefa.europa.conf" -> "UECL";
            default -> competition.toUpperCase(Locale.ROOT);
        };
    }

    public static String competitionAccent(String competition) {
        if (competition == null || competition.isBlank()) {
            return "epl";
        }
        return competition;
    }

    public static String competitionBadge(String competition) {
        return cupSectionTitle(competition) + " · FULL TIME";
    }

    private String renderHtml(String html, String accentForLog) {
        boolean acquired = false;
        try {
            acquired = renderGate.tryAcquire(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("HTML card render timed out waiting for gate ({})", accentForLog);
                return null;
            }
            Path dir = Files.createTempDirectory("notif-card-");
            Path htmlFile = dir.resolve("card.html");
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
            Path png = Files.createTempFile("notif-card-", ".png");

            ensureBrowser();
            try (var context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(WIDTH, HEIGHT)
                    .setDeviceScaleFactor(1.0))) {
                Page page = context.newPage();
                // DOMCONTENTLOADED: do not wait for remote imgs/fonts (ESPN CDN hangs from Odyssey).
                page.navigate(
                        htmlFile.toUri().toString(),
                        new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                .setTimeout(15_000)
                );
                page.waitForTimeout(200);
                page.screenshot(new Page.ScreenshotOptions().setPath(png).setType(com.microsoft.playwright.options.ScreenshotType.PNG));
            }
            return png.toAbsolutePath().toString();
        } catch (Exception e) {
            panicSender.sendPanic("HTML card render failed", e);
            log.error("HTML card render failed: {}", e.getMessage());
            return null;
        } finally {
            if (acquired) {
                renderGate.release();
            }
        }
    }

    private synchronized void ensureBrowser() {
        if (browser != null) {
            return;
        }
        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--font-render-hinting=none"
                ));
        String exe = resolveChromeExecutable();
        if (exe != null) {
            opts.setExecutablePath(Path.of(exe));
            log.info("HtmlImageRenderer using Chrome at {}", exe);
        }
        browser = playwright.chromium().launch(opts);
    }

    private String resolveChromeExecutable() {
        if (chromeExecutable != null && !chromeExecutable.isBlank()) {
            return chromeExecutable;
        }
        String env = System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
        if (env != null && !env.isBlank() && Files.isExecutable(Path.of(env))) {
            return env;
        }
        for (String candidate : List.of(
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium"
        )) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static String nzCode(String code, Integer teamId) {
        if (code != null && !code.isBlank()) {
            return code;
        }
        Team t = teamId != null ? DaoUtil.TEAMS.get(teamId) : null;
        return t != null && t.getCode() != null ? t.getCode() : "?";
    }

    private static String codeOf(int teamId) {
        Team t = DaoUtil.TEAMS.get(teamId);
        return t != null && t.getCode() != null ? t.getCode() : "?";
    }

    private static String formatOdd(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
