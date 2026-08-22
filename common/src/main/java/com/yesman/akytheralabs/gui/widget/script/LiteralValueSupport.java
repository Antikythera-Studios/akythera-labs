package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/// Inline-literal rules for [LiteralNodeWidget], mirroring the engine's [LiteralNode] which can only carry a
/// directly-encodable value. A literal is editable only when its type is a primitive / wrapper, [String], or
/// an `enum`; everything else (object references, data blocks without a simple constant set) must come from
/// another pure node instead.
public final class LiteralValueSupport {
    private LiteralValueSupport() {}

    public static boolean isSupported(@Nullable Class<?> type) {
        if (type == null) return false;
        Class<?> b = boxed(type);
        return b == Boolean.class
            || b == Character.class
            || b == String.class
            || Number.class.isAssignableFrom(b)
            || type.isEnum();
    }

    /// A starting value so a freshly spawned literal always shows something concrete.
    static @Nullable Object defaultValue(Class<?> type) {
        Class<?> b = boxed(type);
        if (b == Boolean.class)   return Boolean.FALSE;
        if (b == Character.class) return ' ';
        if (b == String.class)    return "";
        if (b == Byte.class)      return (byte) 0;
        if (b == Short.class)     return (short) 0;
        if (b == Integer.class)   return 0;
        if (b == Long.class)      return 0L;
        if (b == Float.class)     return 0.0F;
        if (b == Double.class)    return 0.0D;
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        return null;
    }

    static String display(@Nullable Object value) {
        if (value == null) return "<null>";
        if (value instanceof String s) return '"' + s + '"';
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }

    /// Booleans and enums cycle in place on click — no text entry needed.
    static boolean isClickCyclable(Class<?> type) {
        return boxed(type) == Boolean.class || type.isEnum();
    }

    /// Advances a boolean / enum literal to its next value.
    static Object cycle(Class<?> type, @Nullable Object current) {
        if (boxed(type) == Boolean.class) {
            return !(current instanceof Boolean b && b);
        }

        Object[] constants = type.getEnumConstants();
        if (constants.length == 0) return current;

        int index = 0;
        for (int i = 0; i < constants.length; i++) {
            if (constants[i] == current) {
                index = i;
                break;
            }
        }
        return constants[(index + 1) % constants.length];
    }

    /// Filter applied to the text-entry box for numeric / char / string literals, so the
    /// in-progress text never becomes un-parseable. Lenient about partial input ("-", "").
    static Predicate<String> filterFor(Class<?> type) {
        Class<?> b = boxed(type);
        if (b == String.class) return s -> true;
        if (b == Character.class) return s -> s.length() <= 1;
        if (b == Float.class || b == Double.class) return s -> s.matches("-?\\d*\\.?\\d*");
        return s -> s.matches("-?\\d*"); // integral
    }

    /// Parses committed text into the literal's type. Throws on invalid / empty input so
    /// the caller can keep the previous value.
    static Object parse(Class<?> type, String text) {
        Class<?> b = boxed(type);
        if (b == String.class)    return text;
        if (b == Character.class) {
            if (text.isEmpty()) throw new IllegalArgumentException("empty char");
            return text.charAt(0);
        }
        if (b == Byte.class)    return Byte.parseByte(text);
        if (b == Short.class)   return Short.parseShort(text);
        if (b == Integer.class) return Integer.parseInt(text);
        if (b == Long.class)    return Long.parseLong(text);
        if (b == Float.class)   return Float.parseFloat(text);
        if (b == Double.class)  return Double.parseDouble(text);
        throw new IllegalArgumentException("unsupported literal type " + type.getSimpleName());
    }

    static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive())   return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class)    return Byte.class;
        if (type == short.class)   return Short.class;
        if (type == char.class)    return Character.class;
        if (type == int.class)     return Integer.class;
        if (type == long.class)    return Long.class;
        if (type == float.class)   return Float.class;
        if (type == double.class)  return Double.class;
        return Void.class;
    }
}
