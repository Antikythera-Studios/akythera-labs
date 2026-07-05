package com.yesman.akytheralabs.editor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Collects [EditorEditable]-annotated fields off a class, walking superclasses,
/// and caches the result.
public final class EditorFieldScanner {
    private static final Map<Class<?>, List<EditorField>> CACHE = new ConcurrentHashMap<>();

    private EditorFieldScanner() {}

    public static List<EditorField> fieldsOf(Class<?> type) {
        return CACHE.computeIfAbsent(type, EditorFieldScanner::scan);
    }

    private static List<EditorField> scan(Class<?> type) {
        List<EditorField> out = new ArrayList<>();
        Class<?> c = type;

        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                EditorEditable ann = f.getAnnotation(EditorEditable.class);
                if (ann == null) continue;
                f.setAccessible(true);
                out.add(new EditorField(f, ann));
            }

            c = c.getSuperclass();
        }

        out.sort(Comparator.comparingInt(e -> e.annotation().order()));

        return List.copyOf(out);
    }

    public record EditorField(Field field, EditorEditable annotation) {
        public String label() {
            return annotation.label().isBlank() ? field.getName() : annotation.label();
        }
    }
}