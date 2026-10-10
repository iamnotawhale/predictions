package zhigalin.predictions.service.notification;

import static zhigalin.predictions.service.notification.CardRenderModels.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Pure HTML builders for notification cards (no I/O except loading the shared stylesheet). */
final class CardHtml {

    private CardHtml() {
    }

    static String buildTodayHtml(TodayCard card) {
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
                + "<div class=\"foot\">predictions</div>",
                "card-narrow");
    }

    static String buildReminderHtml(ReminderCard card) {
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

    static String buildResultHtml(ResultCard card) {
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
                + "<div class=\"foot\">predictions</div>",
                "card-narrow");
    }

    static String buildWeeklyHtml(WeeklyCard card) {
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
                + "<div class=\"foot\">predictions</div>",
                "card-narrow");
    }

    static String buildYourPredictHtml(YourPredictCard card) {
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
                + "<div class=\"foot\">predictions</div>",
                "card-narrow");
    }

    static String buildChartHtml(String chartDataUri) {
        return shell("epl",
                "<div class=\"badge\">CHART</div>"
                + "<h1 class=\"heading\">Очки по неделям</h1>"
                + "<div class=\"chart-wrap\"><img src=\"" + escapeAttr(chartDataUri) + "\" alt=\"chart\"/></div>"
                + "<div class=\"foot\">predictions</div>",
                "card-narrow");
    }

    private static String oddCell(String label, String val) {
        return "<div class=\"odd-cell\"><div class=\"label\">" + label + "</div><div class=\"val\">"
               + escape(val) + "</div></div>";
    }

    private static String img(String src) {
        if (src == null || src.isBlank()) {
            return "<div class=\"stub\"></div>";
        }
        return "<img src=\"" + escapeAttr(src) + "\" alt=\"\"/>";
    }

    static String shell(String accent, String body) {
        return shell(accent, body, null);
    }

    static String shell(String accent, String body, String layoutClass) {
        String css;
        try {
            css = readClasspath("notification-cards/base.css");
        } catch (Exception e) {
            css = "body{background:#0b0910;color:#fff;font-family:sans-serif}";
        }
        String extra = (layoutClass != null && !layoutClass.isBlank()) ? (" " + layoutClass.trim()) : "";
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
                  <div class="card%s" data-accent="%s">%s</div>
                </body>
                </html>
                """.formatted(css, extra, escapeAttr(accent), body);
    }

    static String readClasspath(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
