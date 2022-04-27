package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.StatsDto;
import zhigalin.predictions.model.event.AggregateStats;

public interface StatsService {

    StatsDto save(StatsDto dto);
    StatsDto getByMatchPublicIdAndTeamId(Long matchPublicId, Long teamId);

    AggregateStats getAvgStatsByTeamId(Long id);
}
