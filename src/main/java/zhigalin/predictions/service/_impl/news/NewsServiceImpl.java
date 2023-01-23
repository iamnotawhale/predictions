package zhigalin.predictions.service._impl.news;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.news.NewsMapper;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.model.news.News;
import zhigalin.predictions.repository.news.NewsRepository;
import zhigalin.predictions.service.news.NewsService;

import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
public class NewsServiceImpl implements NewsService {

    private final NewsRepository repository;
    private final NewsMapper mapper;

    @Override
    public List<NewsDto> getAll() {
        List<News> list = (List<News>) repository.findAll();
        return list.stream().map(mapper::toDto).toList();
    }

    @Override
    public NewsDto save(NewsDto dto) {
        News news = repository.findByTitle(dto.getTitle());
        if (news != null) {
            mapper.updateEntityFromDto(dto, news);
            return mapper.toDto(repository.save(news));
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public List<NewsDto> getLastNews() {
        List<NewsDto> list = getAll();
        return list.stream()
                .sorted(Comparator.comparing(NewsDto::getDateTime).reversed())
                .limit(15)
                .toList();
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public void resetSequence() {
        repository.resetSequence();
    }
}
