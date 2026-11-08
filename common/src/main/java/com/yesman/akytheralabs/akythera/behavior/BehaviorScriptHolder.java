package com.yesman.akytheralabs.akythera.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorCondition;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorSubtask;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorTask;
import com.yesman.akythera.core.blueprint.behavior.BehaviorConditionBlueprint;
import com.yesman.akythera.core.blueprint.behavior.BehaviorSubtaskBlueprint;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTaskBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.ScriptBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/// Modifiable data holder for a data pack task, subtask or condition: one [BlueprintScriptData] laid out by the
/// node's script interface, with the brain as the graphs' Self. [#makeObject] lowers it into the [BlueprintBody] of
/// the blueprint.
public final class BehaviorScriptHolder<B> extends AbstractDataHolder<B> implements ScriptBlueprintHolder<B>, DTOProvider {
    public static final Kind<BehaviorTaskBlueprint> TASK =
        new Kind<>(DataDrivenBehaviorTask.class, BehaviorTaskBlueprint::new, BehaviorTaskBlueprint::body);

    public static final Kind<BehaviorSubtaskBlueprint> SUBTASK =
        new Kind<>(DataDrivenBehaviorSubtask.class, BehaviorSubtaskBlueprint::new, BehaviorSubtaskBlueprint::body);

    public static final Kind<BehaviorConditionBlueprint> CONDITION =
        new Kind<>(DataDrivenBehaviorCondition.class, BehaviorConditionBlueprint::new, BehaviorConditionBlueprint::body);

    private final Kind<B> kind;

    private BlueprintScriptData script = new BlueprintScriptData();

    public BehaviorScriptHolder(final Kind<B> kind) {
        this.kind = kind;
    }

    @Override
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(kind.owner()));
    }

    @Override
    public Class<?> scriptOwner() {
        return BrainComponent.class;
    }

    @Override
    public BlueprintScriptData getScript() {
        return script;
    }

    @Override
    public void readFrom(final B object) {
        BlueprintBody body = kind.body().apply(object);

        // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), body.properties(),
            body.variables().stream().map(BlueprintScriptData.Variable::of).toList());
        this.script.typeProperties(layout());
    }

    @Override
    public B makeObject() {
        return kind.constructor().apply(script.compile(layout(), BrainComponent.class));
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), BrainComponent.class, null);
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

    /// Tokens for `behavior_script_provider.ftl`: the body's `BlueprintBody` constructor.
    @Override
    public Map<String, Object> getDTO() {
        return Map.of("body", NodeTreeSourceRenderer.body(kind.body().apply(makeObject()), 4));
    }

    /// One kind of data pack behavior node.
    ///
    /// @param owner the node class built for each brain, which the script layout is registered for
    public record Kind<B>(Class<?> owner, Function<BlueprintBody, B> constructor, Function<B, BlueprintBody> body) {
        /// The editor codec of a holder of this kind.
        public Codec<BehaviorScriptHolder<B>> codec() {
            return RecordCodecBuilder.create(
                instance -> instance.group(
                    BlueprintScriptData.MAP_CODEC.forGetter(holder -> holder.script)
                ).apply(instance, script -> {
                    BehaviorScriptHolder<B> holder = new BehaviorScriptHolder<>(this);

                    holder.script = script;
                    script.typeProperties(holder.layout());

                    return holder;
                })
            );
        }
    }
}
