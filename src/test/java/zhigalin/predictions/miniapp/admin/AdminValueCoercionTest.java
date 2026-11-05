package zhigalin.predictions.miniapp.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminColumnItem;

class AdminValueCoercionTest {

    @Test
    void escapeLikePattern() {
        assertEquals("100\\%\\_test\\\\end", AdminValueCoercion.escapeLikePattern("100%_test\\end"));
    }

    @Test
    void coerceIntegerAndTimestamp() {
        AdminColumnItem intCol = new AdminColumnItem("n", "integer", false, null);
        assertEquals(42L, AdminValueCoercion.coerceForWrite(intCol, "42"));

        AdminColumnItem ts = new AdminColumnItem("t", "timestamp without time zone", true, null);
        assertEquals(
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-15T12:00:00Z")),
                AdminValueCoercion.coerceForWrite(ts, "2026-01-15T12:00:00Z")
        );
    }

    @Test
    void blankNullableBecomesNull() {
        AdminColumnItem col = new AdminColumnItem("x", "character varying", true, null);
        assertNull(AdminValueCoercion.coerceForWrite(col, "   "));
    }

    @Test
    void invalidIntegerThrows() {
        AdminColumnItem col = new AdminColumnItem("n", "integer", false, null);
        assertThrows(MiniAppException.class, () -> AdminValueCoercion.coerceForWrite(col, "abc"));
    }
}
