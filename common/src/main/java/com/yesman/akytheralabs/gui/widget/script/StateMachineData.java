package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.animation.driver.AnimationStateTransition;
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
///
/// A [GroupData] bundles states that leave on the same transitions: the compile copies each of its
/// edges onto every member, so a group is an authoring device and never reaches the engine.
public record StateMachineData(
    int entryX,
    int entryY,
    List<TransitionData> entryTransitions,
    List<StateData> states,
    List<GroupData> groups
) {
    /// The Entry node's default canvas position, used when a machine has no saved one (a graph saved
    /// before the Entry position was persisted, or a brand-new machine).
    public static final int DEFAULT_ENTRY_X = 30;
    public static final int DEFAULT_ENTRY_Y = 60;

    public static final StateMachineData EMPTY =
        new StateMachineData(DEFAULT_ENTRY_X, DEFAULT_ENTRY_Y, List.of(), List.of(), List.of());

    public static final Codec<StateMachineData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.INT.optionalFieldOf("entry_x", DEFAULT_ENTRY_X).forGetter(StateMachineData::entryX),
                Codec.INT.optionalFieldOf("entry_y", DEFAULT_ENTRY_Y).forGetter(StateMachineData::entryY),
                TransitionData.CODEC.listOf().optionalFieldOf("entry_transitions", List.of()).forGetter(StateMachineData::entryTransitions),
                StateData.CODEC.listOf().optionalFieldOf("states", List.of()).forGetter(StateMachineData::states),
                GroupData.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(StateMachineData::groups)
            ).apply(instance, StateMachineData::new)
    );

    public boolean isEmpty() {
        return states.isEmpty() && entryTransitions.isEmpty();
    }

    /// The shared rename-tracking instances this machine's sub-graphs bind — each state's pose graph
    /// and each transition's condition graph, group edges included. The state signatures and transition
    /// targets themselves are the owning holder's ([AnimatorScriptHolder#referencedReferences]).
    public Stream<EditorReference> referencedReferences() {
        Stream<ScriptGraphData> graphs = Stream.concat(
            Stream.concat(
                states.stream().flatMap(s -> Stream.concat(
                    Stream.of(s.poseGraph()),
                    s.transitions().stream().map(TransitionData::conditionGraph)
                )),
                entryTransitions.stream().map(TransitionData::conditionGraph)
            ),
            groups.stream().flatMap(g -> g.transitions().stream().map(TransitionData::conditionGraph))
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
                s.poseGraph().withCachedPoseGettersRenamed(oldName, newName), s.transitions(), s.group()))
            .toList();
        return new StateMachineData(entryX, entryY, entryTransitions, renamedStates, groups);
    }

    /// One state: its [FSMAnimationState] signature (unique within the machine, also the `{state}`
    /// sub-tab title), its canvas position, its output-pose sub-graph, the transitions leaving it, and
    /// the name of the [GroupData] it belongs to — empty for none. A state is in at most one group.
    public record StateData(
        Holder<FSMAnimationState> state,
        int x,
        int y,
        ScriptGraphData poseGraph,
        List<TransitionData> transitions,
        String group
    ) {
        public static final Codec<StateData> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    DataBlockHolder.holderCodec(FSMAnimationState.ENUM_MANAGER).fieldOf("state").forGetter(StateData::state),
                    Codec.INT.fieldOf("x").forGetter(StateData::x),
                    Codec.INT.fieldOf("y").forGetter(StateData::y),
                    ScriptGraphData.CODEC.optionalFieldOf("pose_graph", ScriptGraphData.EMPTY).forGetter(StateData::poseGraph),
                    TransitionData.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(StateData::transitions),
                    Codec.STRING.optionalFieldOf("group", "").forGetter(StateData::group)
                ).apply(instance, StateData::new)
        );
    }

    /// One state group: an editor-only name and the transitions every member leaves on. The compile
    /// copies each of these onto every state naming this group in [StateData#group].
    ///
    /// A group carries no geometry: its box on the canvas is the bounding box of its members, and a
    /// group with no member is dropped.
    public record GroupData(String name, List<TransitionData> transitions) {
        public static final Codec<GroupData> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(GroupData::name),
                    TransitionData.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(GroupData::transitions)
                ).apply(instance, GroupData::new)
        );
    }

    /// One directed transition to the `to` state and its boolean condition sub-graph, with the time it
    /// cross-fades over, whether the state it leaves hands its elapsed time to the one it enters, and
    /// the priority it is taken at; the source is the owner — the [StateData] holding it, the
    /// [GroupData] holding it, or Entry for an [#entryTransitions] entry. Direction matters: `a → b`
    /// and `b → a` are distinct, and a given direction is unique. The target is always a state: a
    /// machine occupies one state at a time, so a group can never be a target.
    ///
    /// `blendTime`, `transferElapsedTime` and `transitionPriority` are the like-named
    /// [AnimationStateTransition] components.
    public record TransitionData(
        Holder<FSMAnimationState> to, ScriptGraphData conditionGraph,
        float blendTime, boolean transferElapsedTime, int transitionPriority
    ) {
        /// Blend time of a freshly drawn transition, in seconds.
        public static final float DEFAULT_BLEND_TIME = 0.2F;

        /// Elapsed-time transfer of a freshly drawn transition: the entered state starts from zero.
        public static final boolean DEFAULT_TRANSFER_ELAPSED_TIME = false;

        /// Priority of a freshly drawn transition: every edge ranks equally until one is raised.
        public static final int DEFAULT_TRANSITION_PRIORITY = 0;

        public static final Codec<TransitionData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                DataBlockHolder.holderCodec(FSMAnimationState.ENUM_MANAGER).fieldOf("to").forGetter(TransitionData::to),
                ScriptGraphData.CODEC.optionalFieldOf("condition_graph", ScriptGraphData.EMPTY).forGetter(TransitionData::conditionGraph),
                Codec.FLOAT.optionalFieldOf("blend_time", DEFAULT_BLEND_TIME).forGetter(TransitionData::blendTime),
                Codec.BOOL.optionalFieldOf("transfer_elapsed_time", DEFAULT_TRANSFER_ELAPSED_TIME).forGetter(TransitionData::transferElapsedTime),
                Codec.INT.optionalFieldOf("transition_priority", DEFAULT_TRANSITION_PRIORITY).forGetter(TransitionData::transitionPriority)
            ).apply(instance, TransitionData::new)
        );
    }
}
