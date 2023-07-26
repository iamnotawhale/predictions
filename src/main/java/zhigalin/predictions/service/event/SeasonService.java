package zhigalin.predictions.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Season;
import zhigalin.predictions.repository.event.SeasonRepository;
import zhigalin.predictions.util.FieldsUpdater;

@RequiredArgsConstructor
@Service
@Slf4j
public class SeasonService {
    private final SeasonRepository repository;

    public Season save(Season season) {
        Season seasonFromDB = repository.findByName(season.getName());
        if (seasonFromDB != null) {
            return repository.save(FieldsUpdater.update(seasonFromDB, season));
        }
        return repository.save(season);
    }

    public Season findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Season findByName(String name) {
        return repository.findByName(name);
    }

    public Season currentSeason() {
        return repository.findByIsCurrentTrue();
    }
}
