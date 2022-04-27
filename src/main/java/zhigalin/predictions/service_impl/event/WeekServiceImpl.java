package zhigalin.predictions.service_impl.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.WeekMapper;
import zhigalin.predictions.dto.event.WeekDto;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekRepository;
import zhigalin.predictions.service.event.WeekService;

import java.util.List;

@Service
public class WeekServiceImpl implements WeekService {

    private final WeekRepository repository;
    private final WeekMapper mapper;

    @Autowired
    public WeekServiceImpl(WeekRepository repository, WeekMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

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
        return mapper.toDto(repository.findById(id).get());
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
    public WeekDto getByIsCurrent(Boolean b) {
        Week week = repository.getByIsCurrent(b);
        return mapper.toDto(week);
    }

    @Override
    public List<WeekDto> getAll() {
        List<Week> list = (List<Week>) repository.findAll();
        return list.stream().map(mapper::toDto).toList();
    }
}
