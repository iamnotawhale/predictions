package zhigalin.predictions.converter;

import org.mapstruct.MappingTarget;

public interface CustomMapper<T, D> {
    T toEntity(D d);
    D toDto(T t);
    void updateEntity(T t, @MappingTarget T target);
    void updateDto(D d, @MappingTarget D target);
}
