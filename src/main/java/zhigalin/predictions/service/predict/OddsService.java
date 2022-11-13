package zhigalin.predictions.service.predict;

import zhigalin.predictions.dto.predict.OddsDto;

public interface OddsService {

    OddsDto save(OddsDto dto);

    OddsDto getByFixtureId(Long fixtureId);
}
