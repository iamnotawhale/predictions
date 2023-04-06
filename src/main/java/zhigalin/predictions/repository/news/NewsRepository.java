package zhigalin.predictions.repository.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.news.News;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    News findByTitle(String title);
    @Query(value = "SELECT setval('news_sequence', 1, false)", nativeQuery = true)
    void resetSequence();
}
