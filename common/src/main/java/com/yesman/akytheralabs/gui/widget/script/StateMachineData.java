package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.stream.Stream;

/// The persisted contents of one Anim Graph [StateMachineNodeWidget] — the states and the transitions
/// between them. Keyed in the holder by the state machine's name (see
/// [AnimatorScriptHolder]).
///
/// **Builder-shaped** to mirror `AnimationStateMachine.stateModelBuilder()`: each transition is owned
/// by the state it leaves from (`from` is implicit), and the hardcoded Entry node's outgoing edges
/// are the top-level [#entryTransitions] (in the engine builder, `transitTo` calls before the first
/// `addState` attach to the entry state). State identities are [FSMAnimationState] signatures, not raw
/// strings — a state is created from a registered signature and never renamed, so the compiler maps a
/// state straight to `addState(signature, …)` with no `Identifier.parse`.
///
/// Each state owns its own pose graph (the `{state}` sub-tab's data-flow into "Output Animation
/// Pose") and each transition its own condition graph (the `{from} to {to}` sub-tab's boolean
/// data-flow into "Can Enter Transition"). Both are full [ScriptGraphData] sub-graphs, defaulting to
/// [ScriptGraphData#EMPTY] until edited.
public record StateMachineData(
    int entryX,
    int entryY,
    List<TransitionData> entryTransitions,
    List<StateData> states
) {
    /// The Entry node's default canvas position, used when a machine has no saved one (a graph saved
    /// before the Entry position was persisted, or a brand-new machine).
    public static final int DEFAULT_ENTRY_X = 30;
    public static final int DEFAULT_ENTRY_Y = 60;

    public static final StateMachineData EMPTY = new StateMachineData(DEFAULT_ENTRY_X, DEFAULT_ENTRY_Y, List.of(), List.of());

    public static final Codec<StateMachineData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.INT.optionalFieldOf("entry_x", DEFAULT_ENTRY_X).forGetter(StateMachineData::entryX),
                Codec.INT.optionalFieldOf("entry_y", DEFAULT_ENTRY_Y).forGetter(StateMachineData::entryY),
                TransitionData.CODEC.listOf().optionalFieldOf("entry_transitions", List.of()).forGetter(StateMachineData::entryTransitions),
                StateData.CODEC.listOf().optionalFieldOf("states", List.of()).forGetter(StateMachineData::states)
            ).apply(instance, StateMachineData::new)
    );

    public boolean isEmpty() {
        return states.isEmpty() && entryTransitions.isEmpty();
    }

    /// The shared rename-tracking instances this machine's sub-graphs bind — each state's pose graph
    /// and each transition's condition graph. The state signatures and transition targets themselves
    /// are the owning holder's ([AnimatorScriptHolder#referencedReferences]).
    public Stream<EditorReference> referencedReferences() {
        Stream<ScriptGraphData> graphs = Stream.concat(
            states.stream().flatMap(s -> Stream.concat(
                Stream.of(s.poseGraph()),
                s.transitions().stream().map(TransitionData::conditionGraph)
            )),
            entryTransitions.stream().map(TransitionData::conditionGraph)
        );

        return graphs.flatMap(ScriptGraphData::referencedReferences);
    }

    /// A copy with every cached-pose getter in any state's pose sub-graph retargeted from `oldName` to
    /// `newName` — used when a cached pose is renamed on the Anim Graph so getters nested in closed
    /// state sub-tabs follow it. (Transition condition graphs hold no pose getters, so they are left
    /// as-is.) Returns an equivalent machine; a state with no matching getter keeps its sub-graph.
    public StateMachineData withCachedPoseGettersRenamed(String oldName, String newName) {
        List<StateData> renamedStates = states.stream()
            .map(s -> new StateData(s.state(), s.x(), s.y(),
                s.poseGraph().withCachedPoseGettersRenamed(oldName, newName), s.transitions()))
            .toList();
        return new StateMachineData(entryX, entryY, entryTransitions, renamedStates);
    }

    /// One state: its [FSMAnimationState] signature (unique within the machine, also the `{state}`
    /// sub-tab title), its canvas position, its output-pose sub-graph, and the transitions leaving it.
    public record StateData(Holder<FSMAnimationState> state, int x, int y, ScriptGraphData poseGraph, List<TransitionData> transitions) {
        public static final Codec<StateData> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    DataBlockHolder.holderCodec(FSMAnimationState.ENUM_MANAGER).fieldOf("state").forGetter(StateData::state),
                    Codec.INT.fieldOf("x").forGetter(StateData::x),
                    Codec.INT.fieldOf("y").forGetter(StateData::y),
                    ScriptGraphData.CODEC.optionalFieldOf("pose_graph", ScriptGraphData.EMPTY).forGetter(StateData::poseGraph),
                    TransitionData.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(StateData::transitions)
                ).apply(instance, StateData::new)
        );
    }

    /// One directed transition to the `to` state and its boolean condition sub-graph; the `from` state
    /// is the owner (the [StateData] holding it, or Entry for an [#entryTransitions] entry). Direction
    /// matters: `a → b` and `b → a` are distinct, and a given direction is unique.
    public record TransitionData(Holder<FSMAnimationState> to, ScriptGraphData conditionGraph) {
        public static final Codec<TransitionData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                DataBlockHolder.holderCodec(FSMAnimationState.ENUM_MANAGER).fieldOf("to").forGetter(TransitionData::to),
                ScriptGraphData.CODEC.optionalFieldOf("condition_graph", ScriptGraphData.EMPTY).forGetter(TransitionData::conditionGraph)
            ).apply(instance, TransitionData::new)
        );
    }
}