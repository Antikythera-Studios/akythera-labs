package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/// Modifiable data holder for [AbilityCueBlueprint].
///
/// The editor keeps the graphs, property values and variables as one [BlueprintScriptData], laid out by
/// [AbilityCueScript] with [AbilityCue] as the script owner. [#makeObject] is the compile step: it lowers the
/// data into a [BlueprintBody].
public final class AbilityCueBlueprintHolder extends AbstractDataHolder<AbilityCueBlueprint> {
    public static final Codec<AbilityCueBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script)
        ).apply(instance, script -> {
            AbilityCueBlueprintHolder holder = new AbilityCueBlueprintHolder();

            holder.script = script;

            return holder;
        })
    );

    private BlueprintScriptData script = new BlueprintScriptData();

    /// The members of [AbilityCueScript] the editor is laid out with.
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(AbilityCue.class));
    }

    @Override
    public void readFrom(final AbilityCueBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), object.body().properties(),
            object.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
    }

    @Override
    public AbilityCueBlueprint makeObject() {
        return new AbilityCueBlueprint(script.compile(layout(), AbilityCue.class));
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), AbilityCue.class);
        compilation.unresolved(script.failedNodes());
        compilation.build(this::makeObject);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return script.referencedReferences();
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return script.firstFailure();
    }

    public BlueprintScriptData getScript() {
        return script;
    }
}
