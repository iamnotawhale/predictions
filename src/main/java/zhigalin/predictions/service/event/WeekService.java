package zhigalin.predictions.service.event;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekDao;

@RequiredArgsConstructor
@Service
@Slf4j
public class WeekService {
    @Value("${season}")
    private int season;
    private final WeekDao weekDao;

    public Week save(Week week) {
        return weekDao.save(week);
    }

    public void updateCurrent(Week week, Boolean isCurrent) {
        Week w = weekDao.findByNameAndSeasonId(week.getName(), season);
        if (w != null) {
            weekDao.updateCurrentWeek(week.getId(), isCurrent);
        }
    }

    public Week findById(int id) {
        return weekDao.findById(id);
    }

    public Week findByMatchId(int matchId) {
        return weekDao.findByMatchId(matchId);
    }

    public Week findCurrentWeek() {
        return weekDao.findByIsCurrentTrue();
    }

    public List<Week> findAll() {
        return weekDao.findAll();
    }

}
