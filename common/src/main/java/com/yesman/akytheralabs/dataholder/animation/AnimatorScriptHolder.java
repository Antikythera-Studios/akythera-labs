package com.yesman.akytheralabs.dataholder.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Modifiable data holder for [DataDrivenAnimatorScript.AnimatorScriptBlueprint].
///
/// Unlike the runtime blueprint — which stores each flow as a *tree* of
/// [com.yesman.akythera.api.data.modularize.node.ScriptNode]s — the editor keeps each of the three
/// flow entry points (`construct` / `update` / `pose`) as a **flat** [ScriptGraphData]: a node
/// list (with on-canvas ids + positions) and a wire list. This is the only form that round-trips
/// the graph layout, and it is what [#EDITOR_CODEC] writes into the `akythera_projects` file.
///
/// [#makeObject] is the "compile" step: it lowers each flat graph back into a tree
/// [FlowNode] (via [ScriptGraphCompiler]) and assembles the runtime blueprint the engine executes.
/// The [#EDITOR_CODEC] additionally persists the editor-only `preview_model` (the model picked
/// before the editor opens), which the runtime blueprint codec ignores.
public final class AnimatorScriptHolder extends AbstractDataHolder<DataDrivenAnimatorScript.AnimatorScriptBlueprint> {
    /// The script owner type for an animator script. Drives [ScriptGraphCompiler]'s `self`
    /// defaulting of unwired owner ("Target") pins.
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

    /// Each Anim Graph state machine's topology, keyed by the [com.yesman.akytheralabs.gui.widget.script.StateMachineNodeWidget]'s
    /// unique name (insertion-ordered so the file is stable).
    private Map<String, StateMachineData> stateMachines = new LinkedHashMap<>();

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    @Nullable
    private EntityPreviewModel previewModel;

    public AnimatorScriptHolder() {
    }

    @Override
    public void readFrom(DataDrivenAnimatorScript.AnimatorScriptBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so the flat graphs cannot be
        // reconstructed from it (decompiling the tree is future work). Only the variables — which
        // do round-trip — are recovered; the editor itself always loads via EDITOR_CODEC, not here.
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public DataDrivenAnimatorScript.AnimatorScriptBlueprint makeObject() {
        // The Event Graph canvas holds both events as entry nodes in `updateGraph`: the Constructor
        // entry (no data outputs) compiles into `construct`, the Update entry (frameTime / partialTick)
        // into `update`. The legacy separate `constructGraph` is unused (never written by the editor).
        FlowNode eventConstruct = ScriptGraphCompiler.compile(updateGraph, OWNER_TYPE, ScriptGraphCompiler.EntrySelector.CONSTRUCT);
        FlowNode update = ScriptGraphCompiler.compile(updateGraph, OWNER_TYPE, ScriptGraphCompiler.EntrySelector.UPDATE);

        // The Anim Graph is data-flow into Output Pose, not a flow graph: it lowers to a construct
        // step (build the root driver once + store it as a layer, and register each cached pose's driver
        // in the animator's pose cache) plus the poseGetter (read the layer back).
        AnimGraphCompiler.Result anim = AnimGraphCompiler.compile(poseGraph, stateMachines, OWNER_TYPE);
        FlowNode construct = anim.storeLayer() == null
            ? eventConstruct
            : new SequenceNode(List.of(eventConstruct, anim.storeLayer()));

        // Cached poses are now engine-side: they are registered at construct time via poseCaches.put
        // (see AnimGraphCompiler), so the blueprint's legacy cachedPoseProviders map stays empty.
        return new DataDrivenAnimatorScript.AnimatorScriptBlueprint(
            construct, update, anim.poseGetter(), List.copyOf(variables)
        );
    }

    /// ***************************************************************
    /// Flow graphs (flat editor form)
    /// ***************************************************************
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

    /// Every data block reference held by this script's state machines (state signatures +
    /// transition targets) that is a project [EditorReference] (a `ProjectObjectHolder`; engine
    /// blocks are direct holders and don't rename). Used by the rename re-save scan.
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

        return refs.stream();
    }

    private static void addRef(List<EditorReference> out, Holder<?> holder) {
        if (holder instanceof EditorReference ref) out.add(ref);
    }

    /// ***************************************************************
    /// State machines (Anim Graph), keyed by machine name
    /// ***************************************************************
    public Map<String, StateMachineData> getStateMachines() {
        return stateMachines;
    }

    public void setStateMachines(Map<String, StateMachineData> stateMachines) {
        this.stateMachines = new LinkedHashMap<>(stateMachines);
    }

    /// ***************************************************************
    /// Variables
    /// ***************************************************************

    /// Live, mutable variable list — the editor's Variables panel edits a working copy and flushes
    /// it back through [#setVariables] on save.
    public List<ScriptVariables.VariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(List<ScriptVariables.VariableInfo> variables) {
        this.variables = new ArrayList<>(variables);
    }

    /// ***************************************************************
    /// Preview model (editor-only)
    /// ***************************************************************
    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }
}
