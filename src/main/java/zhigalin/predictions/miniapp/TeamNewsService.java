package zhigalin.predictions.miniapp;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchNewsItem;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.AppTimeZones;
import zhigalin.predictions.util.DaoUtil;

/** Sports.ru RSS team news with a short in-memory cache. */
@Service
public class TeamNewsService {
    private static final Logger log = LoggerFactory.getLogger("server");

    private static final DateTimeFormatter NEWS_TS = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final String SPORTS_RU_TEAM_RSS = "https://www.sports.ru/stat/export/rss/taglenta.xml?id=";
    private static final long TEAM_NEWS_CACHE_MS = 120_000L;
    private static final Map<String, Integer> TEAM_NEWS_TAG_IDS = Map.ofEntries(
            Map.entry("ARS", 1685202),
            Map.entry("AST", 1315275),
            Map.entry("BOU", 5643539),
            Map.entry("BRE", 63451855),
            Map.entry("BRI", 5607747),
            Map.entry("CHE", 1046674),
            Map.entry("COV", 3612640),
            Map.entry("CRY", 3006569),
            Map.entry("EVE", 1300662),
            Map.entry("FUL", 1347893),
            Map.entry("HUL", 3096963),
            Map.entry("IPS", 3779605),
            Map.entry("LEE", 2793103),
            Map.entry("LIV", 1046732),
            Map.entry("MCI", 1328029),
            Map.entry("MUN", 1046599),
            Map.entry("NEW", 1062581),
            Map.entry("NOT", 3057313),
            Map.entry("SUN", 2682737),
            Map.entry("TOT", 2682611)
    );

    private final ConcurrentHashMap<String, CachedTeamNews> teamNewsCache = new ConcurrentHashMap<>();

    List<MatchNewsItem> loadMatchNews(Match match, int limit) {
        if (match == null) {
            return List.of();
        }
        try {
            List<MatchNewsItem> news = new ArrayList<>();
            Team home = DaoUtil.TEAMS.get(match.getHomeTeamId());
            Team away = DaoUtil.TEAMS.get(match.getAwayTeamId());
            if (home != null) {
                news.addAll(loadTeamNews(home.getCode(), Math.max(limit, 3)));
            }
            if (away != null) {
                news.addAll(loadTeamNews(away.getCode(), Math.max(limit, 3)));
            }
            news = news.stream()
                    .filter(item -> item.title() != null && !item.title().isBlank())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    MatchNewsItem::url,
                                    item -> item,
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ),
                            map -> new ArrayList<>(map.values())
                    ));
            news.sort(Comparator.comparing(MatchNewsItem::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (news.size() > limit) {
                return news.subList(0, limit);
            }
            return news;
        } catch (Exception e) {
            log.warn("MiniApp match news load failed: matchId={}, error={}",
                    match.getPublicId(), e.getMessage());
            return List.of();
        }
    }

    private List<MatchNewsItem> loadTeamNews(String teamCode, int limit) {
        Integer tagId = TEAM_NEWS_TAG_IDS.get(teamCode);
        if (tagId == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CachedTeamNews cached = teamNewsCache.get(teamCode);
        if (cached != null && now - cached.cachedAtMs() <= TEAM_NEWS_CACHE_MS) {
            return cached.news();
        }
        try {
            URL source = new URL(SPORTS_RU_TEAM_RSS + tagId);
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(source));
            List<MatchNewsItem> items = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (items.size() >= limit) {
                    break;
                }
                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String url = entry.getLink() == null ? "" : entry.getLink().trim();
                if (title.isBlank() || url.isBlank()) {
                    continue;
                }
                String published = "";
                if (entry.getPublishedDate() != null) {
                    published = LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.of(AppTimeZones.DISPLAY.getId()))
                            .format(NEWS_TS);
                }
                items.add(new MatchNewsItem(title, url, published));
            }
            teamNewsCache.put(teamCode, new CachedTeamNews(now, items));
            return items;
        } catch (Exception e) {
            log.warn("MiniApp team news load failed: teamCode={}, tagId={}, error={}",
                    teamCode, tagId, e.getMessage());
            return List.of();
        }
    }

    private record CachedTeamNews(long cachedAtMs, List<MatchNewsItem> news) {
    }
}
