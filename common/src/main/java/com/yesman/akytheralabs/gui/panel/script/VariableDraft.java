package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import org.jspecify.annotations.Nullable;

/// Mutable working copy of a [ScriptVariables.VariableInfo] (which is an immutable
/// record). The Variables / Details panels edit a `VariableDraft` in place; the
/// edits are flushed back into the [AnimatorScriptHolder]
/// only when the user presses "Save". See [VariableEditController].
public final class VariableDraft {
    private String name;
    private ScriptType type;
    @Nullable
    private Object defaultVal;

    public VariableDraft(String name, ScriptType type, @Nullable Object defaultVal) {
        this.name = name;
        this.type = type;
        this.defaultVal = defaultVal;
    }

    public static VariableDraft from(ScriptVariables.VariableInfo info) {
        return new VariableDraft(info.name(), info.type(), info.defaultVal());
    }

    public ScriptVariables.VariableInfo toInfo() {
        return new ScriptVariables.VariableInfo(name, type, defaultVal);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ScriptType getType() {
        return type;
    }

    public void setType(ScriptType type) {
        this.type = type;
    }

    public @Nullable Object getDefaultVal() {
        return defaultVal;
    }

    public void setDefaultVal(@Nullable Object defaultVal) {
        this.defaultVal = defaultVal;
    }
}
