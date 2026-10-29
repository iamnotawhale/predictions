package zhigalin.predictions.config;

import javax.sql.DataSource;
import jakarta.annotation.Priority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
public class DBConfig {

    @Priority(1)
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.driverClassName:org.postgresql.Driver}") String driverClassName,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Priority(2)
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        renameLegacyCupSchema(dataSource);
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("tablesInit.sql"));

        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    private static void renameLegacyCupSchema(DataSource dataSource) {
        new JdbcTemplate(dataSource).execute("""
                DO $$
                DECLARE
                    c RECORD;
                BEGIN
                    IF to_regclass('public.bonus_match') IS NOT NULL AND to_regclass('public.cup_match') IS NULL THEN
                        ALTER TABLE bonus_match RENAME TO cup_match;
                    END IF;
                    IF EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_name = 'predict' AND column_name = 'bonus_match_id')
                       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_name = 'predict' AND column_name = 'cup_match_id') THEN
                        ALTER TABLE predict RENAME COLUMN bonus_match_id TO cup_match_id;
                    END IF;
                    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'unique_bonus_match_espn')
                       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'unique_cup_match_espn') THEN
                        ALTER TABLE cup_match RENAME CONSTRAINT unique_bonus_match_espn TO unique_cup_match_espn;
                    END IF;
                    FOR c IN SELECT conname FROM pg_constraint
                             WHERE conrelid = to_regclass('public.cup_match') AND conname LIKE 'bonus\\_match%' LOOP
                        IF NOT EXISTS (SELECT 1 FROM pg_constraint
                                       WHERE conname = 'cup_match' || substr(c.conname, length('bonus_match') + 1)) THEN
                            EXECUTE format('ALTER TABLE cup_match RENAME CONSTRAINT %I TO %I',
                                           c.conname, 'cup_match' || substr(c.conname, length('bonus_match') + 1));
                        END IF;
                    END LOOP;
                    IF to_regclass('public.unique_predict_bonus') IS NOT NULL AND to_regclass('public.unique_predict_cup') IS NULL THEN
                        ALTER INDEX unique_predict_bonus RENAME TO unique_predict_cup;
                    END IF;
                    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'predict_match_xor_bonus')
                       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'predict_match_xor_cup') THEN
                        ALTER TABLE predict RENAME CONSTRAINT predict_match_xor_bonus TO predict_match_xor_cup;
                    END IF;
                END $$;
                """);
    }
}
