package com.yesman.akytheralabs.gui.widget.script;

/// Implemented by the member-variable node widgets ([ScriptVariableGetterNodeWidget] /
/// [ScriptVariableSetterNodeWidget]) so the [ScriptNodeGraphWidget] can retarget them
/// to a new java type when the variable's type is edited in the Variables panel —
/// re-coloring / re-typing their data pins and dropping wires the new type can no
/// longer satisfy.
public interface VariableNodeWidget {
    /// The script member variable this node reads (getter) or writes (setter).
    String variableName();

    /// The java type the node's data pin(s) currently carry.
    Class<?> variableType();

    /// Re-types the node to `newType`: updates [#variableType], re-colors the title and
    /// data pin(s), and relayouts. Idempotent, so it doubles as both the redo (to the new
    /// type) and the undo (back to the old type) of a variable retype.
    void applyVariableType(Class<?> newType);

    /// Re-targets the node to `newName`: updates [#variableName] and the title / data-pin
    /// label that show it, and relayouts. Idempotent, so it doubles as both the redo (to the
    /// new name) and the undo (back to the old name) of a variable rename.
    void applyVariableName(String newName);
}