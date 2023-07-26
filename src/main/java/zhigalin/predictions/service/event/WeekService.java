package zhigalin.predictions.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekRepository;
import zhigalin.predictions.util.FieldsUpdater;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class WeekService {
    private final WeekRepository repository;

    public Week save(Week week) {
        Week weekFromDB = repository.findByNameAndSeasonId(week.getName(), week.getSeason().getId());
        if (weekFromDB != null) {
            return repository.save(FieldsUpdater.update(weekFromDB, week));
        }
        return repository.save(week);
    }

    public List<Week> findByCurrentSeason() {
        return repository.findBySeasonIsCurrent(true);
    }

    public Week findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Week findCurrentWeek() {
        return repository.findByIsCurrentTrue();
    }

    public List<Week> findAll() {
        return repository.findAll();
    }
}
