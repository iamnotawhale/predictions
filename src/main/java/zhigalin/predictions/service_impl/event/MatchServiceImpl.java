package zhigalin.predictions.service_impl.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.repository.event.MatchRepository;
import zhigalin.predictions.service.event.MatchService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl implements MatchService {

    private final MatchRepository repository;
    private final MatchMapper mapper;

    @Autowired
    public MatchServiceImpl(MatchRepository repository, MatchMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public MatchDto save(MatchDto matchDto) {
        Match match = repository.save(mapper.toEntity(matchDto));
        return mapper.toDto(match);
    }

    @Override
    public MatchDto getById(Long id) {
        return mapper.toDto(repository.findById(id).get());
    }

    @Override
    public List<MatchDto> getAllByWeekId(Long id) {
        List<Match> allByWeekId = repository.getAllByWeekIdOrderByMatchDate(id);
        return allByWeekId.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public MatchDto getByTeamIds(Long homeTeamId, Long awayTeamId) {
        Match match = repository.getMatchByHomeTeam_IdAndAwayTeam_Id(homeTeamId, awayTeamId);
        return mapper.toDto(match);
    }

    @Override
    public MatchDto getByTeamNames(String homeTeamName, String awayTeamName) {
        Match match = repository.getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(homeTeamName, awayTeamName);
        return mapper.toDto(match);
    }

    @Override
    public MatchDto getByTeamCodes(String home, String away) {
        return mapper.toDto(repository.getMatchByHomeTeam_CodeAndAwayTeam_Code(home, away));
    }

    @Override
    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        List<Integer> result = new ArrayList<>();
        Match match = repository.getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }
}
