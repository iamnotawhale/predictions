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
        Match match = repository.findMatchByHomeTeamIdAndAwayTeamId(matchDto.getHomeTeam().getId(),
                matchDto.getAwayTeam().getId());
        if (match != null) {
            mapper.updateEntityFromDto(matchDto, match);
            return mapper.toDto(repository.save(match));
        }
        return mapper.toDto(repository.save(mapper.toEntity(matchDto)));
    }

    @Override
    public MatchDto findById(Long matchId) {
        return mapper.toDto(repository.findById(matchId).orElse(null));
    }

    @Override
    public MatchDto findByPublicId(Long publicId) {
        return mapper.toDto(repository.findMatchByPublicId(publicId));
    }

    @Override
    public List<MatchDto> findAllByTodayDate() {
        return repository.findAllByMatchDateOrderByWeekAscLocalDateTimeAsc(LocalDate.now())
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByUpcomingDays(Integer days) {
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now(),
                        LocalDateTime.now().plusDays(days))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByWeekId(Long weekId) {
        return repository.findAllByWeekId(weekId).stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> findAllByCurrentWeek() {
        return repository.findAllByWeekIsCurrentTrueOrderByLocalDateTime()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public MatchDto findByTeamIds(Long homeTeamId, Long awayTeamId) {
        return mapper.toDto(repository.findMatchByHomeTeamIdAndAwayTeamId(homeTeamId, awayTeamId));
    }

    @Override
    public MatchDto findByTeamNames(String homeTeamName, String awayTeamName) {
        return mapper.toDto(repository.findMatchByHomeTeamTeamNameAndAwayTeamTeamName(homeTeamName, awayTeamName));
    }

    @Override
    public MatchDto findByTeamCodes(String homeCode, String awayCode) {
        return mapper.toDto(repository.findMatchByHomeTeamCodeAndAwayTeamCode(homeCode, awayCode));
    }

    @Override
    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        List<Integer> result = new ArrayList<>();
        Match match = repository.findMatchByHomeTeamTeamNameAndAwayTeamTeamName(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }

    @Override
    public List<MatchDto> findAllByTeamId(Long teamId) {
        return repository.findAllByTeamId(teamId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findLast5MatchesByTeamId(Long teamId) {
        return repository.findAllByTeamId(teamId)
                .stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .map(mapper::toDto).toList();
    }

    @Override
    public List<String> getLast5MatchesResultByTeamId(Long teamId) {
        List<String> result = new ArrayList<>();
        List<MatchDto> list = repository.findAllByTeamId(teamId).stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .map(mapper::toDto)
                .toList();
        for (MatchDto dto : list) {
            if (dto.getHomeTeam().getId().equals(teamId) && dto.getResult().equals("H") ||
                    dto.getAwayTeam().getId().equals(teamId) && dto.getResult().equals("A")) {
                result.add("W");
            } else if (dto.getHomeTeam().getId().equals(teamId) && dto.getResult().equals("A") ||
                    dto.getAwayTeam().getId().equals(teamId) && dto.getResult().equals("H")) {
                result.add("L");
            } else {
                result.add("D");
            }
        }
        return result;
    }

    @Override
    public List<MatchDto> findOnline() {
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusMinutes(130),
                        LocalDateTime.now().plusMinutes(10))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByStatus(String status) {
        return repository.findAllByStatus(status)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public MatchDto getOnlineResult(String teamName) {
        MatchDto matchDto = repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusHours(2),
                        LocalDateTime.now())
                .stream()
                .filter(m -> m.getHomeTeam().getTeamName().equals(teamName) || m.getAwayTeam().getTeamName().equals(teamName))
                .findFirst()
                .map(mapper::toDto)
                .orElse(null);

        if (matchDto != null && matchDto.getResult() != null) {
            if (matchDto.getHomeTeam().getTeamName().equals(teamName)) {
                return MatchDto.builder()
                        .homeTeamScore(matchDto.getHomeTeamScore())
                        .awayTeamScore(matchDto.getAwayTeamScore())
                        .result(Objects.equals(matchDto.getResult(), "H") ? "H" :
                                Objects.equals(matchDto.getResult(), "A") ? "A" : "D")
                        .build();
            } else {
                return MatchDto.builder()
                        .homeTeamScore(matchDto.getAwayTeamScore())
                        .awayTeamScore(matchDto.getHomeTeamScore())
                        .result(Objects.equals(matchDto.getResult(), "A") ? "H" :
                                Objects.equals(matchDto.getResult(), "H") ? "A" : "D")
                        .build();
            }
        }
        return null;
    }
}
