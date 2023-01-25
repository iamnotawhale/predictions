package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekRepository;
import zhigalin.predictions.service.event.WeekService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class WeekServiceImpl implements WeekService {

    private final WeekRepository repository;
    private final WeekMapper mapper;

    @Override
    public WeekDto save(WeekDto weekDto) {
        Week week = repository.findByWeekName(weekDto.getWeekName());
        if (week != null) {
            mapper.updateEntityFromDto(weekDto, week);
            return mapper.toDto(repository.save(week));
        }
        return mapper.toDto(repository.save(mapper.toEntity(weekDto)));
    }

    @Override
    public WeekDto findById(Long weekId) {
        return mapper.toDto(repository.findById(weekId).orElse(null));
    }

    @Override
    public WeekDto findByName(String weekName) {
        Week week = repository.findByWeekName(weekName);
        if (week != null) {
            return mapper.toDto(week);
        }
        return null;
    }

    @Override
    public WeekDto findCurrentWeek() {
        return mapper.toDto(repository.findWeekByIsCurrentTrue());
    }

    @Override
    public List<WeekDto> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
