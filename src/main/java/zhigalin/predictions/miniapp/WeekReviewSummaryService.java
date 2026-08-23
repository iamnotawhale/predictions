package zhigalin.predictions.miniapp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.FormItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.WeekReviewItem;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.odds.OddsService.Odd;

@Service
public class WeekReviewSummaryService {

    public String buildWeekSummary(int totalPoints, List<WeekReviewItem> items) {
        List<WeekReviewItem> scored = items.stream()
                .filter(item -> item.points() != null)
                .toList();
        if (scored.isEmpty()) {
            boolean hasUpcoming = items.stream().anyMatch(item -> item.points() == null && !item.hasPrediction());
            if (hasUpcoming) {
                return "Часть матчей ещё впереди — полный разбор появится после результатов.";
            }
            return "Сделайте прогнозы до начала матчей, чтобы получить разбор тура.";
        }
        long exact = scored.stream().filter(item -> item.points() == 4).count();
        long positive = scored.stream().filter(item -> item.points() > 0).count();
        long missed = scored.stream().filter(item -> item.points() < 0).count();
        StringBuilder sb = new StringBuilder();
        sb.append("Итого ").append(totalPoints).append(" очк. за тур.");
        if (exact > 0) {
            sb.append(" Точных счетов — ").append(exact).append('.');
        }
        sb.append(" Угадано ").append(positive).append(" из ").append(scored.size()).append('.');
        if (missed > 0) {
            sb.append(" Промахов — ").append(missed).append('.');
        }
        return sb.toString();
    }

    public String buildMatchSummary(
            Match match,
            String homeCode,
            String awayCode,
            Prediction prediction,
            boolean hasPrediction,
            Integer points,
            List<FormItem> homeForm,
            List<FormItem> awayForm,
            Odd odd
    ) {
        List<String> parts = new ArrayList<>();
        if (!hasPrediction) {
            if (points != null && points < 0) {
                parts.add("Прогноз не был сделан — автоматически −1 очко.");
            } else if (points == null) {
                parts.add("Прогноз ещё можно поставить до начала матча.");
                return joinParts(parts);
            }
        }

        String formNote = describeFormContrast(homeCode, awayCode, homeForm, awayForm);
        if (formNote != null) {
            parts.add(formNote);
        }

        if (hasPrediction && odd != null) {
            String oddsNote = describeOddsVsPrediction(
                    homeCode,
                    awayCode,
                    prediction.getHomeTeamScore(),
                    prediction.getAwayTeamScore(),
                    odd
            );
            if (oddsNote != null) {
                parts.add(oddsNote);
            }
        } else if (odd != null) {
            parts.add(describeOddsLine(homeCode, awayCode, odd));
        }

        String resultNote = describeResult(points, hasPrediction);
        if (resultNote != null) {
            parts.add(resultNote);
        }

        if (parts.isEmpty()) {
            return null;
        }
        return joinParts(parts);
    }

    private static String joinParts(List<String> parts) {
        return String.join(" ", parts);
    }

    private static String describeFormContrast(
            String homeCode,
            String awayCode,
            List<FormItem> homeForm,
            List<FormItem> awayForm
    ) {
        String homeNote = describeTeamForm(homeCode, homeForm);
        String awayNote = describeTeamForm(awayCode, awayForm);
        if (homeNote != null && awayNote != null) {
            return homeNote + " " + awayNote + ".";
        }
        if (homeNote != null) {
            return homeNote + ".";
        }
        if (awayNote != null) {
            return awayNote + ".";
        }
        return null;
    }

    private static String describeTeamForm(String teamCode, List<FormItem> form) {
        if (form.isEmpty()) {
            return null;
        }
        int wins = countOutcome(form, "W");
        int draws = countOutcome(form, "D");
        int losses = countOutcome(form, "L");
        int sample = form.size();
        if (wins >= 3) {
            return teamCode + " перед матчем — " + wins + " побед из " + sample;
        }
        if (losses >= 3) {
            return teamCode + " перед матчем — " + losses + " поражений из " + sample;
        }
        return teamCode + " перед матчем — форма " + formatFormLine(form);
    }

    private static int countOutcome(List<FormItem> form, String outcome) {
        return (int) form.stream().filter(item -> outcome.equals(item.outcome())).count();
    }

    private static String formatFormLine(List<FormItem> form) {
        StringBuilder sb = new StringBuilder();
        for (FormItem item : form) {
            if (!sb.isEmpty()) {
                sb.append('-');
            }
            sb.append(switch (item.outcome()) {
                case "W" -> "В";
                case "L" -> "П";
                default -> "Н";
            });
        }
        return sb.toString();
    }

    private static String describeOddsLine(String homeCode, String awayCode, Odd odd) {
        Favorite favorite = resolveFavorite(homeCode, awayCode, odd);
        return "Линия: фаворит " + favorite.code() + " (" + formatOdd(favorite.decimal()) + ").";
    }

    private static String describeOddsVsPrediction(
            String homeCode,
            String awayCode,
            int predictHome,
            int predictAway,
            Odd odd
    ) {
        Favorite favorite = resolveFavorite(homeCode, awayCode, odd);
        String predicted = resolveOutcome(predictHome, predictAway);
        String predictedCode = teamCodeForOutcome(predicted, homeCode, awayCode);
        String score = predictHome + ":" + predictAway;
        if (predicted.equals(favorite.side())) {
            return "Ваш прогноз " + score + " — вместе с фаворитом " + favorite.code()
                   + " (" + formatOdd(favorite.decimal()) + ").";
        }
        return "Прогноз " + score + " (" + predictedCode + ") — против линии, фаворит "
               + favorite.code() + " (" + formatOdd(favorite.decimal()) + ").";
    }

    private static String describeResult(Integer points, boolean hasPrediction) {
        if (points == null) {
            return null;
        }
        return switch (points) {
            case 4 -> "Итог: точный счёт, +4 очка.";
            case 2 -> "Итог: угадана разница мячей, +2 очка.";
            case 1 -> "Итог: верный исход, +1 очко.";
            case 0 -> "Матч ещё идёт — очки предварительные.";
            case -1 -> hasPrediction ? "Итог: промах, −1 очко." : null;
            default -> null;
        };
    }

    private static Favorite resolveFavorite(String homeCode, String awayCode, Odd odd) {
        if (odd.home() <= odd.draw() && odd.home() <= odd.away()) {
            return new Favorite("home", homeCode, odd.home());
        }
        if (odd.away() <= odd.draw() && odd.away() <= odd.home()) {
            return new Favorite("away", awayCode, odd.away());
        }
        return new Favorite("draw", "ничья", odd.draw());
    }

    private static String resolveOutcome(int homeScore, int awayScore) {
        if (homeScore > awayScore) {
            return "home";
        }
        if (homeScore < awayScore) {
            return "away";
        }
        return "draw";
    }

    private static String teamCodeForOutcome(String outcome, String homeCode, String awayCode) {
        return switch (outcome) {
            case "home" -> homeCode;
            case "away" -> awayCode;
            default -> "ничья";
        };
    }

    private static String formatOdd(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private record Favorite(String side, String code, double decimal) {
    }

    public List<FormItem> buildRecentFormBefore(
            List<Match> allMatches,
            int teamId,
            int limit,
            LocalDateTime beforeKickoff
    ) {
        return allMatches.stream()
                .filter(match -> match.getHomeTeamId() == teamId || match.getAwayTeamId() == teamId)
                .filter(match -> match.getHomeTeamScore() != null && match.getAwayTeamScore() != null)
                .filter(match -> isFinishedStatus(match.getStatus()))
                .filter(match -> beforeKickoff == null
                                 || match.getLocalDateTime() == null
                                 || match.getLocalDateTime().isBefore(beforeKickoff))
                .sorted(java.util.Comparator
                        .comparing(Match::getLocalDateTime, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .reversed())
                .limit(limit)
                .map(match -> toFormItem(match, teamId))
                .toList();
    }

    private static boolean isFinishedStatus(String status) {
        return status != null && java.util.Set.of("ft", "aet", "pen", "canc", "abd", "awrd", "wo")
                .contains(status.toLowerCase());
    }

    private static FormItem toFormItem(Match match, int teamId) {
        boolean teamIsHome = match.getHomeTeamId() == teamId;
        int ownScore = teamIsHome ? match.getHomeTeamScore() : match.getAwayTeamScore();
        int opponentScore = teamIsHome ? match.getAwayTeamScore() : match.getHomeTeamScore();
        int opponentId = teamIsHome ? match.getAwayTeamId() : match.getHomeTeamId();
        var opponent = zhigalin.predictions.util.DaoUtil.TEAMS.get(opponentId);
        String outcome = ownScore > opponentScore ? "W" : ownScore < opponentScore ? "L" : "D";
        return new FormItem(
                outcome,
                ownScore,
                opponentScore,
                opponent != null ? opponent.getCode() : "?",
                match.getLocalDateTime() != null
                        ? match.getLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
                        : ""
        );
    }
}
