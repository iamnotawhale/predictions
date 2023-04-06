package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.repository.event.SeasonRepository;
import zhigalin.predictions.service.event.SeasonService;

@RequiredArgsConstructor
@Service
@Slf4j
public class SeasonServiceImpl implements SeasonService {
    private final SeasonRepository repository;
    private final SeasonMapper mapper;

    @Override
    public SeasonDto save(SeasonDto dto) {
        Season season = repository.findByName(dto.getName());
        if (season != null) {
            mapper.updateEntityFromDto(dto, season);
            return mapper.toDto(repository.save(season));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public SeasonDto findById(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findById(id).orElse(null));
    }
}
