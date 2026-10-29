\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE n bigint;
BEGIN
    IF to_regclass('public.bonus_match') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM bonus_match' INTO n;
        IF n <> 0 THEN
            RAISE EXCEPTION 'bonus_match is not empty, reconcile manually';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'predict' AND column_name = 'bonus_match_id') THEN
        RAISE EXCEPTION 'predict.bonus_match_id exists, reconcile manually';
    END IF;
END $$;
DROP TABLE IF EXISTS bonus_match;
ALTER TABLE cup_match RENAME TO bonus_match;
ALTER TABLE bonus_match RENAME CONSTRAINT unique_cup_match_espn TO unique_bonus_match_espn;
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = to_regclass('public.bonus_match') AND conname LIKE 'cup\_match%' LOOP
        EXECUTE format('ALTER TABLE bonus_match RENAME CONSTRAINT %I TO %I',
                       c.conname, 'bonus_match' || substr(c.conname, length('cup_match') + 1));
    END LOOP;
END $$;
ALTER TABLE predict RENAME COLUMN cup_match_id TO bonus_match_id;
ALTER INDEX unique_predict_cup RENAME TO unique_predict_bonus;
ALTER TABLE predict RENAME CONSTRAINT predict_match_xor_cup TO predict_match_xor_bonus;
COMMIT;
