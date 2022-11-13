package zhigalin.predictions.service._impl.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.event.HeadToHeadMapper;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.model.event.HeadToHead;
import zhigalin.predictions.repository.event.HeadToHeadRepository;
import zhigalin.predictions.service.event.HeadToHeadService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
public class HeadToHeadServiceImpl implements HeadToHeadService {

    private final HeadToHeadRepository repository;

    private final HeadToHeadMapper mapper;

    @Override
    public HeadToHeadDto save(HeadToHeadDto headToHeadDto) {
        HeadToHead headToHead = repository.getByHomeTeam_PublicIdAndAwayTeam_PublicIdAndLocalDateTime(
                headToHeadDto.getHomeTeam().getPublicId(), headToHeadDto.getAwayTeam().getPublicId(), headToHeadDto.getLocalDateTime());
        if (headToHead != null) {
            mapper.updateEntityFromDto(headToHeadDto, headToHead);
            return mapper.toDto(repository.save(headToHead));
        }
        return mapper.toDto(repository.save(mapper.toEntity(headToHeadDto)));
    }

    @Override
    public List<HeadToHeadDto> getAllByTwoTeamsCode(String firstTeamCode, String secondTeamCode) {
        List<HeadToHead> list1 = repository.getAllByHomeTeam_CodeAndAwayTeam_Code(firstTeamCode, secondTeamCode);
        List<HeadToHead> list2 = repository.getAllByHomeTeam_CodeAndAwayTeam_Code(secondTeamCode, firstTeamCode);

        return Stream.concat(list1.stream(), list2.stream())
                .sorted(Comparator.comparing(HeadToHead::getLocalDateTime).reversed())
                .limit(7)
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<HeadToHeadDto> getAllByMatch(MatchDto matchDto) {
        return getAllByTwoTeamsCode(matchDto.getHomeTeam().getCode(), matchDto.getAwayTeam().getCode());
    }
}
