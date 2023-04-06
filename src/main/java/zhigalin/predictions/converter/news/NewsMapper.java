package zhigalin.predictions.converter.news;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.news.NewsDto;
import zhigalin.predictions.model.news.News;

@Mapper(componentModel = "spring")
public interface NewsMapper extends CustomMapper<News, NewsDto> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(NewsDto dto, @MappingTarget News entity);
}
