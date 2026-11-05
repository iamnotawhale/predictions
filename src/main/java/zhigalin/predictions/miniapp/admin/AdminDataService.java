package zhigalin.predictions.miniapp.admin;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.admin.AdminTableRegistry.TableSpec;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminColumnItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminRowMutationResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminTableItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminTableRowsResponse;
@Service
public class AdminDataService {

    private static final Logger log = LoggerFactory.getLogger(AdminDataService.class);
    private static final int MAX_LIMIT = 100;
    private static final int MAX_OFFSET = 10_000;
    private static final String MASK = "***";

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminGuard adminGuard;

    public AdminDataService(DataSource dataSource, AdminGuard adminGuard) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.adminGuard = adminGuard;
    }

    public List<AdminTableItem> listTables(String telegramId) {
        adminGuard.requireAdmin(telegramId);
        return AdminTableRegistry.all().stream()
                .map(t -> new AdminTableItem(
                        t.id(),
                        t.label(),
                        t.primaryKeys(),
                        t.readOnly(),
                        t.noInsert(),
                        t.noDelete()
                ))
                .toList();
    }

    public List<AdminColumnItem> listColumns(String telegramId, String tableId) {
        adminGuard.requireAdmin(telegramId);
        return loadColumns(requireTable(tableId));
    }

    public AdminTableRowsResponse listRows(
            String telegramId,
            String tableId,
            int limit,
            int offset,
            String query
    ) {
        adminGuard.requireAdmin(telegramId);
        TableSpec spec = requireTable(tableId);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safeOffset = Math.min(Math.max(offset, 0), MAX_OFFSET);
        String tableSql = AdminTableRegistry.quoteIdent(spec.id());
        String orderCol = AdminTableRegistry.quoteIdent(spec.orderColumn());
        String orderSql = buildOrderBy(spec, orderCol);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeLimit)
                .addValue("offset", safeOffset);

        String where = "";
        if (query != null && !query.isBlank()) {
            where = " WHERE CAST(" + orderCol + " AS TEXT) ILIKE :q ESCAPE '\\' ";
            params.addValue("q", "%" + AdminValueCoercion.escapeLikePattern(query.trim()) + "%");
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tableSql + where,
                params,
                Long.class
        );

        String sql = "SELECT * FROM " + tableSql + where + orderSql
                + " LIMIT :limit OFFSET :offset";
        List<Map<String, Object>> rows = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> mapRow(rs, spec)
        );

        return new AdminTableRowsResponse(total == null ? 0 : total, safeLimit, safeOffset, rows);
    }

    @Transactional
    public AdminRowMutationResponse saveRow(
            String telegramId,
            String tableId,
            Map<String, Object> values,
            Map<String, Object> primaryKey,
            String mode
    ) {
        adminGuard.requireAdmin(telegramId);
        TableSpec spec = requireTable(tableId);
        if (spec.readOnly()) {
            throw new MiniAppException(403, "Таблица только для чтения.");
        }
        List<AdminColumnItem> columns = loadColumns(spec);
        Map<String, AdminColumnItem> columnByName = columns.stream()
                .collect(Collectors.toMap(AdminColumnItem::name, c -> c, (a, b) -> a, LinkedHashMap::new));

        Map<String, Object> coerced = coerceIncoming(values, columnByName, spec);

        String normalizedMode = mode == null ? "" : mode.trim().toLowerCase();
        try {
            if ("create".equals(normalizedMode)) {
                if (spec.noInsert()) {
                    throw new MiniAppException(403, "Создание строк запрещено для этой таблицы.");
                }
                Map<String, Object> insertValues = dropNulls(coerced);
                validateInsert(spec, insertValues);
                insertRow(spec, insertValues);
                audit(telegramId, "INSERT", spec.id(), insertValues, null);
                return new AdminRowMutationResponse(true, "Строка создана");
            }
            if ("update".equals(normalizedMode)) {
                if (primaryKey == null || primaryKey.isEmpty()) {
                    throw new MiniAppException(400, "Нужен primaryKey для обновления.");
                }
                Map<String, Object> pk = extractPk(spec, primaryKey, primaryKey);
                if (!rowExists(spec, pk)) {
                    throw new MiniAppException(404, "Строка не найдена.");
                }
                int updated = updateRow(spec, coerced, pk, columnByName);
                if (updated != 1) {
                    throw new MiniAppException(409, "Обновлено строк: " + updated + " (ожидалась 1).");
                }
                audit(telegramId, "UPDATE", spec.id(), coerced, pk);
                return new AdminRowMutationResponse(true, "Строка обновлена");
            }
            throw new MiniAppException(400, "Укажите mode: create или update.");
        } catch (DataIntegrityViolationException e) {
            log.warn("Admin DB constraint: table={} mode={} msg={}", tableId, mode, e.getMessage());
            throw new MiniAppException(409, "Нарушение ограничений БД (уникальность, FK, XOR и т.д.).");
        }
    }

    @Transactional
    public AdminRowMutationResponse deleteRow(
            String telegramId,
            String tableId,
            Map<String, Object> primaryKey
    ) {
        adminGuard.requireAdmin(telegramId);
        TableSpec spec = requireTable(tableId);
        if (spec.readOnly() || spec.noDelete()) {
            throw new MiniAppException(403, "Удаление запрещено для этой таблицы.");
        }
        if (primaryKey == null || primaryKey.isEmpty()) {
            throw new MiniAppException(400, "Нужен primaryKey для удаления.");
        }
        Map<String, Object> pk = extractPk(spec, primaryKey, primaryKey);
        String tableSql = AdminTableRegistry.quoteIdent(spec.id());
        WhereClause where = buildPkWhere(spec, pk);
        try {
            int n = jdbc.update("DELETE FROM " + tableSql + where.sql(), where.params());
            if (n == 0) {
                throw new MiniAppException(404, "Строка не найдена.");
            }
            if (n != 1) {
                throw new MiniAppException(409, "Удалено строк: " + n + " (ожидалась 1).");
            }
            audit(telegramId, "DELETE", spec.id(), null, pk);
            return new AdminRowMutationResponse(true, "Удалено");
        } catch (DataIntegrityViolationException e) {
            log.warn("Admin DB delete constraint: table={} msg={}", tableId, e.getMessage());
            throw new MiniAppException(409, "Нельзя удалить: есть связанные данные.");
        }
    }

    private static TableSpec requireTable(String tableId) {
        try {
            return AdminTableRegistry.require(tableId);
        } catch (IllegalArgumentException e) {
            throw new MiniAppException(404, e.getMessage());
        }
    }

    private List<AdminColumnItem> loadColumns(TableSpec spec) {
        String sql = """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :table
                ORDER BY ordinal_position
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("table", spec.id()),
                (rs, rowNum) -> new AdminColumnItem(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        rs.getString("column_default")
                )
        );
    }

    private Map<String, Object> mapRow(ResultSet rs, TableSpec spec) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnName(i);
            if (spec.hiddenColumns().contains(name)) {
                row.put(name, MASK);
                continue;
            }
            Object val = rs.getObject(i);
            row.put(name, formatValue(val));
        }
        return row;
    }

    private static Object formatValue(Object val) {
        if (val instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (val instanceof Date d) {
            return d.toLocalDate().toString();
        }
        return val;
    }

    private Map<String, Object> coerceIncoming(
            Map<String, Object> values,
            Map<String, AdminColumnItem> columnByName,
            TableSpec spec
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!columnByName.containsKey(e.getKey())) {
                continue;
            }
            if (spec.readOnlyColumns().contains(e.getKey())) {
                continue;
            }
            AdminColumnItem col = columnByName.get(e.getKey());
            Object coerced = AdminValueCoercion.coerceForWrite(col, e.getValue());
            if (coerced instanceof java.time.LocalDate ld) {
                coerced = Date.valueOf(ld);
            }
            out.put(e.getKey(), coerced);
        }
        return out;
    }

    private static Map<String, Object> dropNulls(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private Map<String, Object> extractPk(
            TableSpec spec,
            Map<String, Object> row,
            Map<String, Object> explicitPk
    ) {
        Map<String, AdminColumnItem> columnByName = loadColumns(spec).stream()
                .collect(Collectors.toMap(AdminColumnItem::name, c -> c));
        Map<String, Object> pk = new LinkedHashMap<>();
        for (String col : spec.primaryKeys()) {
            Object raw = explicitPk != null && explicitPk.containsKey(col)
                    ? explicitPk.get(col)
                    : row.get(col);
            AdminColumnItem meta = columnByName.get(col);
            Object v = meta == null ? raw : AdminValueCoercion.coerceForWrite(meta, raw);
            if (v instanceof java.time.LocalDate ld) {
                v = Date.valueOf(ld);
            }
            pk.put(col, v);
        }
        return pk;
    }

    private boolean rowExists(TableSpec spec, Map<String, Object> pk) {
        String tableSql = AdminTableRegistry.quoteIdent(spec.id());
        WhereClause where = buildPkWhere(spec, pk);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tableSql + where.sql(),
                where.params(),
                Integer.class
        );
        return n != null && n > 0;
    }

    private int updateRow(
            TableSpec spec,
            Map<String, Object> row,
            Map<String, Object> pk,
            Map<String, AdminColumnItem> columnByName
    ) {
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (spec.primaryKeys().contains(e.getKey())) {
                continue;
            }
            if (spec.readOnlyColumns().contains(e.getKey())) {
                continue;
            }
            if (!columnByName.containsKey(e.getKey())) {
                continue;
            }
            String param = "v_" + e.getKey();
            sets.add(AdminTableRegistry.quoteIdent(e.getKey()) + " = :" + param);
            params.addValue(param, e.getValue());
        }
        if (sets.isEmpty()) {
            throw new MiniAppException(400, "Нет полей для обновления.");
        }
        WhereClause where = buildPkWhere(spec, pk);
        params.addValues(where.params().getValues());
        String tableSql = AdminTableRegistry.quoteIdent(spec.id());
        return jdbc.update(
                "UPDATE " + tableSql + " SET " + String.join(", ", sets) + where.sql(),
                params
        );
    }

    private void validateInsert(TableSpec spec, Map<String, Object> row) {
        if ("predict".equals(spec.id())) {
            boolean hasMatch = row.containsKey("match_id");
            boolean hasCup = row.containsKey("cup_match_id");
            if (hasMatch == hasCup) {
                throw new MiniAppException(
                        400,
                        "Укажите ровно одно: match_id (АПЛ) или cup_match_id (кубок)."
                );
            }
        }
    }

    private void insertRow(TableSpec spec, Map<String, Object> row) {
        if ("match".equals(spec.id()) && row.get("public_id") != null) {
            insertMatchIfPublicIdFree(row);
            return;
        }
        List<String> cols = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (spec.readOnlyColumns().contains(e.getKey())) {
                continue;
            }
            cols.add(AdminTableRegistry.quoteIdent(e.getKey()));
            String param = "i_" + e.getKey();
            placeholders.add(":" + param);
            params.addValue(param, e.getValue());
        }
        if (cols.isEmpty()) {
            throw new MiniAppException(400, "Пустая строка.");
        }
        String tableSql = AdminTableRegistry.quoteIdent(spec.id());
        String sql = "INSERT INTO " + tableSql + " (" + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", placeholders) + ")";
        jdbc.update(sql, params);
    }

    private void insertMatchIfPublicIdFree(Map<String, Object> row) {
        List<String> cols = new ArrayList<>();
        List<String> selectParts = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        Object publicId = row.get("public_id");
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            cols.add(AdminTableRegistry.quoteIdent(e.getKey()));
            String param = "i_" + e.getKey();
            selectParts.add(":" + param);
            params.addValue(param, e.getValue());
        }
        if (cols.isEmpty()) {
            throw new MiniAppException(400, "Пустая строка.");
        }
        String tableSql = AdminTableRegistry.quoteIdent("match");
        String sql = "INSERT INTO " + tableSql + " (" + String.join(", ", cols) + ") SELECT "
                + String.join(", ", selectParts)
                + " WHERE NOT EXISTS (SELECT 1 FROM " + tableSql
                + " WHERE public_id IS NOT DISTINCT FROM :i_public_id_guard)";
        params.addValue("i_public_id_guard", publicId);
        int n = jdbc.update(sql, params);
        if (n == 0) {
            throw new MiniAppException(409, "Матч с таким public_id уже существует.");
        }
    }

    private WhereClause buildPkWhere(TableSpec spec, Map<String, Object> pk) {
        List<String> parts = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String col : spec.primaryKeys()) {
            Object v = pk.get(col);
            String param = "pk_" + col;
            if (v == null) {
                parts.add(AdminTableRegistry.quoteIdent(col) + " IS NULL");
            } else {
                parts.add(AdminTableRegistry.quoteIdent(col) + " IS NOT DISTINCT FROM :" + param);
                params.addValue(param, v);
            }
        }
        return new WhereClause(" WHERE " + String.join(" AND ", parts), params);
    }

    private static String buildOrderBy(TableSpec spec, String orderCol) {
        StringBuilder sb = new StringBuilder(" ORDER BY ").append(orderCol).append(" DESC NULLS LAST");
        for (String pk : spec.primaryKeys()) {
            if (pk.equals(spec.orderColumn())) {
                continue;
            }
            sb.append(", ").append(AdminTableRegistry.quoteIdent(pk)).append(" DESC");
        }
        return sb.toString();
    }

    private void audit(String telegramId, String action, String table, Object payload, Map<String, Object> pk) {
        log.info("ADMIN_DATA {} admin={} table={} pk={} payload={}", action, telegramId, table, pk, payload);
    }

    private record WhereClause(String sql, MapSqlParameterSource params) {
    }
}
