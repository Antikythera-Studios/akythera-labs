package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.ability.AbilityResourceScript;
import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.ScriptBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Modifiable data holder for [AbilityResourceBlueprint].
///
/// The editor keeps the graphs, property values and variables as one [BlueprintScriptData], laid out by the
/// [AbilityResourceScript] class the parent constructor builds. [#makeObject] is the compile step: it lowers the data into
/// a [BlueprintBody].
public final class AbilityResourceBlueprintHolder extends AbstractDataHolder<AbilityResourceBlueprint>
    implements ScriptBlueprintHolder<AbilityResourceBlueprint>
{
    public static final Codec<AbilityResourceBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                .forGetter(h -> h.ctor),
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script.withPropertiesOf(h.layout()))
        ).apply(instance, (ctor, script) -> {
            AbilityResourceBlueprintHolder holder = new AbilityResourceBlueprintHolder();

            holder.ctor = ctor;
            holder.script = script;
            script.typeProperties(holder.layout());

            return holder;
        })
    );

    private ScriptFunction ctor = AkytheraScriptFunctions.ABILITY_RESOURCE_CONSTRUCTOR.value();

    private BlueprintScriptData script = new BlueprintScriptData();

    @Override
    public BlueprintLayout layout() {
        return ScriptModularizeHelper.scanBlueprintLayout(scriptOwner());
    }

    /// The resource class the parent constructor builds.
    @Override
    public Class<?> scriptOwner() {
        return ctor.functionSignature().returnType();
    }

    @Override
    public void readFrom(final AbilityResourceBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
        this.ctor = object.ctor();
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), object.body().properties(),
            object.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
        this.script.typeProperties(layout());
    }

    @Override
    public AbilityResourceBlueprint makeObject() {
        return new AbilityResourceBlueprint(ctor, script.compile(layout(), scriptOwner()));
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), scriptOwner(), null);
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

    @Override
    public BlueprintScriptData getScript() {
        return script;
    }

    /// Every resource constructor whose class can be inherited from.
    @Override
    public List<ScriptFunction> parentCandidates() {
        return AkytheraRegistries.Frozen.SCRIPT_FUNCTION.stream()
            .filter(function -> function.metadata().usage() == ScriptFunction.Usage.ABILITY_RESOURCE_CTOR)
            .filter(function -> !Modifier.isFinal(function.functionSignature().returnType().getModifiers()))
            .toList();
    }

    @Override
    public ScriptFunction getParent() {
        return ctor;
    }

    @Override
    public void setParent(final ScriptFunction parent) {
        this.ctor = parent;
        script.typeProperties(layout());
    }
}
