package zhigalin.predictions.recommender.model;

/**
 * Extended metrics (FootyStats + SoccerSTATS) stored as JSON in DB.
 */
public record FootyStatsExtendedMetrics(
        Double formBttsOverall,
        Double formBttsHome,
        Double formBttsAway,
        Double formCsOverall,
        Double formCsHome,
        Double formCsAway,
        Double formAvgGoalsOverall,
        Double formAvgGoalsHome,
        Double formAvgGoalsAway,
        Double formWinPctOverall,
        Double formWinPctHome,
        Double formWinPctAway,
        Double xgVsActual,
        Double xPts,
        Double actualPts,
        Double xPtsDelta,
        Double homeAdvantage,
        Double seasonBttsOverall,
        Double seasonBttsHome,
        Double seasonBttsAway,
        Double seasonCsOverall,
        Double seasonCsHome,
        Double seasonCsAway,
        Double ftsHome,
        Double ftsAway,
        Double drawPctOverall,
        Double drawPctHome,
        Double drawPctAway,
        Double seasonAvgTotalHome,
        Double seasonAvgTotalAway,
        Double seasonScoredPerMatch,
        Double seasonScoredHome,
        Double seasonScoredAway,
        Double seasonConcededPerMatch,
        Double seasonConcededHome,
        Double seasonConcededAway,
        Double over25Overall,
        Double over25Home,
        Double over25Away,
        Double under25Overall,
        Double under25Home,
        Double under25Away,
        Double homePpg,
        Double awayPpg,
        Double htPpg,
        Double secondHalfPpg,
        Double winningAtHtPct,
        Double losingAtHtPct,
        // SoccerSTATS (ss*) — game-state metrics not covered well by FootyStats
        Double ssScoredFirstPct,
        Double ssScoredFirstPpg,
        Double ssConcededFirstPct,
        Double ssConcededFirstPpg,
        Double ssLeadPct,
        Double ssLevelPct,
        Double ssTrailPct,
        Double ssEqualiserScoredPct,
        Double ssEqualiserConcededPct,
        Double ssOgsPct,
        Double ssOgcPct,
        Double ssAvgFirstGoalMin,
        Double ssFavouritePpg
) {
    public static FootyStatsExtendedMetrics empty() {
        return builder().build();
    }

    public FootyStatsExtendedMetrics merge(FootyStatsExtendedMetrics patch) {
        if (patch == null) {
            return this;
        }
        return new FootyStatsExtendedMetrics(
                coalesce(patch.formBttsOverall, formBttsOverall),
                coalesce(patch.formBttsHome, formBttsHome),
                coalesce(patch.formBttsAway, formBttsAway),
                coalesce(patch.formCsOverall, formCsOverall),
                coalesce(patch.formCsHome, formCsHome),
                coalesce(patch.formCsAway, formCsAway),
                coalesce(patch.formAvgGoalsOverall, formAvgGoalsOverall),
                coalesce(patch.formAvgGoalsHome, formAvgGoalsHome),
                coalesce(patch.formAvgGoalsAway, formAvgGoalsAway),
                coalesce(patch.formWinPctOverall, formWinPctOverall),
                coalesce(patch.formWinPctHome, formWinPctHome),
                coalesce(patch.formWinPctAway, formWinPctAway),
                coalesce(patch.xgVsActual, xgVsActual),
                coalesce(patch.xPts, xPts),
                coalesce(patch.actualPts, actualPts),
                coalesce(patch.xPtsDelta, xPtsDelta),
                coalesce(patch.homeAdvantage, homeAdvantage),
                coalesce(patch.seasonBttsOverall, seasonBttsOverall),
                coalesce(patch.seasonBttsHome, seasonBttsHome),
                coalesce(patch.seasonBttsAway, seasonBttsAway),
                coalesce(patch.seasonCsOverall, seasonCsOverall),
                coalesce(patch.seasonCsHome, seasonCsHome),
                coalesce(patch.seasonCsAway, seasonCsAway),
                coalesce(patch.ftsHome, ftsHome),
                coalesce(patch.ftsAway, ftsAway),
                coalesce(patch.drawPctOverall, drawPctOverall),
                coalesce(patch.drawPctHome, drawPctHome),
                coalesce(patch.drawPctAway, drawPctAway),
                coalesce(patch.seasonAvgTotalHome, seasonAvgTotalHome),
                coalesce(patch.seasonAvgTotalAway, seasonAvgTotalAway),
                coalesce(patch.seasonScoredPerMatch, seasonScoredPerMatch),
                coalesce(patch.seasonScoredHome, seasonScoredHome),
                coalesce(patch.seasonScoredAway, seasonScoredAway),
                coalesce(patch.seasonConcededPerMatch, seasonConcededPerMatch),
                coalesce(patch.seasonConcededHome, seasonConcededHome),
                coalesce(patch.seasonConcededAway, seasonConcededAway),
                coalesce(patch.over25Overall, over25Overall),
                coalesce(patch.over25Home, over25Home),
                coalesce(patch.over25Away, over25Away),
                coalesce(patch.under25Overall, under25Overall),
                coalesce(patch.under25Home, under25Home),
                coalesce(patch.under25Away, under25Away),
                coalesce(patch.homePpg, homePpg),
                coalesce(patch.awayPpg, awayPpg),
                coalesce(patch.htPpg, htPpg),
                coalesce(patch.secondHalfPpg, secondHalfPpg),
                coalesce(patch.winningAtHtPct, winningAtHtPct),
                coalesce(patch.losingAtHtPct, losingAtHtPct),
                coalesce(patch.ssScoredFirstPct, ssScoredFirstPct),
                coalesce(patch.ssScoredFirstPpg, ssScoredFirstPpg),
                coalesce(patch.ssConcededFirstPct, ssConcededFirstPct),
                coalesce(patch.ssConcededFirstPpg, ssConcededFirstPpg),
                coalesce(patch.ssLeadPct, ssLeadPct),
                coalesce(patch.ssLevelPct, ssLevelPct),
                coalesce(patch.ssTrailPct, ssTrailPct),
                coalesce(patch.ssEqualiserScoredPct, ssEqualiserScoredPct),
                coalesce(patch.ssEqualiserConcededPct, ssEqualiserConcededPct),
                coalesce(patch.ssOgsPct, ssOgsPct),
                coalesce(patch.ssOgcPct, ssOgcPct),
                coalesce(patch.ssAvgFirstGoalMin, ssAvgFirstGoalMin),
                coalesce(patch.ssFavouritePpg, ssFavouritePpg)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Double formBttsOverall;
        private Double formBttsHome;
        private Double formBttsAway;
        private Double formCsOverall;
        private Double formCsHome;
        private Double formCsAway;
        private Double formAvgGoalsOverall;
        private Double formAvgGoalsHome;
        private Double formAvgGoalsAway;
        private Double formWinPctOverall;
        private Double formWinPctHome;
        private Double formWinPctAway;
        private Double xgVsActual;
        private Double xPts;
        private Double actualPts;
        private Double xPtsDelta;
        private Double homeAdvantage;
        private Double seasonBttsOverall;
        private Double seasonBttsHome;
        private Double seasonBttsAway;
        private Double seasonCsOverall;
        private Double seasonCsHome;
        private Double seasonCsAway;
        private Double ftsHome;
        private Double ftsAway;
        private Double drawPctOverall;
        private Double drawPctHome;
        private Double drawPctAway;
        private Double seasonAvgTotalHome;
        private Double seasonAvgTotalAway;
        private Double seasonScoredPerMatch;
        private Double seasonScoredHome;
        private Double seasonScoredAway;
        private Double seasonConcededPerMatch;
        private Double seasonConcededHome;
        private Double seasonConcededAway;
        private Double over25Overall;
        private Double over25Home;
        private Double over25Away;
        private Double under25Overall;
        private Double under25Home;
        private Double under25Away;
        private Double homePpg;
        private Double awayPpg;
        private Double htPpg;
        private Double secondHalfPpg;
        private Double winningAtHtPct;
        private Double losingAtHtPct;
        private Double ssScoredFirstPct;
        private Double ssScoredFirstPpg;
        private Double ssConcededFirstPct;
        private Double ssConcededFirstPpg;
        private Double ssLeadPct;
        private Double ssLevelPct;
        private Double ssTrailPct;
        private Double ssEqualiserScoredPct;
        private Double ssEqualiserConcededPct;
        private Double ssOgsPct;
        private Double ssOgcPct;
        private Double ssAvgFirstGoalMin;
        private Double ssFavouritePpg;

        public Builder formBtts(Double overall, Double home, Double away) {
            formBttsOverall = overall;
            formBttsHome = home;
            formBttsAway = away;
            return this;
        }

        public Builder formCs(Double overall, Double home, Double away) {
            formCsOverall = overall;
            formCsHome = home;
            formCsAway = away;
            return this;
        }

        public Builder formAvgGoals(Double overall, Double home, Double away) {
            formAvgGoalsOverall = overall;
            formAvgGoalsHome = home;
            formAvgGoalsAway = away;
            return this;
        }

        public Builder formWinPct(Double overall, Double home, Double away) {
            formWinPctOverall = overall;
            formWinPctHome = home;
            formWinPctAway = away;
            return this;
        }

        public Builder seasonBtts(Double overall, Double home, Double away) {
            seasonBttsOverall = overall;
            seasonBttsHome = home;
            seasonBttsAway = away;
            return this;
        }

        public Builder seasonCs(Double overall, Double home, Double away) {
            seasonCsOverall = overall;
            seasonCsHome = home;
            seasonCsAway = away;
            return this;
        }

        public Builder drawPct(Double overall, Double home, Double away) {
            drawPctOverall = overall;
            drawPctHome = home;
            drawPctAway = away;
            return this;
        }

        public Builder seasonAvgTotal(Double home, Double away) {
            seasonAvgTotalHome = home;
            seasonAvgTotalAway = away;
            return this;
        }

        public Builder seasonScored(Double perMatch, Double home, Double away) {
            seasonScoredPerMatch = perMatch;
            seasonScoredHome = home;
            seasonScoredAway = away;
            return this;
        }

        public Builder seasonConceded(Double perMatch, Double home, Double away) {
            seasonConcededPerMatch = perMatch;
            seasonConcededHome = home;
            seasonConcededAway = away;
            return this;
        }

        public Builder over25(Double overall, Double home, Double away) {
            over25Overall = overall;
            over25Home = home;
            over25Away = away;
            return this;
        }

        public Builder under25(Double overall, Double home, Double away) {
            under25Overall = overall;
            under25Home = home;
            under25Away = away;
            return this;
        }

        public Builder xgVsActual(Double value) {
            xgVsActual = value;
            return this;
        }

        public Builder xPts(Double xPts, Double actualPts, Double xPtsDelta) {
            this.xPts = xPts;
            this.actualPts = actualPts;
            this.xPtsDelta = xPtsDelta;
            return this;
        }

        public Builder homeAdvantage(Double value) {
            homeAdvantage = value;
            return this;
        }

        public Builder ftsHome(Double value) {
            ftsHome = value;
            return this;
        }

        public Builder ftsAway(Double value) {
            ftsAway = value;
            return this;
        }

        public Builder homePpg(Double value) {
            homePpg = value;
            return this;
        }

        public Builder awayPpg(Double value) {
            awayPpg = value;
            return this;
        }

        public Builder htPpg(Double value) {
            htPpg = value;
            return this;
        }

        public Builder secondHalfPpg(Double value) {
            secondHalfPpg = value;
            return this;
        }

        public Builder winningAtHtPct(Double value) {
            winningAtHtPct = value;
            return this;
        }

        public Builder losingAtHtPct(Double value) {
            losingAtHtPct = value;
            return this;
        }

        public Builder ssScoredFirst(Double pct, Double ppg) {
            ssScoredFirstPct = pct;
            ssScoredFirstPpg = ppg;
            return this;
        }

        public Builder ssConcededFirst(Double pct, Double ppg) {
            ssConcededFirstPct = pct;
            ssConcededFirstPpg = ppg;
            return this;
        }

        public Builder ssLeadDurations(Double lead, Double level, Double trail) {
            ssLeadPct = lead;
            ssLevelPct = level;
            ssTrailPct = trail;
            return this;
        }

        public Builder ssEqualiserScoredPct(Double value) {
            ssEqualiserScoredPct = value;
            return this;
        }

        public Builder ssEqualiserConcededPct(Double value) {
            ssEqualiserConcededPct = value;
            return this;
        }

        public Builder ssFirstGoal(Double ogsPct, Double ogcPct, Double avgMin) {
            ssOgsPct = ogsPct;
            ssOgcPct = ogcPct;
            ssAvgFirstGoalMin = avgMin;
            return this;
        }

        public Builder ssFavouritePpg(Double value) {
            ssFavouritePpg = value;
            return this;
        }

        public FootyStatsExtendedMetrics build() {
            return new FootyStatsExtendedMetrics(
                    formBttsOverall, formBttsHome, formBttsAway,
                    formCsOverall, formCsHome, formCsAway,
                    formAvgGoalsOverall, formAvgGoalsHome, formAvgGoalsAway,
                    formWinPctOverall, formWinPctHome, formWinPctAway,
                    xgVsActual, xPts, actualPts, xPtsDelta, homeAdvantage,
                    seasonBttsOverall, seasonBttsHome, seasonBttsAway,
                    seasonCsOverall, seasonCsHome, seasonCsAway,
                    ftsHome, ftsAway,
                    drawPctOverall, drawPctHome, drawPctAway,
                    seasonAvgTotalHome, seasonAvgTotalAway,
                    seasonScoredPerMatch, seasonScoredHome, seasonScoredAway,
                    seasonConcededPerMatch, seasonConcededHome, seasonConcededAway,
                    over25Overall, over25Home, over25Away,
                    under25Overall, under25Home, under25Away,
                    homePpg, awayPpg, htPpg, secondHalfPpg,
                    winningAtHtPct, losingAtHtPct,
                    ssScoredFirstPct, ssScoredFirstPpg,
                    ssConcededFirstPct, ssConcededFirstPpg,
                    ssLeadPct, ssLevelPct, ssTrailPct,
                    ssEqualiserScoredPct, ssEqualiserConcededPct,
                    ssOgsPct, ssOgcPct, ssAvgFirstGoalMin,
                    ssFavouritePpg
            );
        }
    }

    private static Double coalesce(Double preferred, Double fallback) {
        return preferred != null ? preferred : fallback;
    }
}
