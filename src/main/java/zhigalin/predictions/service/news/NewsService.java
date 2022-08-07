package zhigalin.predictions.service.news;

import zhigalin.predictions.dto.news.NewsDto;

import java.util.List;

public interface NewsService {
    List<NewsDto> getAll();

    NewsDto save(NewsDto dto);

    List<NewsDto> getAllLast();

    void deleteAll();

    void resetSequence();
}
