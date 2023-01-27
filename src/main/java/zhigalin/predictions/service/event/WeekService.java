package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.WeekDto;

import java.util.List;

public interface WeekService {

    WeekDto save(WeekDto dto);
    WeekDto findById(Long id);
    WeekDto findByName(String name);
    WeekDto findCurrentWeek();
    List<WeekDto> findAll();
}
