package zhigalin.predictions.repository.news;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.news.News;

@Repository
public interface NewsRepository extends CrudRepository<News, Long> {
    News findByTitle(String title);

}
