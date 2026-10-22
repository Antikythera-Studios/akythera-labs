package com.yesman.akytheralabs.akythera.ability;

import com.google.common.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/// Modifiable data holder for [AbilityBlueprint].
///
/// The editor keeps the graphs, property values and variables as one [BlueprintScriptData], laid out by the
/// [Ability] class the parent constructor builds. [#makeObject] is the compile step: it lowers the data into
/// a [BlueprintBody].
public final class AbilityBlueprintHolder extends AbstractDataHolder<AbilityBlueprint> {
    public static final Codec<AbilityBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                .forGetter(h -> h.ctor),
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script)
        ).apply(instance, (ctor, script) -> {
            AbilityBlueprintHolder holder = new AbilityBlueprintHolder();

            holder.ctor = ctor;
            holder.script = script;

            return holder;
        })
    );

    /// A new ability is data driven until the author picks otherwise, which is the only parent that
    /// runs the graphs this holder edits.
    private ScriptFunction ctor = AkytheraScriptFunctions.SYSTEMATIC_ABILITY_CONSTRUCTOR.value();

    private BlueprintScriptData script = new BlueprintScriptData();

    /// The ability class the parent constructor builds, the script owner of every canvas.
    public Class<?> ownerType() {
        return ctor.functionSignature().returnType();
    }

    /// The members [#ownerType] lays the editor out with.
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(ownerType()));
    }

    /// The state type parameter of the class `ctor` builds that the exported ability's own state class
    /// binds. Null when the parent binds its state itself, which leaves the blueprint no place for variables.
    public static @Nullable TypeVariable<?> stateSlot(final ScriptFunction ctor) {
        Type state = TypeToken.of(ctor.functionSignature().returnType())
            .resolveType(Ability.class.getTypeParameters()[0])
            .getType();

        return state instanceof TypeVariable<?> slot ? slot : null;
    }

    @Override
    public void readFrom(final AbilityBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
        this.ctor = object.ctor();
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), object.body().properties(),
            object.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
    }

    @Override
    public AbilityBlueprint makeObject() {
        return new AbilityBlueprint(ctor, script.compile(layout(), ownerType()));
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), ownerType(), null);
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

    public ScriptFunction getCtor() {
        return ctor;
    }

    public void setCtor(final ScriptFunction ctor) {
        this.ctor = ctor;
    }

    public BlueprintScriptData getScript() {
        return script;
    }
}
