package zhigalin.predictions.service_impl.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.SeasonMapper;
import zhigalin.predictions.dto.event.SeasonDto;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.repository.event.SeasonRepository;
import zhigalin.predictions.service.event.SeasonService;

@Service
public class SeasonServiceImpl implements SeasonService {

    private final SeasonRepository seasonRepository;
    private final SeasonMapper mapper;

    @Autowired
    public SeasonServiceImpl(SeasonRepository seasonRepository, SeasonMapper mapper) {
        this.seasonRepository = seasonRepository;
        this.mapper = mapper;
    }

    @Override
    public SeasonDto saveSeason(SeasonDto seasonDto) {
        Season savedSeason = seasonRepository.save(mapper.toEntity(seasonDto));
        return mapper.toDto(savedSeason);
    }

    @Override
    public SeasonDto getById(Long id) {
        return mapper.toDto(seasonRepository.findById(id).get());
    }

    @Override
    public SeasonDto getByName(String seasonName) {
        Season season = seasonRepository.getBySeasonName(seasonName);
        if(season != null) {
            return mapper.toDto(season);
        }
        return null;
    }
}
