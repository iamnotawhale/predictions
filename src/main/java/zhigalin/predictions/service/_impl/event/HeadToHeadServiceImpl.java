package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.HeadToHeadMapper;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.repository.event.HeadToHeadRepository;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
public class HeadToHeadServiceImpl implements HeadToHeadService {

    private final HeadToHeadRepository repository;

    private final HeadToHeadMapper mapper;

    private final MatchService matchService;

    @Override
    public HeadToHeadDto save(HeadToHeadDto dto) {
        HeadToHead headToHead = repository.findByHomeTeamPublicIdAndAwayTeamPublicIdAndLocalDateTime(
                dto.getHomeTeam().getPublicId(), dto.getAwayTeam().getPublicId(), dto.getLocalDateTime());
        if (headToHead != null) {
            mapper.updateEntityFromDto(dto, headToHead);
            return mapper.toDto(repository.save(headToHead));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public List<HeadToHeadDto> findAllByTwoTeamsCode(String homeTeamCode, String awayTeamCode) {
        List<HeadToHead> list1 = repository.findAllByHomeTeamCodeAndAwayTeamCode(homeTeamCode, awayTeamCode);
        List<HeadToHead> list2 = repository.findAllByHomeTeamCodeAndAwayTeamCode(awayTeamCode, homeTeamCode);

        return Stream.concat(list1.stream(), list2.stream())
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(7)
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<HeadToHeadDto> findAllByMatch(MatchDto dto) {
        return findAllByTwoTeamsCode(dto.getHomeTeam().getCode(), dto.getAwayTeam().getCode());
    }

    @Override
    public List<List<HeadToHeadDto>> findAllByCurrentWeek() {
        List<List<HeadToHeadDto>> listOfHeadToHeads = new ArrayList<>();
        List<MatchDto> allByCurrentWeek = matchService.findAllByCurrentWeek();
        for (MatchDto matchDto : allByCurrentWeek) {
            listOfHeadToHeads.add(findAllByMatch(matchDto));
        }
        return listOfHeadToHeads;
    }


}
