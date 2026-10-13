package com.yesman.akytheralabs.gui.panel.script;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.editor.ValueShape;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Mutable working copy of a [BlueprintScriptData.Variable] (which is an immutable record). The Variables /
/// Details panels edit a `VariableDraft` in place; the edits are flushed back into the [BlueprintScriptData]
/// only when the user presses "Save". See [VariableEditController].
///
/// The default value is held as its [ValueShape] editor value, beside the JSON of the last value that
/// encoded.
public final class VariableDraft {
    private String name;
    private ScriptType type;
    private ScriptVariables.SyncDirection sync;

    /// The default value as the editor holds it, see [ValueShape#toEditor].
    @Nullable
    private Object defaultVal;

    /// What [#toData] writes as the default value. Null while the variable starts unset.
    @Nullable
    private JsonElement defaultJson;

    /// Whether [#defaultVal] does not encode yet, such as a list element with nothing picked.
    private boolean defaultIncomplete;

    /// Whether the stored default value failed to decode, leaving only [#defaultJson].
    private boolean defaultUnreadable;

    public VariableDraft(BlueprintScriptData.Variable variable) {
        this.name = variable.name();
        this.type = variable.type();
        load(variable);
    }

    /// Replaces the type, sync direction and default value with `variable`'s.
    public void load(BlueprintScriptData.Variable variable) {
        this.type = variable.type();
        this.sync = variable.sync();
        this.defaultJson = variable.defaultValue().orElse(null);
        this.defaultIncomplete = false;
        this.defaultUnreadable = false;

        ValueShape shape = ValueShape.of(type);

        try {
            this.defaultVal = defaultJson == null ? shape.unset() : shape.fromJson(defaultJson);
        } catch (RuntimeException e) {
            this.defaultVal = shape.unset();
            this.defaultUnreadable = true;
        }
    }

    public BlueprintScriptData.Variable toData() {
        return new BlueprintScriptData.Variable(name, type, Optional.ofNullable(defaultJson), sync);
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

    /// Retypes the variable, starting it at `defaultVal`, an editor value of `type`.
    public void setType(ScriptType type, @Nullable Object defaultVal) {
        this.type = type;
        setDefaultVal(defaultVal);
    }

    public @Nullable Object getDefaultVal() {
        return defaultVal;
    }

    /// Sets the default value and, when it encodes, the JSON [#toData] writes. The type's unset value leaves the
    /// variable without a default.
    public void setDefaultVal(@Nullable Object defaultVal) {
        ValueShape shape = ValueShape.of(type);

        this.defaultVal = defaultVal;
        this.defaultIncomplete = false;
        this.defaultUnreadable = false;

        if (defaultVal == null) {
            this.defaultJson = null;
            return;
        }

        try {
            this.defaultJson = shape.toJson(defaultVal);
        } catch (RuntimeException e) {
            if (defaultVal.equals(shape.unset())) {
                this.defaultJson = null;
            } else {
                this.defaultIncomplete = true;
            }
        }
    }

    public @Nullable JsonElement getDefaultJson() {
        return defaultJson;
    }

    public boolean isDefaultIncomplete() {
        return defaultIncomplete;
    }

    public boolean isDefaultUnreadable() {
        return defaultUnreadable;
    }

    public ScriptVariables.SyncDirection getSync() {
        return sync;
    }

    public void setSync(ScriptVariables.SyncDirection sync) {
        this.sync = sync;
    }
}
