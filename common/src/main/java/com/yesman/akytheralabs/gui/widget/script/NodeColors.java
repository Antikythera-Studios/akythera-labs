package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.world.phys.Vec3;

/// Pin and header colors keyed on a value's java type, mirroring Unreal's
/// type-coded pins (red boolean, green float, teal integer, yellow vector, …).
/// Used by the data-driven node widgets so a pin's color advertises its type.
///
/// [#forType] is public so the Variables UI (the type combo's color indicator and the
/// drag ghost) can paint a variable in the exact same color its Get / Set node pins use.
public final class NodeColors {
    static final int EXEC = 0xFFEEEEEE;
    /// A "wildcard" pin color for a data pin whose type is not yet decided (e.g. a
    /// [ComparatorNodeWidget] in type-detecting mode). Mirrors UE's gray wildcard pins.
    static final int WILDCARD = 0xFFBFBFBF;

    private static final int BOOLEAN = 0xFFA01E1E;
    /// Floating-point types (float / double): a vivid pure green, kept clearly distinct from the
    /// teal [#INTEGRAL] so the two numeric families read apart at a glance (UE green-vs-cyan pins).
    private static final int FLOATING = 0xFF49D14A;
    /// Integer types (byte / short / int / long): teal/cyan, distinct from the green [#FLOATING].
    private static final int INTEGRAL = 0xFF1FB5A8;
    private static final int STRING = 0xFFE45CC8;
    private static final int VECTOR = 0xFFF2D94E;
    private static final int OBJECT = 0xFF40A0FF;
    /// Whitish, mirroring UE's animation [Pose] pins (the "running figure" pins).
    private static final int POSE = 0xFFE8E8E8;

    private NodeColors() {}

    /// [#forType] for a possibly-undecided type: a null type yields the [#WILDCARD] color.
    static int forTypeOrWildcard(@org.jspecify.annotations.Nullable Class<?> type) {
        return type == null ? WILDCARD : forType(type);
    }

    public static int forType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return BOOLEAN;
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) return FLOATING;
        if (type == int.class || type == Integer.class
            || type == long.class || type == Long.class
            || type == short.class || type == Short.class
            || type == byte.class || type == Byte.class) return INTEGRAL;
        if (type == String.class) return STRING;
        if (type == Vec3.class) return VECTOR;
        if (type == Pose.class) return POSE;
        return OBJECT;
    }

    /// A darkened tint of a type color, used for a node's title bar.
    static int titleTint(Class<?> type) {
        return darken(forType(type), 0.45F);
    }

    private static int darken(int argb, float factor) {
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * factor);
        int g = Math.round(((argb >> 8) & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
