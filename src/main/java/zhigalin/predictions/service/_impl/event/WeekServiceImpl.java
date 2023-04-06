package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekRepository;
import zhigalin.predictions.service.event.WeekService;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
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
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public WeekDto findCurrentWeek() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findByIsCurrentTrue());
    }

    @Override
    public List<WeekDto> findAll() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
