package zhigalin.predictions.service.football;

import zhigalin.predictions.dto.football.StandingDto;

import java.util.List;

public interface StandingService {

    List<StandingDto> getAll();

    StandingDto save(StandingDto dto);

    StandingDto findByTeam_Id(Long id);

    List<StandingDto> currentTable();
}
