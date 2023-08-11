package zhigalin.predictions.util;

import java.lang.reflect.Field;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FieldsUpdater<T> {

    public static <T> T update(T oldValue, T newValue) {
        Class<?> clazz = null;
        try {
            clazz = oldValue.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object newValueField = field.get(newValue);

                if (newValueField != null) {
                    field.set(oldValue, newValueField);
                }
            }
        } catch (IllegalAccessException e) {
            log.error("Error to update fields of {} with: {}", clazz, e.getMessage());
        }
        return oldValue;
    }
}
