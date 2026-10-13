package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/// Working copy of a [BlueprintScriptData.UserFunction], whose name, inputs and output the Details panel
/// edits. Its canvas is kept by the [BlueprintEditorSession].
public final class UserFunctionDraft {
    @EditorEditable(label = LangKeys.GUI_BLUEPRINT_FUNCTION_NAME, setOnCommit = true, rebuildOnWrite = true)
    private String name;

    @EditorEditable(label = LangKeys.GUI_BLUEPRINT_FUNCTION_INPUTS, defaultElement = "newParameter")
    private final List<Parameter> inputs;

    /// What the function returns, or null for nothing.
    @EditorEditable(label = LangKeys.GUI_BLUEPRINT_FUNCTION_OUTPUT, rebuildOnWrite = true)
    private @Nullable ScriptType output;

    /// Told `(oldName, newName)` on every rename.
    private final BiConsumer<String, String> onRenamed;

    public UserFunctionDraft(
        final String name, final List<Parameter> inputs, final @Nullable ScriptType output,
        final BiConsumer<String, String> onRenamed
    ) {
        this.name = name;
        this.inputs = new ArrayList<>(inputs);
        this.output = output;
        this.onRenamed = onRenamed;
    }

    public static UserFunctionDraft from(
        final BlueprintScriptData.UserFunction function, final BiConsumer<String, String> onRenamed
    ) {
        return new UserFunctionDraft(
            function.name(),
            function.parameters().stream().map(parameter -> new Parameter(parameter.name(), parameter.type())).toList(),
            function.returnType().orElse(null),
            onRenamed
        );
    }

    /// The `+` of the Inputs list.
    public static Parameter newParameter() {
        return new Parameter("parameter", ScriptType.of(float.class));
    }

    /// This draft with `graph` as its canvas. A parameter left without a type is dropped.
    public BlueprintScriptData.UserFunction toData(final ScriptGraphData graph) {
        return new BlueprintScriptData.UserFunction(
            name,
            inputs.stream()
                .filter(parameter -> parameter.type != null)
                .map(parameter -> new FunctionGraph.Parameter(parameter.name, Objects.requireNonNull(parameter.type)))
                .toList(),
            Optional.ofNullable(output),
            graph
        );
    }

    public BlueprintSignature signature() {
        return new BlueprintSignature(
            name,
            BlueprintSignature.Kind.FUNCTION,
            inputs.stream()
                .map(parameter -> new BlueprintSignature.Parameter(
                    parameter.name, parameter.type == null ? Object.class : parameter.type.type()))
                .toList(),
            output == null ? null : output.type()
        );
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        String previous = this.name;

        this.name = name;

        if (!previous.equals(name)) onRenamed.accept(previous, name);
    }

    public List<Parameter> getInputs() {
        return inputs;
    }

    /// One input of the function.
    public static final class Parameter {
        @EditorEditable(label = LangKeys.GUI_BLUEPRINT_FUNCTION_PARAMETER_NAME, setOnCommit = true, rebuildOnWrite = true)
        private String name;

        @EditorEditable(label = LangKeys.GUI_BLUEPRINT_FUNCTION_PARAMETER_TYPE, rebuildOnWrite = true)
        private @Nullable ScriptType type;

        public Parameter(final String name, final @Nullable ScriptType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public @Nullable ScriptType getType() {
            return type;
        }
    }
}
