package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.event.MatchRepository;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class MatchServiceImpl implements MatchService {
    private final MatchRepository repository;
    private final MatchMapper mapper;
    private final PredictionService predictionService;
    private final PredictionMapper predictionMapper;

    @Override
    public MatchDto save(MatchDto dto) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        Match match = repository.findByHomeTeamIdAndAwayTeamId(dto.getHomeTeam().getId(),
                dto.getAwayTeam().getId());
        if (match != null) {
            mapper.updateEntityFromDto(dto, match);
            updatePredictionsByMatch(dto);
            return mapper.toDto(repository.save(match));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public MatchDto findById(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public MatchDto findByPublicId(Long publicId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findByPublicId(publicId));
    }

    @Override
    public List<MatchDto> findAllByTodayDate() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime
                                .of(LocalDate.now(), LocalTime.of(0, 1)),
                        LocalDateTime
                                .of(LocalDate.now(), LocalTime.of(23, 59)))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByUpcomingDays(Integer days) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now(),
                        LocalDateTime.now().plusDays(days))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByWeekId(Long weekId) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByWeekIdOrderByLocalDateTime(weekId).stream().map(mapper::toDto).toList();
    }

    @Override
    public List<MatchDto> findAllByCurrentWeek() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByWeekIsCurrentTrueOrderByLocalDateTime()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAll() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public MatchDto findByTeamNames(String homeTeamName, String awayTeamName) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findByHomeTeamNameAndAwayTeamName(homeTeamName, awayTeamName));
    }

    @Override
    public MatchDto findByTeamCodes(String homeTeamCode, String awayTeamCode) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return mapper.toDto(repository.findByHomeTeamCodeAndAwayTeamCode(homeTeamCode, awayTeamCode));
    }

    @Override
    public List<Integer> getResultByTeamNames(String homeTeamName, String awayTeamName) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        List<Integer> result = new ArrayList<>();
        Match match = repository.findByHomeTeamNameAndAwayTeamName(homeTeamName, awayTeamName);
        result.add(match.getHomeTeamScore());
        result.add(match.getAwayTeamScore());
        return result;
    }

    @Override
    public List<MatchDto> findAllByTeamId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByTeamId(id)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findLast5MatchesByTeamId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByTeamId(id)
                .stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .map(mapper::toDto).toList();
    }

    @Override
    public List<String> getLast5MatchesResultByTeamId(Long id) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        List<String> result = new ArrayList<>();
        List<MatchDto> list = repository.findAllByTeamId(id).stream()
                .sorted(Comparator.comparing(Match::getLocalDateTime).reversed())
                .filter(m -> m.getResult() != null)
                .limit(5)
                .map(mapper::toDto)
                .toList();
        for (MatchDto dto : list) {
            if (dto.getHomeTeam().getId().equals(id) && dto.getResult().equals("H") ||
                    dto.getAwayTeam().getId().equals(id) && dto.getResult().equals("A")) {
                result.add("W");
            } else if (dto.getHomeTeam().getId().equals(id) && dto.getResult().equals("A") ||
                    dto.getAwayTeam().getId().equals(id) && dto.getResult().equals("H")) {
                result.add("L");
            } else {
                result.add("D");
            }
        }
        return result;
    }

    @Override
    public List<MatchDto> findOnline() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusMinutes(130),
                        LocalDateTime.now().plusMinutes(10))
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByStatus(String status) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAllByStatus(status)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public MatchDto getOnlineResult(String teamName) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        MatchDto matchDto = repository.findAllByLocalDateTimeBetweenOrderByLocalDateTime(LocalDateTime.now().minusHours(2),
                        LocalDateTime.now())
                .stream()
                .filter(m -> m.getHomeTeam().getName().equals(teamName) || m.getAwayTeam().getName().equals(teamName))
                .findFirst()
                .map(mapper::toDto)
                .orElse(null);

        if (matchDto != null && matchDto.getResult() != null) {
            if (matchDto.getHomeTeam().getName().equals(teamName)) {
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

    public void updatePredictionsByMatch(MatchDto match) {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        if (match.getPredictions() != null) {
            for (Prediction prediction : match.getPredictions()) {
                prediction.setPoints();
                predictionService.save(predictionMapper.toDto(prediction));
            }
        }
    }
}
