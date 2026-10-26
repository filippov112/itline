package com.example.itline.unit;

import com.example.itline.domain.Record;
import java.lang.reflect.Field;

/** Хелпер для тестов: выставляет id/version в обход JPA. */
final class TestRecords {
    private TestRecords() {}

    static void setId(Record r, Long id) {
        write(r, "id", id);
    }

    static void setVersion(Record r, long version) {
        write(r, "version", version);
    }

    private static void write(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + field, e);
        }
    }
}