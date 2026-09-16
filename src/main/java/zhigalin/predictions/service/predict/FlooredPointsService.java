package zhigalin.predictions.service.predict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import zhigalin.predictions.repository.predict.PointEventDao;

/**
 * Loads ordered point events and applies {@link FlooredPointsCalculator}.
 */
@Service
public class FlooredPointsService {

    public record OrderedPointsRow(
            String login,
            int userId,
            java.time.LocalDateTime sortTime,
            long sequence,
            int weekId,
            Integer points,
            boolean cup
    ) {
    }

    private final PointEventDao pointEventDao;

    public FlooredPointsService(PointEventDao pointEventDao) {
        this.pointEventDao = pointEventDao;
    }

    public Map<String, Integer> flooredSeasonTotals() {
        return floorByLogin(pointEventDao.findSeasonFinishedEvents());
    }

    public Map<String, Integer> flooredWeekTotals(int weekId) {
        return floorByLogin(pointEventDao.findWeekFinishedEvents(weekId));
    }

    /**
     * Cumulative floored total after each season week.
     * Cup finishes update the latest EPL week snapshot so midweek cups appear on the chart.
     */
    public Map<String, Map<Integer, Integer>> flooredCumulativeByWeek() {
        List<PointEvent> events = toEvents(pointEventDao.findSeasonFinishedEvents());
        events.sort(PointEvent.BY_TIME);
        Map<String, List<PointEvent>> byLogin = new LinkedHashMap<>();
        for (PointEvent e : events) {
            byLogin.computeIfAbsent(e.login(), k -> new ArrayList<>()).add(e);
        }
        Map<String, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<PointEvent>> entry : byLogin.entrySet()) {
            result.put(entry.getKey(), cumulativeWeekSnapshots(entry.getValue()));
        }
        return result;
    }

    /**
     * Running floored total snapped after each EPL week; cups fold into the active week.
     */
    static Map<Integer, Integer> cumulativeWeekSnapshots(List<PointEvent> events) {
        Map<Integer, Integer> weekSnap = new LinkedHashMap<>();
        if (events == null || events.isEmpty()) {
            return weekSnap;
        }
        int running = 0;
        int activeWeek = 0;
        for (PointEvent e : events) {
            if (e == null) {
                continue;
            }
            running = Math.max(0, running + e.rawPoints());
            if (!e.cup() && e.weekId() > 0) {
                activeWeek = e.weekId();
                weekSnap.put(activeWeek, running);
            } else if (e.cup() && activeWeek > 0) {
                weekSnap.put(activeWeek, running);
            }
        }
        return weekSnap;
    }

    public Map<String, Integer> floorByLogin(List<OrderedPointsRow> rows) {
        List<PointEvent> events = toEvents(rows);
        events.sort(PointEvent.BY_TIME);
        Map<String, List<Integer>> points = new LinkedHashMap<>();
        for (PointEvent e : events) {
            points.computeIfAbsent(e.login(), k -> new ArrayList<>()).add(e.rawPoints());
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : points.entrySet()) {
            totals.put(e.getKey(), FlooredPointsCalculator.applyFloor(e.getValue()));
        }
        return totals;
    }

    private static List<PointEvent> toEvents(List<OrderedPointsRow> rows) {
        List<PointEvent> events = new ArrayList<>();
        if (rows == null) {
            return events;
        }
        for (OrderedPointsRow row : rows) {
            if (row == null || row.login() == null) {
                continue;
            }
            events.add(new PointEvent(
                    row.login(),
                    row.userId(),
                    row.sortTime(),
                    row.sequence(),
                    row.weekId(),
                    row.points() != null ? row.points() : 0,
                    row.cup()
            ));
        }
        return events;
    }

    public static Map<String, Integer> floorProvisional(Map<String, List<Integer>> orderedByLogin) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : orderedByLogin.entrySet()) {
            out.put(e.getKey(), FlooredPointsCalculator.applyFloor(e.getValue()));
        }
        return out;
    }
}
