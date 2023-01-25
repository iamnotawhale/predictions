package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.SeasonDto;

public interface SeasonService {

    SeasonDto save(SeasonDto seasonDto);

    SeasonDto findById(Long seasonId);

    SeasonDto findByName(String seasonName);
}
