package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.api.world.ability.ActivationType;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.ResourceKey;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/// Modifiable data holder for [AbilityBlueprint].
///
/// The editor keeps each event as a flat [ScriptGraphData] — a node list carrying on-canvas ids and
/// positions, plus a wire list — while the runtime blueprint stores each as a tree of
/// [com.yesman.akythera.api.data.modularize.node.ScriptNode]s. [#makeObject] is the compile step.
public final class AbilityBlueprintHolder extends AbstractDataHolder<AbilityBlueprint> {
    /// The owner type an unwired owner ("Target") pin defaults to.
    private static final Class<?> OWNER_TYPE = Ability.class;

    /// One canvas per overridable entry point the ability editor offers.
    public enum Graph {
        ON_EQUIPPED("on_equipped_graph"),
        ON_UNEQUIPPED("on_unequipped_graph"),
        ON_ACTIVATED("on_activated_graph"),
        ON_RECAST("on_recast_graph"),
        ON_CANCELED("on_canceled_graph"),
        ON_FINISHED("on_finished_graph"),

        /// The one function graph, which answers with a [ReturnNode] rather than acting.
        CAN_ACTIVATE("can_activate_graph");

        private final String field;

        Graph(final String field) {
            this.field = field;
        }

        public String fieldName() {
            return field;
        }
    }

    public static final Codec<AbilityBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                .forGetter(h -> h.ctor),
            SystematicAbility.SettingsSpec.CODEC.fieldOf("settings").forGetter(AbilityBlueprintHolder::settings),
            Codec.unboundedMap(Codec.STRING, ScriptGraphData.CODEC).optionalFieldOf("graphs", Map.of())
                .forGetter(AbilityBlueprintHolder::graphsByName),
            ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                .forGetter(h -> h.variables)
        ).apply(instance, (ctor, settings, graphs, variables) -> {
            AbilityBlueprintHolder holder = new AbilityBlueprintHolder();

            holder.ctor = ctor;
            holder.applySettings(settings);
            holder.variables = new ArrayList<>(variables);

            for (Graph graph : Graph.values()) {
                ScriptGraphData data = graphs.get(graph.fieldName());

                if (data != null) holder.graphs.put(graph, data);
            }

            return holder;
        })
    );

    /// A new ability is data driven until the author picks otherwise, which is the only parent that
    /// runs the graphs this holder edits.
    private ScriptFunction ctor = AkytheraScriptFunctions.DATA_DRIVEN_ABILITY_CONSTRUCTOR.value();

    @EditorEditable
    private ActivationType activationType = ActivationType.INSTANT;

    /// The recharge timer banking this ability's charges.
    @EditorEditable
    private @Nullable ResourceKey<AbilityEffect> cooldownEffect;

    /// Present only on a Duration preset, whose expiry is what finishes the ability.
    @EditorEditable
    private @Nullable ResourceKey<AbilityEffect> durationEffect;

    /// The key this ability reads, required by the presets that stay activated by input.
    @EditorEditable
    private @Nullable ResourceKey<InputAction> inputAction;

    /// The parts of the spec the panel has no widget for yet, kept so they round-trip.
    private List<ResourceKey<AbilityEffect>> costs = List.of();

    private SystematicAbility.Tags tags = SystematicAbility.Tags.NONE;

    private List<SystematicAbility.Trigger> triggers = List.of();

    private final Map<Graph, ScriptGraphData> graphs = new EnumMap<>(Graph.class);

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    public AbilityBlueprintHolder() {
        for (Graph graph : Graph.values()) {
            graphs.put(graph, ScriptGraphData.EMPTY);
        }
    }

    /// The spec these fields describe.
    public SystematicAbility.SettingsSpec settings() {
        return new SystematicAbility.SettingsSpec(
            activationType,
            costs,
            Optional.ofNullable(cooldownEffect),
            Optional.ofNullable(durationEffect),
            Optional.ofNullable(inputAction),
            tags,
            triggers
        );
    }

    private void applySettings(final SystematicAbility.SettingsSpec spec) {
        this.activationType = spec.activationType();
        this.costs = List.copyOf(spec.costs());
        this.cooldownEffect = spec.cooldownEffect().orElse(null);
        this.durationEffect = spec.durationEffect().orElse(null);
        this.inputAction = spec.inputAction().orElse(null);
        this.tags = spec.tags();
        this.triggers = List.copyOf(spec.triggers());
    }

    @Override
    public void readFrom(final AbilityBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the flat fields round-trip.
        this.ctor = object.ctor();
        applySettings(object.settings());
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public AbilityBlueprint makeObject() {
        return new AbilityBlueprint(
            ctor,
            settings(),
            Optional.empty(),
            Optional.of(compile(Graph.CAN_ACTIVATE)),
            compile(Graph.ON_EQUIPPED),
            compile(Graph.ON_UNEQUIPPED),
            compile(Graph.ON_ACTIVATED),
            compile(Graph.ON_RECAST),
            compile(Graph.ON_CANCELED),
            compile(Graph.ON_FINISHED),
            List.copyOf(variables)
        );
    }

    private FlowNode compile(final Graph graph) {
        return ScriptGraphCompiler.compile(graphs.get(graph), OWNER_TYPE);
    }

    private Map<String, ScriptGraphData> graphsByName() {
        Map<String, ScriptGraphData> byName = new java.util.LinkedHashMap<>();

        graphs.forEach((graph, data) -> byName.put(graph.fieldName(), data));

        return byName;
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return graphs.values().stream().flatMap(ScriptGraphData::referencedReferences);
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        for (ScriptGraphData data : graphs.values()) {
            Exception failure = data.firstFailure();

            if (failure != null) return failure;
        }

        return null;
    }

    // ***************************************************************
    // Graphs
    // ***************************************************************

    public ScriptGraphData getGraph(final Graph graph) {
        return graphs.getOrDefault(graph, ScriptGraphData.EMPTY);
    }

    public void setGraph(final Graph graph, final ScriptGraphData data) {
        graphs.put(graph, data);
    }

    // ***************************************************************
    // Settings and variables
    // ***************************************************************

    public ScriptFunction getCtor() {
        return ctor;
    }

    public void setCtor(final ScriptFunction ctor) {
        this.ctor = ctor;
    }

    public void setSettings(final SystematicAbility.SettingsSpec settings) {
        applySettings(settings);
    }

    /// The live variable list. [#setVariables] replaces it with a copy.
    public List<ScriptVariables.VariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(final List<ScriptVariables.VariableInfo> variables) {
        this.variables = new ArrayList<>(variables);
    }
}
