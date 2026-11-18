package com.yesman.akytheralabs.gui.panel.script;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Mutable working copy of a [BlueprintScriptData.Variable] (which is an immutable record). The Variables /
/// Details panels edit a `VariableDraft` in place; the edits are flushed back into the [BlueprintScriptData]
/// only when the user presses "Save". See [VariableEditController].
///
/// The default value is held as its [ValueShape] editor value, beside the JSON of the last value that
/// encoded.
///
/// A draft standing for a data parent's variable keeps the parent's name and type, and holds the default value
/// and sync direction the blueprint lays over the parent's. It is written out only while [#overrides] holds.
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

    /// The data parent's variable this draft stands for, as a draft nothing edits. Null for a variable the
    /// blueprint declares itself.
    private final @Nullable VariableDraft inherited;

    public VariableDraft(BlueprintScriptData.Variable variable) {
        this(variable, null);
    }

    /// A draft of `variable` standing for the data parent's variable `inherited`, when it is not null.
    public VariableDraft(BlueprintScriptData.Variable variable, BlueprintScriptData.@Nullable Variable inherited) {
        this.name = variable.name();
        this.type = variable.type();
        this.inherited = inherited == null ? null : new VariableDraft(inherited);
        load(variable);
    }

    /// The drafts of `data`: one per variable its data parents declare, holding what `data` lays over it, then one
    /// per variable `data` declares itself. A variable `data` declares under an inherited name with another type
    /// is dropped for the inherited one.
    public static List<VariableDraft> of(BlueprintScriptData data) {
        Map<String, BlueprintScriptData.Variable> declared = new LinkedHashMap<>();
        List<VariableDraft> drafts = new ArrayList<>();

        data.getVariables().forEach(variable -> declared.put(variable.name(), variable));

        for (BlueprintScriptData.Variable inherited : data.inheritedVariables()) {
            BlueprintScriptData.Variable laidOver = declared.remove(inherited.name());

            drafts.add(new VariableDraft(laidOver != null && laidOver.type().equals(inherited.type()) ? laidOver : inherited, inherited));
        }

        declared.values().forEach(variable -> drafts.add(new VariableDraft(variable)));

        return drafts;
    }

    /// Whether this draft stands for a data parent's variable.
    public boolean isInherited() {
        return inherited != null;
    }

    /// Whether this draft holds a default value or a sync direction the data parent's variable does not.
    public boolean overrides() {
        return inherited != null && (!Objects.equals(defaultJson, inherited.defaultJson) || sync != inherited.sync);
    }

    /// The data parent's variable, or null for a variable the blueprint declares itself.
    public BlueprintScriptData.@Nullable Variable inheritedData() {
        return inherited == null ? null : inherited.toData();
    }

    /// The default value of the data parent's variable as the editor holds it, or null when there is none.
    public @Nullable Object inheritedDefaultVal() {
        return inherited == null ? null : inherited.defaultVal;
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

    /// Whether the default value does not encode, did not decode, or names an entry that does not resolve.
    public boolean hasDefaultError() {
        return defaultIncomplete || defaultUnreadable || !EditorWidgetFactory.resolves(defaultVal);
    }

    public ScriptVariables.SyncDirection getSync() {
        return sync;
    }

    public void setSync(ScriptVariables.SyncDirection sync) {
        this.sync = sync;
    }
}
