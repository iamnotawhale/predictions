package zhigalin.predictions.service.event;

import zhigalin.predictions.dto.event.WeekDto;

import java.util.List;

public interface WeekService {

    WeekDto save(WeekDto weekDto);
    WeekDto findById(Long weekId);
    WeekDto findByName(String weekName);
    WeekDto findCurrentWeek();
    List<WeekDto> findAll();
}
