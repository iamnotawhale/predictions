package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.repository.event.MatchRepository;
import zhigalin.predictions.service.event.MatchService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class MatchServiceImpl implements MatchService {

    private final MatchRepository repository;
    private final MatchMapper mapper;

    @Override
    public MatchDto save(MatchDto matchDto) {
        Match match = repository.getMatchByHomeTeam_IdAndAwayTeam_Id(matchDto.getHomeTeam().getId(), matchDto.getAwayTeam().getId());
        if (match != null) {
            mapper.updateEntityFromDto(matchDto, match);
            return mapper.toDto(repository.save(match));
        }
        return mapper.toDto(repository.save(mapper.toEntity(matchDto)));
    }

    @Override
    public MatchDto getById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public MatchDto getByPublicId(Long publicId) {
        return mapper.toDto(repository.getMatchByPublicId(publicId));
    }

    @Override
    public List<MatchDto> getAllByTodayDate() {
        List<Match> allToday = repository.getAllByMatchDateOrderByWeekAscLocalDateTimeAsc(LocalDate.now());
        return allToday.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getAllByUpcomingDays(Integer days) {
        List<Match> fromNowToDay = repository.getAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now(), LocalDateTime.now().plusDays(days));
        return fromNowToDay.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getAllByWeekId(Long id) {
        List<Match> allByWeekId = repository.getAllByWeek_IdOrderByLocalDateTime(id);
        return allByWeekId.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getAllByCurrentWeek(Boolean b) {
        List<Match> allByCurrentWeek = repository.getAllByWeek_IsCurrentOrderByLocalDateTime(b);
        return allByCurrentWeek.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getAll() {
        List<Match> list = (List<Match>) repository.findAll();
        return list.stream().map(mapper::toDto).toList();
    }

    @Override
    public MatchDto getByTeamIds(Long homeTeamId, Long awayTeamId) {
        Match match = repository.getMatchByHomeTeam_IdAndAwayTeam_Id(homeTeamId, awayTeamId);
        return mapper.toDto(match);
    }

    @Override
    public MatchDto getByTeamNames(String homeTeamName, String awayTeamName) {
        Match match = repository.getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(homeTeamName, awayTeamName);
        return mapper.toDto(match);
    }

    @Override
    public MatchDto getByTeamCodes(String home, String away) {
        return mapper.toDto(repository.getMatchByHomeTeam_CodeAndAwayTeam_Code(home, away));
    }

    @Override
    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        List<Integer> result = new ArrayList<>();
        Match match = repository.getMatchByHomeTeam_TeamNameAndAwayTeam_TeamName(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }

    @Override
    public List<MatchDto> getAllByTeamId(Long id) {
        List<Match> list = repository.getAllByTeamId(id);
        return list.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getLast5MatchesByTeamId(Long id) {
        List<Match> list = repository.getAllByTeamId(id);
        return list.stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .map(mapper::toDto).toList();
    }

    @Override
    public List<String> getLast5MatchesResultByTeamId(Long id) {
        List<String> result = new ArrayList<>();
        List<Match> list = repository.getAllByTeamId(id).stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .toList();
        for (Match match : list) {
            if (match.getHomeTeam().getId().equals(id) && match.getResult().equals("H")
                    || match.getAwayTeam().getId().equals(id) && match.getResult().equals("A")) {
                result.add("W");
            } else if (match.getHomeTeam().getId().equals(id) && match.getResult().equals("A")
                    || match.getAwayTeam().getId().equals(id) && match.getResult().equals("H")) {
                result.add("L");
            } else {
                result.add("D");
            }
        }
        return result;
    }

    @Override
    public List<MatchDto> getOnline() {
        List<Match> list = repository.getAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusHours(2).minusMinutes(10),
                LocalDateTime.now().plusMinutes(10));
        return list.stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> getAllByStatus(String status) {
        List<Match> matchesByStatus = repository.getAllByStatus(status);
        return matchesByStatus.stream().map(mapper::toDto).toList();
    }

    @Override
    public MatchDto getOnlineResult(String teamName) {
        List<Match> online = repository.getAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusHours(2), LocalDateTime.now());
        MatchDto matchDto = online.stream().filter(m -> m.getHomeTeam().getTeamName().equals(teamName) ||
                m.getAwayTeam().getTeamName().equals(teamName)).findFirst().map(mapper::toDto).orElse(null);

        if (matchDto != null && matchDto.getResult() != null) {
            if (matchDto.getHomeTeam().getTeamName().equals(teamName)) {
                return MatchDto.builder()
                        .homeTeamScore(matchDto.getHomeTeamScore())
                        .awayTeamScore(matchDto.getAwayTeamScore())
                        .result(Objects.equals(matchDto.getResult(), "H") ? "H" : Objects.equals(matchDto.getResult(), "A") ? "A" : "D")
                        .build();
            } else {
                return MatchDto.builder()
                        .homeTeamScore(matchDto.getAwayTeamScore())
                        .awayTeamScore(matchDto.getHomeTeamScore())
                        .result(Objects.equals(matchDto.getResult(), "A") ? "H" : Objects.equals(matchDto.getResult(), "H") ? "A" : "D")
                        .build();
            }
        }
        return null;
    }
}
