package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// Modifiable data holder for [AbilityCueBlueprint].
///
/// A cue has one entry point, so the editor keeps one flat [ScriptGraphData] and [#makeObject]
/// lowers it into the runtime tree.
public final class AbilityCueBlueprintHolder extends AbstractDataHolder<AbilityCueBlueprint> {
    /// The owner type an unwired owner ("Target") pin defaults to.
    private static final Class<?> OWNER_TYPE = AbilityCue.class;

    public static final Codec<AbilityCueBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AbilityTag.ENUM_MANAGER.getNameCodec().listOf().optionalFieldOf("tags", List.of())
                .forGetter(h -> List.copyOf(h.tags)),
            Codec.BOOL.optionalFieldOf("ends_with_ability", true).forGetter(h -> h.endsWithAbility),
            Codec.INT.optionalFieldOf("duration", 0).forGetter(h -> h.duration),
            ScriptGraphData.CODEC.optionalFieldOf("on_execute_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.onExecuteGraph),
            ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                .forGetter(h -> h.variables)
        ).apply(instance, (tags, endsWithAbility, duration, graph, variables) -> {
            AbilityCueBlueprintHolder holder = new AbilityCueBlueprintHolder();

            holder.tags = new LinkedHashSet<>(tags);
            holder.endsWithAbility = endsWithAbility;
            holder.duration = duration;
            holder.onExecuteGraph = graph;
            holder.variables = new ArrayList<>(variables);

            return holder;
        })
    );

    private Set<AbilityTag> tags = new LinkedHashSet<>();

    /// Seeds each play's own flag, which a play may override.
    @EditorEditable
    private boolean endsWithAbility = true;

    /// Ticks the cue runs for. Zero or less runs until something cancels it.
    @EditorEditable
    private int duration;

    private ScriptGraphData onExecuteGraph = ScriptGraphData.EMPTY;

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    @Override
    public void readFrom(final AbilityCueBlueprint object) {
        this.tags = new LinkedHashSet<>(object.tags());
        this.endsWithAbility = object.endsWithAbility();
        this.duration = object.duration();
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public AbilityCueBlueprint makeObject() {
        return new AbilityCueBlueprint(
            Set.copyOf(tags),
            endsWithAbility,
            duration,
            ScriptGraphCompiler.compile(onExecuteGraph, OWNER_TYPE),
            List.copyOf(variables)
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return onExecuteGraph.referencedReferences();
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return onExecuteGraph.firstFailure();
    }

    public Set<AbilityTag> getTags() {
        return tags;
    }

    public void setTags(final Set<AbilityTag> tags) {
        this.tags = new LinkedHashSet<>(tags);
    }

    public boolean endsWithAbility() {
        return endsWithAbility;
    }

    public void setEndsWithAbility(final boolean endsWithAbility) {
        this.endsWithAbility = endsWithAbility;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(final int duration) {
        this.duration = duration;
    }

    public ScriptGraphData getOnExecuteGraph() {
        return onExecuteGraph;
    }

    public void setOnExecuteGraph(final ScriptGraphData onExecuteGraph) {
        this.onExecuteGraph = onExecuteGraph;
    }

    /// The live variable list. [#setVariables] replaces it with a copy.
    public List<ScriptVariables.VariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(final List<ScriptVariables.VariableInfo> variables) {
        this.variables = new ArrayList<>(variables);
    }
}
