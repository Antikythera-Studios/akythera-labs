package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Modifiable data holder for [DataDrivenAnimatorScript.AnimatorScriptBlueprint].
///
/// The editor keeps each of the three flow entry points (`construct` / `update` / `pose`) as a flat
/// [ScriptGraphData] — a node list carrying on-canvas ids and positions, plus a wire list — which
/// [#EDITOR_CODEC] writes into the `akythera_projects` file along with the editor-only `preview_model`. The
/// runtime blueprint stores each flow as a tree of [ScriptNode]s instead.
///
/// [#makeObject] is the compile step: it lowers each flat graph back into a tree [FlowNode] through
/// [ScriptGraphCompiler] and assembles the runtime blueprint.
public final class AnimatorScriptHolder extends AbstractDataHolder<DataDrivenAnimatorScript.AnimatorScriptBlueprint> {
    /// The script owner type an unwired owner ("Target") pin defaults to.
    private static final Class<?> OWNER_TYPE = Animator.class;

    public static final Codec<AnimatorScriptHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            ScriptGraphData.CODEC.optionalFieldOf("construct_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.constructGraph),
            ScriptGraphData.CODEC.optionalFieldOf("update_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.updateGraph),
            ScriptGraphData.CODEC.optionalFieldOf("pose_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.poseGraph),
            Codec.unboundedMap(Codec.STRING, StateMachineData.CODEC)
                .optionalFieldOf("state_machines", Map.of())
                .forGetter(h -> h.stateMachines),
            ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                .forGetter(h -> h.variables),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(AnimatorScriptHolder::getPreviewModel))
        ).apply(instance, (construct, update, pose, machines, variables, pm) -> {
            AnimatorScriptHolder h = new AnimatorScriptHolder();
            h.constructGraph = construct;
            h.updateGraph = update;
            h.poseGraph = pose;
            h.stateMachines = new LinkedHashMap<>(machines);
            h.variables = new ArrayList<>(variables);
            pm.ifPresent(h::setPreviewModel);
            return h;
        })
    );

    private ScriptGraphData constructGraph = ScriptGraphData.EMPTY;
    private ScriptGraphData updateGraph = ScriptGraphData.EMPTY;
    private ScriptGraphData poseGraph = ScriptGraphData.EMPTY;

    /// Each Anim Graph state machine's topology, keyed by machine name and insertion-ordered so the
    /// file is stable.
    private Map<String, StateMachineData> stateMachines = new LinkedHashMap<>();

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    @Nullable
    private EntityPreviewModel previewModel;

    public AnimatorScriptHolder() {
    }

    @Override
    public void readFrom(DataDrivenAnimatorScript.AnimatorScriptBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the variables round-trip.
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public DataDrivenAnimatorScript.AnimatorScriptBlueprint makeObject() {
        // Both events live as entry nodes in `updateGraph`: the Constructor entry (no data outputs)
        // compiles into `construct`, the Update entry (frameTime / partialTick) into `update`.
        // `constructGraph` is a legacy field the editor never writes.
        FlowNode eventConstruct = ScriptGraphCompiler.compile(updateGraph, OWNER_TYPE, ScriptGraphCompiler.EntrySelector.CONSTRUCT);
        FlowNode update = ScriptGraphCompiler.compile(updateGraph, OWNER_TYPE, ScriptGraphCompiler.EntrySelector.UPDATE);

        // The Anim Graph contributes a construct step of its own plus the poseGetter.
        AnimGraphCompiler.Result anim = AnimGraphCompiler.compile(poseGraph, stateMachines, OWNER_TYPE);
        FlowNode construct = anim.storeLayer() == null
            ? eventConstruct
            : new SequenceNode(List.of(eventConstruct, anim.storeLayer()));

        // Cached poses register themselves at construct time, so the blueprint's legacy
        // cachedPoseProviders map stays empty.
        return new DataDrivenAnimatorScript.AnimatorScriptBlueprint(
            construct, update, anim.poseGetter(), List.copyOf(variables)
        );
    }

    // ***************************************************************
    // Flow graphs (flat editor form)
    // ***************************************************************
    public ScriptGraphData getConstructGraph() {
        return constructGraph;
    }

    public void setConstructGraph(ScriptGraphData graph) {
        this.constructGraph = graph;
    }

    public ScriptGraphData getUpdateGraph() {
        return updateGraph;
    }

    public void setUpdateGraph(ScriptGraphData graph) {
        this.updateGraph = graph;
    }

    public ScriptGraphData getPoseGraph() {
        return poseGraph;
    }

    public void setPoseGraph(ScriptGraphData graph) {
        this.poseGraph = graph;
    }

    /// Every project [EditorReference] this script binds: the state machines' state signatures and
    /// transition targets, plus whatever the Event Graph, the Anim Graph and each state / transition
    /// sub-graph reference. Engine data blocks are direct holders and do not appear here.
    @Override
    public Stream<EditorReference> referencedReferences() {
        List<EditorReference> refs = new ArrayList<>();

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.TransitionData t : machine.entryTransitions()) addRef(refs, t.to());

            for (StateMachineData.StateData s : machine.states()) {
                addRef(refs, s.state());
                for (StateMachineData.TransitionData t : s.transitions()) addRef(refs, t.to());
            }
        }

        return Stream.concat(
            refs.stream(),
            Stream.concat(
                Stream.of(constructGraph, updateGraph, poseGraph).flatMap(ScriptGraphData::referencedReferences),
                stateMachines.values().stream().flatMap(StateMachineData::referencedReferences)
            )
        );
    }

    private static void addRef(List<EditorReference> out, Holder<?> holder) {
        if (holder instanceof EditorReference ref) out.add(ref);
    }

    /// Corruption this script carries while staying structurally valid, of two kinds:
    ///
    ///   - an unresolved node the decode guard salvaged, on the Event / Anim Graph or any
    ///     state-machine pose / condition sub-graph;
    ///   - a missing reference — an unbound state signature, transition target, anim-layer signature
    ///     or interface key, or a pose player bound to an asset that resolves nowhere. The editor
    ///     draws these as a red `MISSING!` state node or an errored player node, and [#makeObject]
    ///     skips them with a warning.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception e;
        if ((e = constructGraph.firstFailure()) != null) return e;
        if ((e = updateGraph.firstFailure()) != null) return e;
        if ((e = poseGraph.firstFailure()) != null) return e;

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData s : machine.states()) {
                if ((e = s.poseGraph().firstFailure()) != null) return e;

                for (StateMachineData.TransitionData t : s.transitions()) {
                    if ((e = t.conditionGraph().firstFailure()) != null) return e;
                }
            }

            for (StateMachineData.TransitionData t : machine.entryTransitions()) {
                if ((e = t.conditionGraph().firstFailure()) != null) return e;
            }
        }

        List<Identifier> missing = AnimGraphCompiler.findMissingReferences(poseGraph, stateMachines);
        if (!missing.isEmpty()) {
            return new IllegalStateException("Missing reference(s): "
                + missing.stream().map(Identifier::toString).distinct().collect(Collectors.joining(", ")));
        }

        return null;
    }

    // ***************************************************************
    // State machines (Anim Graph), keyed by machine name
    // ***************************************************************
    public Map<String, StateMachineData> getStateMachines() {
        return stateMachines;
    }

    public void setStateMachines(Map<String, StateMachineData> stateMachines) {
        this.stateMachines = new LinkedHashMap<>(stateMachines);
    }

    // ***************************************************************
    // Variables
    // ***************************************************************

    /// The live variable list. [#setVariables] replaces it with a copy.
    public List<ScriptVariables.VariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(List<ScriptVariables.VariableInfo> variables) {
        this.variables = new ArrayList<>(variables);
    }

    // ***************************************************************
    // Preview model (editor-only)
    // ***************************************************************
    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }
}
