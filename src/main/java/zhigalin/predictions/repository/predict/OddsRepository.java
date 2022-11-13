package zhigalin.predictions.repository.predict;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.predict.Odds;

@Repository
public interface OddsRepository extends CrudRepository<Odds,Long> {

    Odds getOddsByFixtureId(Long id);
}
