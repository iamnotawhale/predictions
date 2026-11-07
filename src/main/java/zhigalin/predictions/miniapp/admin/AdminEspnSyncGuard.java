package zhigalin.predictions.miniapp.admin;

import java.time.LocalDate;
import org.springframework.stereotype.Component;
import zhigalin.predictions.model.v2.Event;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.service.api.EspnScoreboardClient;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.TeamCodeMapper;

@Component
public class AdminEspnSyncGuard {

    private static final int SCORE_CORRECTION_WINDOW_HOURS = 2;

    private final MatchService matchService;
    private final EspnScoreboardClient espnScoreboardClient;

    public AdminEspnSyncGuard(MatchService matchService, EspnScoreboardClient espnScoreboardClient) {
        this.matchService = matchService;
        this.espnScoreboardClient = espnScoreboardClient;
    }

    public boolean isEplMatchEditBlocked(Match match) {
        if (match == null || !isSyncBusy()) {
            return false;
        }
        Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
        Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
        if (home == null || away == null) {
            return false;
        }
        Scoreboard scoreboard = espnScoreboardClient.fetchScoreboard();
        return isOnScoreboard(scoreboard, home.getCode(), away.getCode());
    }

    public boolean isCupMatchEditBlocked(CupMatch cup) {
        if (cup == null || !isSyncBusy()) {
            return false;
        }
        if (cup.getStartsAt() == null) {
            return false;
        }
        LocalDate today = LocalDate.now(AppTimeZones.DISPLAY);
        if (!cup.getStartsAt().toLocalDate().equals(today)) {
            return false;
        }
        String league = cup.getCompetition();
        if (league == null || league.isBlank()) {
            return true;
        }
        Scoreboard scoreboard = espnScoreboardClient.fetchScoreboard(league);
        String home = cup.getHomeEspnCode() != null ? cup.getHomeEspnCode() : "?";
        String away = cup.getAwayEspnCode() != null ? cup.getAwayEspnCode() : "?";
        return isOnScoreboard(scoreboard, home, away);
    }

    private boolean isSyncBusy() {
        return !matchService.findOnlineMatches().isEmpty()
                || matchService.hasPostponedMatches()
                || matchService.hasRecentlyFinishedMatches(SCORE_CORRECTION_WINDOW_HOURS);
    }

    private static boolean isOnScoreboard(Scoreboard scoreboard, String homeCode, String awayCode) {
        if (scoreboard == null || scoreboard.getEvents() == null) {
            return false;
        }
        String wantHome = TeamCodeMapper.toInternalCode(homeCode);
        String wantAway = TeamCodeMapper.toInternalCode(awayCode);
        for (Event event : scoreboard.getEvents()) {
            if (event.getShortName() == null || !event.getShortName().contains(" @ ")) {
                continue;
            }
            String[] teams = event.getShortName().split(" @ ");
            if (teams.length != 2) {
                continue;
            }
            String eventHome = TeamCodeMapper.toInternalCode(teams[1]);
            String eventAway = TeamCodeMapper.toInternalCode(teams[0]);
            if (wantHome.equals(eventHome) && wantAway.equals(eventAway)) {
                return true;
            }
        }
        return false;
    }
}
