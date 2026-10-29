package zhigalin.predictions.config;

import org.springframework.jdbc.core.JdbcTemplate;

final class CupSchemaRename {

    private CupSchemaRename() {
    }

    static void apply(JdbcTemplate jdbc) {
        jdbc.execute("""
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
