package zhigalin.predictions.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.repository.event.HeadToHeadRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
@Slf4j
public class HeadToHeadService {
    private final HeadToHeadRepository repository;
    private final MatchService matchService;

    public HeadToHead save(HeadToHead h2h) {
        HeadToHead fromBD = repository.findByHomeTeamPublicIdAndAwayTeamPublicIdAndLocalDateTime(
                h2h.getHomeTeam().getPublicId(),
                h2h.getAwayTeam().getPublicId(),
                h2h.getLocalDateTime()
        );
        return fromBD != null ? null : repository.save(h2h);
    }

    public List<HeadToHead> findAllByTwoTeamsCode(String homeTeamCode, String awayTeamCode) {
        List<HeadToHead> list1 = repository.findAllByHomeTeamCodeAndAwayTeamCode(homeTeamCode, awayTeamCode);
        List<HeadToHead> list2 = repository.findAllByHomeTeamCodeAndAwayTeamCode(awayTeamCode, homeTeamCode);

        return Stream.concat(list1.stream(), list2.stream())
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(7)
                .toList();
    }

    public List<HeadToHead> findAllByMatch(Match match) {
        return findAllByTwoTeamsCode(match.getHomeTeam().getCode(), match.getAwayTeam().getCode());
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
