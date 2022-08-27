package zhigalin.predictions.service_impl.predict;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.predict.OddsMapper;
import zhigalin.predictions.dto.predict.OddsDto;
import zhigalin.predictions.model.predict.Odds;
import zhigalin.predictions.repository.predict.OddsRepository;
import zhigalin.predictions.service.predict.OddsService;

@Service
public class OddsServiceImpl implements OddsService {
    private final OddsRepository repository;
    private final OddsMapper mapper;

    @Autowired
    public OddsServiceImpl(OddsRepository repository, OddsMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }


    @Override
    public OddsDto save(OddsDto dto) {
        Odds odds = repository.getOddsByFixtureId(dto.getFixtureId());
        if (odds != null) {
            mapper.updateEntityFromDto(dto, odds);
            return mapper.toDto(repository.save(odds));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public OddsDto getByFixtureId(Long fixtureId) {
        return mapper.toDto(repository.getOddsByFixtureId(fixtureId));
    }

}
