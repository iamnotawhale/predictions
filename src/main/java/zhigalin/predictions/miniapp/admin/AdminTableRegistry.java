package zhigalin.predictions.miniapp.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AdminTableRegistry {

    public record TableSpec(
            String id,
            String label,
            List<String> primaryKeys,
            String orderColumn,
            Set<String> hiddenColumns,
            Set<String> readOnlyColumns,
            boolean readOnly,
            boolean noInsert,
            boolean noDelete
    ) {
    }

    private static final List<TableSpec> TABLES = List.of(
            new TableSpec("match", "Матчи АПЛ", List.of("public_id"), "local_date_time",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("predict", "Прогнозы", List.of("user_id", "match_id", "cup_match_id"), "user_id",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("cup_match", "Кубковые матчи", List.of("public_id"), "local_date_time",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("teams", "Команды", List.of("public_id"), "code",
                    Set.of(), Set.of(), false, false, true),
            new TableSpec("weeks", "Туры", List.of("id"), "id",
                    Set.of(), Set.of(), false, false, true),
            new TableSpec("users", "Пользователи", List.of("id"), "id",
                    Set.of("password"), Set.of("password"), false, false, true),
            new TableSpec("h2h", "H2H", List.of("home_team_id", "away_team_id", "local_date_time"), "local_date_time",
                    Set.of(), Set.of(), true, true, true),
            new TableSpec("user_week_bonus_match", "Бонус тура", List.of("user_id", "week_id"), "week_id",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("cup_match_tip", "Кубковые tips", List.of("cup_match_id"), "cup_match_id",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("match_recommendation", "Рекомендации EPL", List.of("match_public_id"), "week_id",
                    Set.of(), Set.of(), false, false, false),
            new TableSpec("recommender_team_stats", "Снимок команд (рекомендатор)", List.of("week_id", "team_code"), "week_id",
                    Set.of(), Set.of(), true, true, true),
            new TableSpec("recommender_league_snapshot", "Снимок лиги (рекомендатор)", List.of("week_id"), "week_id",
                    Set.of(), Set.of(), true, true, true),
            new TableSpec("fixture_xg", "Кэш xG матчей", List.of("fixture_id"), "fetched_at",
                    Set.of(), Set.of(), true, true, true),
            new TableSpec("notification_weekly_results_sent", "Отправка итогов тура", List.of("week_id"), "week_id",
                    Set.of(), Set.of(), true, true, true),
            new TableSpec(
                    "notification_reminder_sent",
                    "Напоминания",
                    List.of("user_id", "match_public_id", "reminder_minutes"),
                    "sent_at",
                    Set.of(), Set.of(), true, true, true
            )
    );

    private static final Map<String, TableSpec> BY_ID = TABLES.stream()
            .collect(Collectors.toUnmodifiableMap(TableSpec::id, Function.identity()));

    private AdminTableRegistry() {
    }

    public static List<TableSpec> all() {
        return TABLES;
    }

    public static TableSpec require(String tableId) {
        return Optional.ofNullable(BY_ID.get(tableId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown table: " + tableId));
    }

    public static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
