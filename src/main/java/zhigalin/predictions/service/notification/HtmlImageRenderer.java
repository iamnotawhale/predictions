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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.odds.OddsService;
import zhigalin.predictions.service.odds.OddsService.Odd;
import zhigalin.predictions.util.DaoUtil;

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
        return renderHtml(buildTodayHtml(toTodayCard(list)), "epl");
    }

    public String createReminderImage(int matchPublicId, int homeTeamId, int awayTeamId, String kickoff) {
        return createReminderImage(matchPublicId, homeTeamId, awayTeamId, kickoff, false);
    }

    public String createReminderImage(
            int matchPublicId, int homeTeamId, int awayTeamId, String kickoff, boolean weekBonus
    ) {
        ReminderCard card = buildReminderCard(matchPublicId, homeTeamId, awayTeamId, kickoff, weekBonus);
        return renderHtml(buildReminderHtml(card), card.accent());
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
                resolveLogoSrc(homeTeamId, homeLogoUrl),
                resolveLogoSrc(awayTeamId, awayLogoUrl),
                score != null ? score : "-:-",
                aiKickoffScore,
                toResultLines(results)
        );
        return renderHtml(buildResultHtml(card), card.accent());
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
        return renderHtml(buildWeeklyHtml(new WeeklyCard(weekId, rows)), "epl");
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
                resolveLogoSrc(match.getHomeTeamId(), null),
                resolveLogoSrc(match.getAwayTeamId(), null),
                "WEEK " + match.getWeekId(),
                kickoff,
                predictScore != null ? predictScore : "-:-"
        );
        return renderHtml(buildYourPredictHtml(card), "epl");
    }

    /** Embed a chart PNG (already rendered) into the shared card chrome. */
    public String createChartImage(byte[] chartPngBytes) {
        if (chartPngBytes == null || chartPngBytes.length == 0) {
            return null;
        }
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(chartPngBytes);
        return renderHtml(buildChartHtml(dataUri), "epl");
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
                        resolveLogoSrc(row.getHomeTeamId(), null),
                        resolveLogoSrc(row.getAwayTeamId(), null),
                        row.getHomeTeamScore() + ":" + row.getAwayTeamScore()
                ));
            }
        }
        return new ReminderCard(
                "epl",
                "PREDICT",
                home != null ? home.getCode() : "?",
                away != null ? away.getCode() : "?",
                resolveLogoSrc(homeTeamId, null),
                resolveLogoSrc(awayTeamId, null),
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
            chips.add(new FormChip(resolveLogoSrc(opponentId, null), score));
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
                resolveLogoSrc(mr.homeTeamId(), mr.homeLogoUrl()),
                resolveLogoSrc(mr.awayTeamId(), mr.awayLogoUrl()),
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

    private String buildTodayHtml(TodayCard card) {
        StringBuilder sections = new StringBuilder();
        for (TodaySection section : card.sections()) {
            sections.append("<section class=\"sheet\">");
            sections.append("<h2 class=\"section-title\">").append(escape(section.title())).append("</h2>");
            for (TodayRow row : section.rows()) {
                sections.append("<div class=\"match-row\">")
                        .append(img(row.homeLogoSrc()))
                        .append("<div class=\"code home\">").append(escape(row.homeCode())).append("</div>")
                        .append("<div class=\"center-pill\">").append(escape(row.kickoff())).append("</div>")
                        .append("<div class=\"code away\">").append(escape(row.awayCode())).append("</div>")
                        .append(img(row.awayLogoSrc()))
                        .append("</div>");
            }
            sections.append("</section>");
        }
        return shell("epl",
                "<div class=\"badge\">TODAY</div>"
                + "<h1 class=\"heading\">Сегодняшние матчи</h1>"
                + sections
                + "<div class=\"foot\">predictions</div>");
    }

    private String buildReminderHtml(ReminderCard card) {
        StringBuilder h2h = new StringBuilder("<div class=\"h2h-grid\">");
        if (card.h2h().isEmpty()) {
            h2h.append("<div class=\"hint-empty\">нет данных</div>");
        } else {
            for (H2hChip chip : card.h2h()) {
                h2h.append("<div class=\"h2h-chip\">")
                        .append(imgSm(chip.homeLogoSrc()))
                        .append("<span class=\"chip-score\">").append(escape(chip.score())).append("</span>")
                        .append(imgSm(chip.awayLogoSrc()))
                        .append("</div>");
            }
        }
        h2h.append("</div>");

        StringBuilder formHome = new StringBuilder("<div class=\"form-col\">");
        for (FormChip chip : card.homeForm()) {
            formHome.append(formChipHtml(chip, false));
        }
        if (card.homeForm().isEmpty()) {
            formHome.append("<div class=\"hint-empty\">—</div>");
        }
        formHome.append("</div>");

        StringBuilder formAway = new StringBuilder("<div class=\"form-col form-col-away\">");
        for (FormChip chip : card.awayForm()) {
            formAway.append(formChipHtml(chip, true));
        }
        if (card.awayForm().isEmpty()) {
            formAway.append("<div class=\"hint-empty\">—</div>");
        }
        formAway.append("</div>");

        return shell(card.accent(),
                "<div class=\"card-inner reminder\">"
                + "<div class=\"badge\">" + escape(card.badge()) + "</div>"
                + "<h1 class=\"heading heading-sm\">Не проставлен прогноз"
                + (card.weekBonus() ? "<span class=\"badge-bonus reminder-bonus\">бонус</span>" : "")
                + "</h1>"
                + "<div class=\"block-label\">head to head</div>"
                + h2h
                + "<div class=\"match-banner\">"
                + imgClass(card.homeLogoSrc(), "banner-logo")
                + "<div class=\"match-bar sheet\">"
                + "<span class=\"code\">" + escape(card.homeCode()) + "</span>"
                + "<span class=\"kick\">" + escape(card.kickoff()) + "</span>"
                + "<span class=\"code\">" + escape(card.awayCode()) + "</span>"
                + "</div>"
                + imgClass(card.awayLogoSrc(), "banner-logo")
                + "</div>"
                + "<div class=\"form-band\">"
                + formHome
                + "<div class=\"block-label form-mid-label\">last 5</div>"
                + formAway
                + "</div>"
                + "<div class=\"odds odds-compact\">"
                + oddCell("1", card.homeOdd()) + oddCell("X", card.drawOdd()) + oddCell("2", card.awayOdd())
                + "</div>"
                + "</div>"
                + "<div class=\"foot\">predictions</div>");
    }

    private static String formChipHtml(FormChip chip, boolean awaySide) {
        StringBuilder sb = new StringBuilder("<div class=\"form-chip\">");
        if (awaySide) {
            sb.append("<span class=\"chip-score\">").append(escape(chip.score())).append("</span>")
                    .append(imgSm(chip.opponentLogoSrc()));
        } else {
            sb.append(imgSm(chip.opponentLogoSrc()))
                    .append("<span class=\"chip-score\">").append(escape(chip.score())).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String imgSm(String src) {
        if (src == null || src.isBlank()) {
            return "<div class=\"logo-sm stub\"></div>";
        }
        return "<img class=\"logo-sm\" src=\"" + escapeAttr(src) + "\" alt=\"\"/>";
    }

    private static String imgClass(String src, String cssClass) {
        if (src == null || src.isBlank()) {
            return "<div class=\"" + cssClass + " stub\"></div>";
        }
        return "<img class=\"" + cssClass + "\" src=\"" + escapeAttr(src) + "\" alt=\"\"/>";
    }

    private String buildResultHtml(ResultCard card) {
        StringBuilder results = new StringBuilder();
        for (ResultLine line : card.results()) {
            results.append("<div class=\"result-line")
                    .append(line.weekBonus() ? " week-bonus" : "")
                    .append("\"><span class=\"result-who\">")
                    .append(escape(line.login())).append(" ")
                    .append(escape(line.predict()));
            if (line.weekBonus()) {
                results.append("<span class=\"badge-bonus\">бонус</span>");
            }
            results.append("</span><span class=\"pts\">").append(line.points()).append("</span></div>");
        }
        String ai = card.aiKickoffScore() != null && !card.aiKickoffScore().isBlank()
                ? "<div class=\"ai-line\">AI " + escape(card.aiKickoffScore()) + "</div>"
                : "";
        return shell(card.accent(),
                "<div class=\"badge\">" + escape(card.badge()) + "</div>"
                + "<h1 class=\"heading\">Матч окончен</h1>"
                + "<div class=\"sheet hero\">"
                + "<div class=\"hero-side\">" + img(card.homeLogoSrc())
                + "<div class=\"code\">" + escape(card.homeCode()) + "</div></div>"
                + "<div class=\"hero-center\"><div class=\"score\">" + escape(card.score()) + "</div>" + ai + "</div>"
                + "<div class=\"hero-side\">" + img(card.awayLogoSrc())
                + "<div class=\"code\">" + escape(card.awayCode()) + "</div></div>"
                + "</div>"
                + (results.isEmpty() ? "" : "<div class=\"sheet\"><div class=\"results\">" + results + "</div></div>")
                + "<div class=\"foot\">predictions</div>");
    }

    private String buildWeeklyHtml(WeeklyCard card) {
        StringBuilder rows = new StringBuilder("<div class=\"weekly-list\">");
        for (WeeklyRow row : card.rows()) {
            String placeClass = row.place() <= 3 ? " p" + row.place() : "";
            rows.append("<div class=\"weekly-row").append(placeClass).append("\">")
                    .append("<div class=\"weekly-place\">").append(row.place()).append("</div>")
                    .append("<div class=\"weekly-login\">").append(escape(row.login())).append("</div>")
                    .append("<div class=\"weekly-pts\">").append(row.points()).append("</div>")
                    .append("</div>");
        }
        rows.append("</div>");
        return shell("epl",
                "<div class=\"badge\">WEEK " + card.weekId() + "</div>"
                + "<h1 class=\"heading\">Результаты " + card.weekId() + " тура</h1>"
                + rows
                + "<div class=\"foot\">predictions</div>");
    }

    private String buildYourPredictHtml(YourPredictCard card) {
        return shell("epl",
                "<div class=\"badge\">" + escape(card.weekLabel()) + "</div>"
                + "<h1 class=\"heading\">Твой прогноз</h1>"
                + "<p class=\"sub\">kickoff " + escape(card.kickoff()) + "</p>"
                + "<div class=\"sheet hero\">"
                + "<div class=\"hero-side\">" + img(card.homeLogoSrc())
                + "<div class=\"code\">" + escape(card.homeCode()) + "</div></div>"
                + "<div class=\"hero-center\"><div class=\"score\">" + escape(card.predictScore()) + "</div></div>"
                + "<div class=\"hero-side\">" + img(card.awayLogoSrc())
                + "<div class=\"code\">" + escape(card.awayCode()) + "</div></div>"
                + "</div>"
                + "<div class=\"predict-banner\">Сохранено</div>"
                + "<div class=\"foot\">predictions</div>");
    }

    private String buildChartHtml(String chartDataUri) {
        return shell("epl",
                "<div class=\"badge\">CHART</div>"
                + "<h1 class=\"heading\">Очки по неделям</h1>"
                + "<div class=\"chart-wrap\"><img src=\"" + escapeAttr(chartDataUri) + "\" alt=\"chart\"/></div>"
                + "<div class=\"foot\">predictions</div>");
    }

    private static String oddCell(String label, String val) {
        return "<div class=\"odd-cell\"><div class=\"label\">" + label + "</div><div class=\"val\">"
               + escape(val) + "</div></div>";
    }

    private static String img(String src) {
        if (src == null || src.isBlank()) {
            return "<div style=\"width:56px;height:56px;border-radius:8px;background:rgba(255,255,255,.08)\"></div>";
        }
        return "<img src=\"" + escapeAttr(src) + "\" alt=\"\"/>";
    }

    private String shell(String accent, String body) {
        String css;
        try {
            css = readClasspath("notification-cards/base.css");
        } catch (Exception e) {
            css = "body{background:#0b0910;color:#fff;font-family:sans-serif}";
        }
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="utf-8"/>
                  <link rel="preconnect" href="https://fonts.googleapis.com"/>
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
                  <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@500;700&family=Manrope:wght@500;600;700&family=Unbounded:wght@600;700&display=swap" rel="stylesheet"/>
                  <style>%s</style>
                </head>
                <body>
                  <div class="card" data-accent="%s">%s</div>
                </body>
                </html>
                """.formatted(css, escapeAttr(accent), body);
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
                page.navigate(htmlFile.toUri().toString());
                page.waitForTimeout(400); // fonts
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

    static String resolveLogoSrc(Integer teamId, String remoteUrl) {
        if (teamId != null) {
            String data = classpathImageDataUri("static/img/teams/" + teamId + ".webp");
            if (data == null) {
                data = classpathImageDataUri("static/img/teams/" + teamId + ".png");
            }
            if (data != null) {
                return data;
            }
        }
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            return remoteUrl;
        }
        return "";
    }

    private static String classpathImageDataUri(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) {
                return null;
            }
            try (InputStream in = res.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                String mime = path.endsWith(".webp") ? "image/webp" : "image/png";
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readClasspath(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
