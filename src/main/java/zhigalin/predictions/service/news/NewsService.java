package zhigalin.predictions.service.news;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.repository.news.NewsRepository;

import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class NewsService {
    private final NewsRepository repository;

    public News save(News news) {
        return repository.save(news);
    }

    public List<News> findAll() {
        return repository.findAll();
    }

    public List<News> findLastNews() {
        List<News> list = findAll();
        return list.stream()
                .sorted(Comparator.comparing(News::getLocalDateTime).reversed())
                .limit(15)
                .toList();
    }

    public void deleteAll() {
        repository.deleteAll();
    }
}
