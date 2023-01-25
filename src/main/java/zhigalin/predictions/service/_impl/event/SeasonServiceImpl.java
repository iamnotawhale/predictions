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

    private final SeasonRepository seasonRepository;
    private final SeasonMapper mapper;

    @Override
    public SeasonDto save(SeasonDto seasonDto) {
        Season season = seasonRepository.findBySeasonName(seasonDto.getSeasonName());
        if (season != null) {
            mapper.updateEntityFromDto(seasonDto, season);
            return mapper.toDto(seasonRepository.save(season));
        }
        return mapper.toDto(seasonRepository.save(mapper.toEntity(seasonDto)));
    }

    @Override
    public SeasonDto findById(Long seasonId) {
        return mapper.toDto(seasonRepository.findById(seasonId).orElse(null));
    }

    @Override
    public SeasonDto findByName(String seasonName) {
        Season season = seasonRepository.findBySeasonName(seasonName);
        if (season != null) {
            return mapper.toDto(season);
        }
        return null;
    }
}
