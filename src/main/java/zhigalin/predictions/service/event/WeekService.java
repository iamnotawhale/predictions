package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.WeekDto;

public interface WeekService {
    WeekDto save(WeekDto weekDto);

    WeekDto getById(Long id);

    WeekDto getByName(String weekName);

    WeekDto getByIsCurrent(Boolean b);
}
