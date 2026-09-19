package zhigalin.predictions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import zhigalin.predictions.util.EspnTeamLogos;

@Component("espnTeamLogosMigration")
public class EspnTeamLogosMigration {

    private static final Logger log = LoggerFactory.getLogger("server");

    public EspnTeamLogosMigration(JdbcTemplate jdbcTemplate) {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate);
        int updated = 0;
        for (var e : EspnTeamLogos.allEspnIds().entrySet()) {
            String url = EspnTeamLogos.logoUrl(e.getKey());
            int n = named.update(
                    """
                            UPDATE teams
                            SET logo = :logo
                            WHERE code = :code
                              AND (logo IS DISTINCT FROM :logo)
                            """,
                    new MapSqlParameterSource()
                            .addValue("code", e.getKey())
                            .addValue("logo", url)
            );
            updated += n;
        }
        log.info("ESPN team logos synced: {} rows updated", updated);
    }
}
