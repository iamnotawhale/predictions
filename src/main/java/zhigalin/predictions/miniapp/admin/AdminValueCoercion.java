package zhigalin.predictions.miniapp.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminColumnItem;

final class AdminValueCoercion {

    private AdminValueCoercion() {
    }

    static String escapeLikePattern(String query) {
        if (query == null) {
            return "";
        }
        return query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    static Object coerceForWrite(AdminColumnItem column, Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return column.nullable() ? null : s;
            }
            return coerceString(column, s.trim());
        }
        return raw;
    }

    private static Object coerceString(AdminColumnItem column, String s) {
        String type = column.dataType() == null ? "" : column.dataType();
        return switch (type) {
            case "integer", "bigint", "smallint" -> parseLong(s, column.name());
            case "numeric", "double precision", "real" -> parseDouble(s, column.name());
            case "boolean" -> parseBoolean(s);
            case "timestamp without time zone", "timestamp with time zone" -> parseTimestamp(s, column.name());
            case "date" -> parseDate(s, column.name());
            default -> s;
        };
    }

    private static Long parseLong(String s, String column) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new MiniAppException(400, "Колонка " + column + ": ожидается целое число.");
        }
    }

    private static Double parseDouble(String s, String column) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new MiniAppException(400, "Колонка " + column + ": ожидается число.");
        }
    }

    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "t".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "f".equalsIgnoreCase(s)) {
            return false;
        }
        throw new MiniAppException(400, "Колонка boolean: используйте true или false.");
    }

    private static Timestamp parseTimestamp(String s, String column) {
        try {
            if (s.endsWith("Z") || s.contains("+")) {
                return Timestamp.from(Instant.parse(s));
            }
            LocalDateTime ldt = LocalDateTime.parse(s);
            return Timestamp.valueOf(ldt);
        } catch (DateTimeParseException e) {
            try {
                return Timestamp.from(OffsetDateTime.parse(s).toInstant());
            } catch (DateTimeParseException e2) {
                throw new MiniAppException(400, "Колонка " + column + ": неверный формат даты/времени.");
            }
        }
    }

    private static LocalDate parseDate(String s, String column) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new MiniAppException(400, "Колонка " + column + ": неверный формат date.");
        }
    }
}
