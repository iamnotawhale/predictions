package zhigalin.predictions.service.notification;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link SettingsStore} backed by the {@code app_setting} table (created on first use). */
@Component
public class JdbcSettingsStore implements SettingsStore {

    private final JdbcTemplate jdbc;

    public JdbcSettingsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_setting (
                    key   varchar(100) PRIMARY KEY,
                    value varchar(500) NOT NULL
                )
                """);
    }

    @Override
    public Optional<String> get(String key) {
        return jdbc.query("SELECT value FROM app_setting WHERE key = ?", (rs, i) -> rs.getString(1), key)
                .stream().findFirst();
    }

    @Override
    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO app_setting (key, value) VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """, key, value);
    }
}
