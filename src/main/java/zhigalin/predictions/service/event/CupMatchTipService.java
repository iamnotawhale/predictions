package zhigalin.predictions.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.recommender.CupOddsModel;
import zhigalin.predictions.recommender.model.TipTrackRecord;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.predict.ScoringMode;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.event.CupMatchTipDao;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.util.AppTimeZones;

@Service
public class CupMatchTipService {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final int HORIZON_DAYS = 3;
    private static final Duration REGULAR = Duration.ofMinutes(30);
    private static final Duration NEAR_KICKOFF = Duration.ofMinutes(10);

    private final CupMatchDao cupMatchDao;
    private final CupMatchTipDao tipDao;
    private final ApiClient apiClient;

    public CupMatchTipService(CupMatchDao cupMatchDao, CupMatchTipDao tipDao, ApiClient apiClient) {
        this.cupMatchDao = cupMatchDao;
        this.tipDao = tipDao;
        this.apiClient = apiClient;
    }

    public void refreshUpcoming() {
        LocalDateTime now = LocalDateTime.now(AppTimeZones.DISPLAY);
        LocalDateTime until = now.plusDays(HORIZON_DAYS);
        List<CupMatch> upcoming = new ArrayList<>();
        for (CupMatch match : cupMatchDao.findAllFrom(now.toLocalDate())) {
            LocalDateTime kickoff = match.getLocalDateTime();
            if (kickoff != null && kickoff.isAfter(now) && !kickoff.isAfter(until)) {
                upcoming.add(match);
            }
        }
        Map<Integer, LocalDateTime> updatedAt = tipDao.updatedAtByCupMatchIds(
                upcoming.stream().map(CupMatch::getPublicId).toList());
        for (CupMatch match : upcoming) {
            LocalDateTime last = updatedAt.get(match.getPublicId());
            Duration interval = match.getLocalDateTime().isBefore(now.plusHours(1)) ? NEAR_KICKOFF : REGULAR;
            if (last != null && last.isAfter(now.minus(interval))) {
                continue;
            }
            try {
                refresh(match, now);
            } catch (Exception e) {
                log.warn("Cup tip refresh failed for {}: {}", match.getPublicId(), e.getMessage());
            }
        }
    }

    public Map<Integer, CupOddsModel.Odds> oddsFor(Collection<Integer> cupMatchIds) {
        return tipDao.oddsByCupMatchIds(cupMatchIds);
    }

    public Optional<CupOddsModel.Tip> tipFor(int cupMatchId) {
        return tipDao.findByCupMatchId(cupMatchId);
    }

    public TipTrackRecord cupTrackRecord() {
        return computeTrackRecord(tipDao.findFinishedTipResults());
    }

    static TipTrackRecord computeTrackRecord(List<int[]> rows) {
        int total = 0;
        int exact = 0;
        for (int[] r : rows) {
            int points = PredictionService.computePoints(ScoringMode.CUP, r[2], r[3], r[0], r[1]);
            total += points;
            if (points == 2) {
                exact++;
            }
        }
        double avg = rows.isEmpty() ? 0.0 : (double) total / rows.size();
        return new TipTrackRecord(rows.size(), avg, exact);
    }

    public Map<Integer, int[]> kickoffScoresByCupMatchIds(Collection<Integer> cupMatchIds) {
        return tipDao.kickoffScoresByCupMatchIds(cupMatchIds);
    }

    private void refresh(CupMatch match, LocalDateTime now) {
        JsonNode root = apiClient.fetchEspnSummary(match.getEspnId(), match.getCompetition());
        Optional<CupOddsModel.Odds> odds = CupOddsModel.fromEspnSummary(root);
        if (odds.isEmpty()) {
            return;
        }
        Instant kickoff = match.getLocalDateTime().atZone(AppTimeZones.DISPLAY).toInstant();
        CupOddsModel.Form form = CupOddsModel.formFromEspnSummary(root, kickoff);
        CupOddsModel.recommend(odds.get(), form).ifPresent(tip -> tipDao.upsert(
                new CupMatchTipDao.Tip(match.getPublicId(), tip, odds.get(), form, now)));
    }
}
