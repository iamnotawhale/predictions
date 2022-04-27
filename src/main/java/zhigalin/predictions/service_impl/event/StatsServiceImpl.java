package zhigalin.predictions.service_impl.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.StatsMapper;
import zhigalin.predictions.dto.event.StatsDto;
import zhigalin.predictions.model.event.AggregateStats;
import zhigalin.predictions.model.event.Stats;
import zhigalin.predictions.repository.event.StatsRepository;
import zhigalin.predictions.service.event.StatsService;

@Service
public class StatsServiceImpl implements StatsService {

    private final StatsRepository repository;

    private final StatsMapper mapper;

    @Autowired
    public StatsServiceImpl(StatsRepository repository, StatsMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    @Override
    public StatsDto save(StatsDto dto) {
        Stats stats = repository.getByMatchPublicIdAndTeam_Id(dto.getMatchPublicId(), dto.getTeam().getId());
        if (stats != null) {
            mapper.updateEntityFromDto(dto, stats);
            return mapper.toDto(repository.save(stats));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public StatsDto getByMatchPublicIdAndTeamId(Long matchPublicId, Long teamId) {
        Stats stats = repository.getByMatchPublicIdAndTeam_Id(matchPublicId, teamId);
        return mapper.toDto(stats);
    }

    @Override
    public AggregateStats getAvgStatsByTeamId(Long id) {
        return repository.getAvgStatsByTeamId(id);
    }
}
