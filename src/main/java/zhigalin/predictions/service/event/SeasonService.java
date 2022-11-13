package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.SeasonDto;

public interface SeasonService {

    SeasonDto saveSeason(SeasonDto seasonDto);

    SeasonDto getById(Long id);

    SeasonDto getByName(String seasonName);
}
