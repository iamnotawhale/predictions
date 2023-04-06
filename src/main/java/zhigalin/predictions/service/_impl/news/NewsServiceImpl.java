package zhigalin.predictions.service._impl.news;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class NewsServiceImpl implements NewsService {
    private final NewsRepository repository;
    private final NewsMapper mapper;

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
    public List<NewsDto> findAll() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public List<NewsDto> findLastNews() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        List<NewsDto> list = findAll();
        return list.stream()
                .sorted(Comparator.comparing(NewsDto::getLocalDateTime).reversed())
                .limit(15)
                .toList();
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public void resetSequence() {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        repository.resetSequence();
    }
}
