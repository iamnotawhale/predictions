package zhigalin.predictions.converter;

public interface CustomMapper<T, D> {
    T toEntity(D d);
    D toDto(T t);
}
