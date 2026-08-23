package zhigalin.predictions.service.odds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.event.MatchOdds;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.v2.Competition;
import zhigalin.predictions.model.v2.Event;
import zhigalin.predictions.model.v2.OddV2;
import zhigalin.predictions.model.v2.Scoreboard;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.repository.event.MatchDao;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.util.TeamCodeMapper;

@Service
public class OddsService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final long REFRESH_INTERVAL_MS = 60_000L;

    private final PanicSender panicSender;
    private final ObjectMapper mapper;
    private final MatchDao matchDao;
    private volatile long lastRefreshAtMs = 0L;

    /** In-memory cache; persisted odds are loaded from DB on startup and after fetch. */
    private final Map<Integer, Odd> oddsCache = new HashMap<>();

    public OddsService(PanicSender panicSender, ObjectMapper objectMapper, MatchDao matchDao) {
        this.panicSender = panicSender;
        this.mapper = objectMapper;
        this.matchDao = matchDao;
    }

    @PostConstruct
    void loadPersistedOdds() {
        Map<Integer, MatchOdds> persisted = matchDao.findAllOdds();
        persisted.forEach((publicId, stored) -> oddsCache.put(publicId, toOdd(stored)));
        if (!persisted.isEmpty()) {
            log.info("Loaded {} persisted match odds from database", persisted.size());
        }
    }

    public Odd getOdd(int matchPublicId) {
        Odd cached = oddsCache.get(matchPublicId);
        if (cached != null) {
            return cached;
        }
        MatchOdds stored = matchDao.findOdds(matchPublicId);
        if (stored == null) {
            return null;
        }
        Odd odd = toOdd(stored);
        oddsCache.put(matchPublicId, odd);
        return odd;
    }

    public void oddsInit2(List<Match> matches) {
        try {
            HttpResponse<String> response = Unirest.get("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard")
                    .asString();

            Scoreboard scoreboard = mapper.readValue(response.getBody(), Scoreboard.class);
            List<Event> events = scoreboard.getEvents();

            for (Event event : events) {
                String state = event.getStatus().getType().getState();
                if (state.equals("pre")) {
                    String[] teams = event.getShortName().split(" @ ");

                    String home = TeamCodeMapper.toInternalCode(teams[1]);
                    String away = TeamCodeMapper.toInternalCode(teams[0]);

                    Match match = matches.stream()
                            .filter(m -> {
                                Team homeTeam = DaoUtil.TEAMS.get(m.getHomeTeamId());
                                Team awayTeam = DaoUtil.TEAMS.get(m.getAwayTeamId());
                                return homeTeam.getCode().equalsIgnoreCase(home) &&
                                       awayTeam.getCode().equalsIgnoreCase(away);
                            })
                            .findFirst()
                            .orElse(null);

                    if (match != null) {
                        Competition competition = event.getCompetitions().getFirst();
                        if (competition.getOdds() != null && !competition.getOdds().isEmpty()) {
                            OddV2 oddV2 = competition.getOdds().getFirst();
                            Odd odd = extractOdd(oddV2);
                            if (odd != null) {
                                storeOdd(match.getPublicId(), odd);
                                log.info("Odds loaded for {}-{}: {} / {} / {}", home, away, odd.home(), odd.draw(), odd.away());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            String message = "Failed to retrieve odds";
            panicSender.sendPanic(message, e);
        }
    }

    /**
     * Refresh odds at most once per minute to avoid hammering ESPN
     * on frequent Mini App polling.
     */
    public synchronized void ensureFresh(List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshAtMs < REFRESH_INTERVAL_MS) {
            return;
        }
        oddsInit2(matches);
        lastRefreshAtMs = now;
    }

    private void storeOdd(int publicId, Odd odd) {
        oddsCache.put(publicId, odd);
        matchDao.saveOdds(publicId, odd.home(), odd.draw(), odd.away());
    }

    private static Odd toOdd(MatchOdds stored) {
        return new Odd(stored.home(), stored.draw(), stored.away());
    }

    private Odd extractOdd(OddV2 oddV2) {
        OddV2.Moneyline ml = oddV2.getMoneyline();
        if (ml != null
            && ml.getHome() != null && ml.getHome().getClose() != null
            && ml.getDraw() != null && ml.getDraw().getClose() != null
            && ml.getAway() != null && ml.getAway().getClose() != null) {
            Double homeDecimal = americanToDecimal(ml.getHome().getClose().getOdds());
            Double drawDecimal = americanToDecimal(ml.getDraw().getClose().getOdds());
            Double awayDecimal = americanToDecimal(ml.getAway().getClose().getOdds());
            if (homeDecimal != null && drawDecimal != null && awayDecimal != null) {
                return new Odd(round(homeDecimal), round(drawDecimal), round(awayDecimal));
            }
        }

        if (oddV2.getHomeTeamOdds() != null && oddV2.getDrawOdds() != null && oddV2.getAwayTeamOdds() != null
            && oddV2.getHomeTeamOdds().getValue() != null && oddV2.getDrawOdds().getValue() != null && oddV2.getAwayTeamOdds().getValue() != null) {
            return new Odd(
                    round(oddV2.getHomeTeamOdds().getValue()),
                    round(oddV2.getDrawOdds().getValue()),
                    round(oddV2.getAwayTeamOdds().getValue())
            );
        }

        return null;
    }

    static Double americanToDecimal(String americanOdds) {
        if (americanOdds == null || americanOdds.isBlank()) return null;
        try {
            int odds = Integer.parseInt(americanOdds.replace("+", ""));
            if (odds >= 0) {
                return 1.0 + (double) odds / 100.0;
            } else {
                return 1.0 + 100.0 / Math.abs(odds);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Odd(double home, double draw, double away) {
    }

    public static double round(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
