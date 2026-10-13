package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;

/// Implemented by the member-variable node widgets ([ScriptVariableGetterNodeWidget] /
/// [ScriptVariableSetterNodeWidget]) so the [ScriptNodeGraphWidget] can retarget them
/// to a new type when the variable's type is edited in the Variables panel —
/// re-coloring / re-typing their data pins and dropping wires the new type can no
/// longer satisfy.
public interface VariableNodeWidget {
    /// The script member variable this node reads (getter) or writes (setter).
    String variableName();

    /// The variable's type, or null for a node whose stored type did not resolve. Its data pin(s) carry
    /// [ScriptType#type].
    @Nullable ScriptType variableType();

    /// Re-types the node to `newType`: updates [#variableType], re-colors the title and
    /// data pin(s), and relayouts. Idempotent, so it doubles as both the redo (to the new
    /// type) and the undo (back to the old type) of a variable retype.
    void applyVariableType(@Nullable ScriptType newType);

    /// Re-targets the node to `newName`: updates [#variableName] and the title / data-pin
    /// label that show it, and relayouts. Idempotent, so it doubles as both the redo (to the
    /// new name) and the undo (back to the old name) of a variable rename.
    void applyVariableName(String newName);

    /// The class the data pins of a variable of `type` carry.
    static Class<?> pinType(@Nullable ScriptType type) {
        return type == null ? Object.class : type.type();
    }

    /// The type the data pins of a variable of `type` declare: `List<Integer>` for a list of integers, so a wire
    /// between two collections checks their elements too, see [AbstractNodeWidget#pinsCompatible].
    static Type pinGenericType(@Nullable ScriptType type) {
        if (type == null) return Object.class;

        return switch (type.container()) {
            case SINGLE -> type.type();
            case LIST, SET -> ParseUtils.parameterized(type.type(), pinGenericType(type.base()));
            case MAP -> ParseUtils.parameterized(
                type.type(), pinGenericType(type.base()), pinGenericType(Objects.requireNonNull(type.element())));
        };
    }

    /// The class the pins and title of a variable of `type` are colored by: the type it is made of, see
    /// [ScriptType#base].
    static Class<?> colorType(@Nullable ScriptType type) {
        return type == null ? Object.class : type.base().type();
    }
}