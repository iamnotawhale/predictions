package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.StandingDto;

import java.util.List;
import java.util.Map;

public interface StandingService {
    StandingDto save(StandingDto dto);
    List<StandingDto> findAll();
    Map<Long, Integer> getPlaces();
}
