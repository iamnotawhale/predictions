package zhigalin.predictions.service.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.repository.event.HeadToHeadDao;

@Slf4j
@Service
public class HeadToHeadService {
    private final HeadToHeadDao dao;
    private final MatchService matchService;

    public HeadToHeadService(HeadToHeadDao dao, MatchService matchService) {
        this.dao = dao;
        this.matchService = matchService;
    }

    public void save(HeadToHead h2h) {
        dao.save(h2h);
    }

    public List<HeadToHead> findAllByTwoTeamsCode(String homeTeamCode, String awayTeamCode) {
        return dao.getH2hByTeamsCode(homeTeamCode, awayTeamCode).stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(7)
                .toList();
    }

    public List<HeadToHead> findAllByMatch(Match match) {
        return dao.getAllByTeamsIds(match.getHomeTeamId(), match.getAwayTeamId()).stream()
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(7)
                .toList();
    }

    public List<List<HeadToHead>> findAllByCurrentWeek() {
        List<List<HeadToHead>> listOfHeadToHeads = new ArrayList<>();
        List<Match> allByCurrentWeek = matchService.findAllByCurrentWeek();
        for (Match match : allByCurrentWeek) {
            listOfHeadToHeads.add(findAllByMatch(match));
        }
        return listOfHeadToHeads;
    }
}
