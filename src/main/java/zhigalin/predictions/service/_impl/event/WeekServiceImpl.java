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
        Week week = repository.getByWeekName(weekDto.getWeekName());
        if (week != null) {
            mapper.updateEntityFromDto(weekDto, week);
            return mapper.toDto(repository.save(week));
        }
        return mapper.toDto(repository.save(mapper.toEntity(weekDto)));
    }

    @Override
    public WeekDto getById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public WeekDto getByName(String weekName) {
        Week week = repository.getByWeekName(weekName);
        if (week != null) {
            return mapper.toDto(week);
        }
        return null;
    }

    @Override
    public WeekDto getCurrentWeek() {
        Week week = repository.getWeekByIsCurrentTrue();
        return mapper.toDto(week);
    }

    @Override
    public List<WeekDto> getAll() {
        List<Week> list = (List<Week>) repository.findAll();
        return list.stream().map(mapper::toDto).toList();
    }

    @Override
    public Long getCurrentWeekId() {
        return repository.getWeekByIsCurrentTrue().getId();
    }
}
