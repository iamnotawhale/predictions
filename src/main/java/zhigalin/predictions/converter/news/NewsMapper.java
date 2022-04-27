package zhigalin.predictions.converter.news;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.model.news.News;

@Mapper(componentModel = "spring")
public interface NewsMapper extends CustomMapper<News, NewsDto> {
}
