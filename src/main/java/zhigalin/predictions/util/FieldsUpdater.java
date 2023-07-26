package zhigalin.predictions.util;

import java.lang.reflect.Field;

public class FieldsUpdater<T> {

    public static <T> T update(T oldValue, T newValue) {
        try {
            Class<?> clazz = oldValue.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object newValueField = field.get(newValue);

                if (newValueField != null) {
                    field.set(oldValue, newValueField);
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return oldValue;
    }
}
