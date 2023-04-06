package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.SeasonDto;

public interface SeasonService {
    SeasonDto save(SeasonDto dto);
    SeasonDto findById(Long id);
}
