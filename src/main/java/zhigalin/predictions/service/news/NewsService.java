package zhigalin.predictions.service.news;

import zhigalin.predictions.dto.news.NewsDto;

import java.util.List;

public interface NewsService {

    NewsDto save(NewsDto dto);

    List<NewsDto> findAll();

    List<NewsDto> findLastNews();

    void deleteAll();

    void resetSequence();
}
