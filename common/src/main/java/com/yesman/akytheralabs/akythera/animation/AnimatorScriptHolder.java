package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import com.yesman.akytheralabs.gui.widget.script.node.EntryPointNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.MethodEntryNodeData;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Modifiable data holder for [DataDrivenAnimatorScript.AnimatorScriptBlueprint].
///
/// The editor keeps the Event Graph, the function canvases, the property values and the variables as one
/// [BlueprintScriptData] laid out by [AnimatorScript], and the Anim Graph (`pose`) as a flat
/// [ScriptGraphData] with its state machines. [#EDITOR_CODEC] writes them into the `akythera_projects` file
/// along with the editor-only `preview_entity_type`. The runtime blueprint stores each flow as a tree of
/// [ScriptNode]s instead.
///
/// [#makeObject] is the compile step: it lowers the data into a [BlueprintBody], appends the Anim Graph's
/// construct step to the `construct` event, and assembles the runtime blueprint.
public final class AnimatorScriptHolder extends AbstractDataHolder<DataDrivenAnimatorScript.AnimatorScriptBlueprint> {
    /// The script owner type an unwired owner ("Target") pin defaults to.
    private static final Class<?> OWNER_TYPE = Animator.class;

    public static final Codec<AnimatorScriptHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script),
            ScriptGraphData.CODEC.optionalFieldOf("update_graph").forGetter(_ -> Optional.empty()),
            ScriptGraphData.CODEC.optionalFieldOf("pose_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.poseGraph),
            Codec.unboundedMap(Codec.STRING, StateMachineData.CODEC)
                .optionalFieldOf("state_machines", Map.of())
                .forGetter(h -> h.stateMachines),
            Identifier.CODEC.optionalFieldOf("preview_entity_type")
                .forGetter(ParseUtils.provideOptional(AnimatorScriptHolder::getPreviewEntityType))
        ).apply(instance, (script, updateGraph, pose, machines, pm) -> {
            AnimatorScriptHolder h = new AnimatorScriptHolder();
            h.script = script;
            h.poseGraph = pose;
            h.stateMachines = new LinkedHashMap<>(machines);
            pm.ifPresent(h::setPreviewEntityType);

            // A project that keeps the Event Graph under `update_graph` tells its two entry rows apart
            // by their labels: the one with data outputs is `update`.
            if (script.getEventGraph().isEmpty()) {
                updateGraph.ifPresent(graph -> script.setEventGraph(graph.mapNodes(data ->
                    data instanceof EntryPointNodeData entry
                        ? new MethodEntryNodeData(entry.hasDataOutputs() ? DataDrivenAnimatorScript.UPDATE : DataDrivenAnimatorScript.CONSTRUCT)
                        : data)));
            }

            return h;
        })
    );

    private BlueprintScriptData script = new BlueprintScriptData();
    private ScriptGraphData poseGraph = ScriptGraphData.EMPTY;

    /// Each Anim Graph state machine's topology, keyed by machine name and insertion-ordered so the
    /// file is stable.
    private Map<String, StateMachineData> stateMachines = new LinkedHashMap<>();

    @Nullable
    private Identifier previewEntityType;

    public AnimatorScriptHolder() {
    }

    /// The members [AnimatorScript] lays the editor out with.
    public static BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(OWNER_TYPE));
    }

    @Override
    public void readFrom(DataDrivenAnimatorScript.AnimatorScriptBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), object.body().properties(),
            object.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
    }

    @Override
    public DataDrivenAnimatorScript.AnimatorScriptBlueprint makeObject() {
        BlueprintLayout layout = layout();
        BlueprintBody body = script.compile(layout, OWNER_TYPE);

        // The Anim Graph contributes a construct step of its own plus the poseGetter.
        AnimGraphCompiler.Result anim = AnimGraphCompiler.compile(poseGraph, stateMachines, OWNER_TYPE);

        if (anim.storeLayer() != null) {
            String construct = Objects.requireNonNull(layout.method(DataDrivenAnimatorScript.CONSTRUCT)).key();
            Map<String, FlowNode> methods = new LinkedHashMap<>(body.methods());
            FlowNode eventConstruct = methods.get(construct);

            methods.put(construct, eventConstruct == null
                ? anim.storeLayer()
                : new SequenceNode(List.of(eventConstruct, anim.storeLayer())));

            body = new BlueprintBody(methods, body.functions(), body.properties(), body.variables());
        }

        // Cached poses register themselves at construct time.
        return new DataDrivenAnimatorScript.AnimatorScriptBlueprint(body, anim.poseGetter());
    }

    // ***************************************************************
    // Flow graphs (flat editor form)
    // ***************************************************************
    public BlueprintScriptData getScript() {
        return script;
    }

    public ScriptGraphData getPoseGraph() {
        return poseGraph;
    }

    public void setPoseGraph(ScriptGraphData graph) {
        this.poseGraph = graph;
    }

    /// Every project [EditorReference] this script binds: the state machines' state signatures and
    /// transition targets, plus whatever the blueprint's canvases, the Anim Graph and each state /
    /// transition sub-graph reference. Engine data blocks are direct holders and do not appear here.
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

        return Stream.of(
            refs.stream(),
            script.referencedReferences(),
            poseGraph.referencedReferences(),
            stateMachines.values().stream().flatMap(StateMachineData::referencedReferences)
        ).flatMap(references -> references);
    }

    private static void addRef(List<EditorReference> out, Holder<?> holder) {
        if (holder instanceof EditorReference ref) out.add(ref);
    }

    /// Corruption this script carries while staying structurally valid, of two kinds:
    ///
    ///   - an unresolved node the decode guard salvaged, on a blueprint canvas, the Anim Graph or any
    ///     state-machine pose / condition sub-graph;
    ///   - a missing reference — an unbound state signature, transition target, anim-layer signature
    ///     or interface key, or a pose player bound to an asset that resolves nowhere. The editor
    ///     draws these as a red `MISSING!` state node or an errored player node, and [#makeObject]
    ///     skips them with a warning.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception e;
        if ((e = script.firstFailure()) != null) return e;
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
    // Preview model (editor-only)
    // ***************************************************************
    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    public void setPreviewEntityType(@Nullable Identifier previewEntityType) {
        this.previewEntityType = previewEntityType;
    }
}
