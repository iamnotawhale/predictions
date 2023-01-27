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
    public WeekDto save(WeekDto dto) {
        Week week = repository.findByName(dto.getName());
        if (week != null) {
            mapper.updateEntityFromDto(dto, week);
            return mapper.toDto(repository.save(week));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public WeekDto findById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public WeekDto findByName(String name) {
        Week week = repository.findByName(name);
        if (week != null) {
            return mapper.toDto(week);
        }
        return null;
    }

    @Override
    public WeekDto findCurrentWeek() {
        return mapper.toDto(repository.findByIsCurrentTrue());
    }

    @Override
    public List<WeekDto> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
