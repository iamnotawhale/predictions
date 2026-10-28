package zhigalin.predictions.service.event;

import java.util.List;
import java.util.Objects;

import zhigalin.predictions.model.v2.Competitor;
import zhigalin.predictions.model.v2.Detail;

/** Score after 90 minutes (stoppage time included), rebuilt from ESPN scoreboard goal events. */
final class RegulationScore {

    private static final int REGULATION_MINUTES = 90;

    private RegulationScore() {
    }

    /**
     * Subtracts extra-time goals from the score after extra time.
     * Returns null when the goal list is missing or does not add up, so the caller can fall back.
     */
    static int[] fromDetails(List<Detail> details, Competitor home, Competitor away, int aetHome, int aetAway) {
        if (details == null || home == null || away == null) {
            return null;
        }
        int scoringPlays = 0;
        int extraHome = 0;
        int extraAway = 0;
        for (Detail d : details) {
            if (d == null || !Boolean.TRUE.equals(d.getScoringPlay()) || Boolean.TRUE.equals(d.getShootout())) {
                continue;
            }
            scoringPlays++;
            Integer minute = minuteOf(d.getClock());
            if (minute == null) {
                return null;
            }
            if (minute <= REGULATION_MINUTES) {
                continue;
            }
            if (Boolean.TRUE.equals(d.getOwnGoal())) {
                // Which side ESPN credits an own goal to is not reliable enough to subtract it.
                return null;
            }
            String teamId = d.getTeam() != null ? d.getTeam().getId() : null;
            if (isTeam(home, teamId)) {
                extraHome++;
            } else if (isTeam(away, teamId)) {
                extraAway++;
            } else {
                return null;
            }
        }
        if (scoringPlays != aetHome + aetAway) {
            return null;
        }
        int regHome = aetHome - extraHome;
        int regAway = aetAway - extraAway;
        if (regHome < 0 || regAway < 0) {
            return null;
        }
        return new int[] {regHome, regAway};
    }

    /** "90'+4'" → 90, "105'" → 105; stoppage time counts toward the half it belongs to. */
    static Integer minuteOf(Detail.Clock clock) {
        if (clock == null || clock.getDisplayValue() == null) {
            return null;
        }
        String v = clock.getDisplayValue().trim();
        int end = 0;
        while (end < v.length() && Character.isDigit(v.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        return Integer.parseInt(v.substring(0, end));
    }

    private static boolean isTeam(Competitor c, String teamId) {
        if (teamId == null) {
            return false;
        }
        return Objects.equals(c.getId(), teamId)
               || (c.getTeam() != null && Objects.equals(c.getTeam().getId(), teamId));
    }
}
