package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.StandingDto;

import java.util.List;

public interface StandingService {

    StandingDto save(StandingDto dto);

    List<StandingDto> findAll();
}
