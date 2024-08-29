package zhigalin.predictions.service.event;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.event.Week;
import zhigalin.predictions.repository.event.WeekDao;

@RequiredArgsConstructor
@Service
public class WeekService {

    private final WeekDao weekDao;

    public Week save(Week week) {
        return weekDao.save(week);
    }

    public void updateCurrent() {
        weekDao.updateCurrentWeek();
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
