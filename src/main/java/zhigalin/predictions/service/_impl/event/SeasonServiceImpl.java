package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.repository.event.SeasonRepository;
import zhigalin.predictions.service.event.SeasonService;

@RequiredArgsConstructor
@Service
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
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public SeasonDto findByName(String name) {
        Season season = repository.findByName(name);
        if (season != null) {
            return mapper.toDto(season);
        }
        return null;
    }
}
