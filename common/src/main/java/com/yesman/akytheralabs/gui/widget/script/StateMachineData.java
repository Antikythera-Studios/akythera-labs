package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.api.world.behavior.AkytheraMobBehaviorState;
import com.yesman.akythera.api.world.behavior.MobBehaviorState;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.animation.driver.AnimationStateTransition;
import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeData;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// The persisted contents of one state machine canvas — the states and the transitions between them. Keyed in the
/// holder by the state machine's name (see [AnimatorScriptHolder]).
///
/// **Builder-shaped** to mirror the engine's `StateModel.StateModelBuilder`: each transition is owned by the state it
/// leaves from (`from` is implicit), and the hardcoded Entry node's outgoing edges are the top-level
/// [#entryTransitions]. State identities are the [Kind]'s signatures, not raw strings — a state is created from a
/// registered signature and never renamed.
///
/// What a state runs and how a transition is taken depend on the machine's [Kind]: each state and transition carries
/// a [StatePart] and a [TransitionPart] of that kind. Every transition has its own condition graph (the
/// `{from} to {to}` sub-tab's boolean data-flow into "Can Enter Transition").
///
/// A [GroupData] is a canvas node whose transitions every state declaring its name in
/// [StateData#groups] inherits. The compile copies them onto the members, so a group never reaches
/// the engine.
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

    /// The codec of an animation state machine.
    public static final Codec<StateMachineData> CODEC = codec(Kind.ANIMATION);

    /// The codec of a machine of `kind`.
    public static Codec<StateMachineData> codec(final Kind<?> kind) {
        Codec<TransitionData> transitionCodec = TransitionData.codec(kind);

        return RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.INT.optionalFieldOf("entry_x", DEFAULT_ENTRY_X).forGetter(StateMachineData::entryX),
                    Codec.INT.optionalFieldOf("entry_y", DEFAULT_ENTRY_Y).forGetter(StateMachineData::entryY),
                    transitionCodec.listOf().optionalFieldOf("entry_transitions", List.of()).forGetter(StateMachineData::entryTransitions),
                    StateData.codec(kind, transitionCodec).listOf().optionalFieldOf("states", List.of()).forGetter(StateMachineData::states),
                    GroupData.codec(transitionCodec).listOf().optionalFieldOf("groups", List.of()).forGetter(StateMachineData::groups)
                ).apply(instance, StateMachineData::new)
        );
    }

    public boolean isEmpty() {
        return states.isEmpty() && entryTransitions.isEmpty();
    }

    /// The shared rename-tracking instances this machine's sub-graphs bind — each animation state's pose graph and
    /// each transition's condition graph, group edges included. The state signatures and transition targets
    /// themselves are the owning holder's ([AnimatorScriptHolder#referencedReferences]).
    public Stream<EditorReference> referencedReferences() {
        Stream<ScriptGraphData> graphs = Stream.concat(
            Stream.concat(
                states.stream().flatMap(s -> Stream.concat(
                    s.part() instanceof AnimationStatePart animation ? Stream.of(animation.poseGraph()) : Stream.empty(),
                    s.transitions().stream().map(TransitionData::conditionGraph)
                )),
                entryTransitions.stream().map(TransitionData::conditionGraph)
            ),
            groups.stream().flatMap(g -> g.transitions().stream().map(TransitionData::conditionGraph))
        );

        return graphs.flatMap(ScriptGraphData::referencedReferences);
    }

    /// A copy with every cached-pose getter in any animation state's pose sub-graph retargeted from `oldName` to
    /// `newName` — used when a cached pose is renamed on the Anim Graph so getters nested in closed state sub-tabs
    /// follow it. (Transition condition graphs hold no pose getters, so they are left as-is.)
    public StateMachineData withCachedPoseGettersRenamed(String oldName, String newName) {
        List<StateData> renamedStates = states.stream()
            .map(s -> s.part() instanceof AnimationStatePart animation
                ? new StateData(s.state(), s.x(), s.y(),
                    new AnimationStatePart(animation.poseGraph().withCachedPoseGettersRenamed(oldName, newName)), s.transitions(), s.groups())
                : s)
            .toList();
        return new StateMachineData(entryX, entryY, entryTransitions, renamedStates, groups);
    }

    /// What one kind of state machine is made of: its signatures, its reserved entry and exit states, and the parts
    /// its states and transitions carry. The part codecs write their fields beside the shared ones.
    ///
    /// @param defaultStatePart      the part of a freshly placed state
    /// @param defaultTransitionPart the part of a freshly drawn transition
    public record Kind<S extends FSMState>(
        EnumerableDataBlockManager<S> manager,
        S entry,
        S exit,
        MapCodec<StatePart> statePartCodec,
        MapCodec<TransitionPart> transitionPartCodec,
        Supplier<StatePart> defaultStatePart,
        Supplier<TransitionPart> defaultTransitionPart
    ) {
        public static final Kind<FSMAnimationState> ANIMATION = new Kind<>(
            FSMAnimationState.ENUM_MANAGER, AkytheraAnimationState.ENTRY, AkytheraAnimationState.EXIT,
            AnimationStatePart.MAP_CODEC.xmap(part -> part, part -> (AnimationStatePart)part),
            AnimationTransitionPart.MAP_CODEC.xmap(part -> part, part -> (AnimationTransitionPart)part),
            () -> AnimationStatePart.EMPTY,
            () -> AnimationTransitionPart.DEFAULT
        );

        public static final Kind<MobBehaviorState> BEHAVIOR = new Kind<>(
            MobBehaviorState.ENUM_MANAGER, AkytheraMobBehaviorState.ENTRY, AkytheraMobBehaviorState.EXIT,
            BehaviorStatePart.MAP_CODEC.xmap(part -> part, part -> (BehaviorStatePart)part),
            BehaviorTransitionPart.MAP_CODEC.xmap(part -> part, part -> (BehaviorTransitionPart)part),
            () -> BehaviorStatePart.EMPTY,
            () -> BehaviorTransitionPart.DEFAULT
        );

        /// The holder codec of this kind's signatures.
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Codec<Holder<? extends FSMState>> signatureCodec() {
            return (Codec)DataBlockHolder.holderCodec(manager);
        }
    }

    /// What a state runs, by the machine's [Kind].
    public sealed interface StatePart permits AnimationStatePart, BehaviorStatePart {}

    /// How a transition is taken, by the machine's [Kind].
    public sealed interface TransitionPart permits AnimationTransitionPart, BehaviorTransitionPart {}

    /// The output-pose sub-graph of an animation state, edited in its `{state}` sub-tab.
    public record AnimationStatePart(ScriptGraphData poseGraph) implements StatePart {
        public static final AnimationStatePart EMPTY = new AnimationStatePart(ScriptGraphData.EMPTY);

        public static final MapCodec<AnimationStatePart> MAP_CODEC = ScriptGraphData.CODEC.optionalFieldOf("pose_graph", ScriptGraphData.EMPTY)
            .xmap(AnimationStatePart::new, AnimationStatePart::poseGraph);
    }

    /// The behavior tree a behavior state runs, edited in its `{state}` sub-tab.
    public record BehaviorStatePart(BehaviorTreeData tree) implements StatePart {
        public static final BehaviorStatePart EMPTY = new BehaviorStatePart(BehaviorTreeData.EMPTY);

        public static final MapCodec<BehaviorStatePart> MAP_CODEC = BehaviorTreeData.CODEC.optionalFieldOf("tree", BehaviorTreeData.EMPTY)
            .xmap(BehaviorStatePart::new, BehaviorStatePart::tree);
    }

    /// The cross-fade of an animation transition: `blendTime` and `transferElapsedTime` are the like-named
    /// [AnimationStateTransition] components.
    public record AnimationTransitionPart(float blendTime, boolean transferElapsedTime) implements TransitionPart {
        /// Blend time of a freshly drawn transition, in seconds.
        public static final float DEFAULT_BLEND_TIME = 0.2F;

        /// Elapsed-time transfer of a freshly drawn transition: the entered state starts from zero.
        public static final boolean DEFAULT_TRANSFER_ELAPSED_TIME = false;

        public static final AnimationTransitionPart DEFAULT = new AnimationTransitionPart(DEFAULT_BLEND_TIME, DEFAULT_TRANSFER_ELAPSED_TIME);

        public static final MapCodec<AnimationTransitionPart> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("blend_time", DEFAULT_BLEND_TIME).forGetter(AnimationTransitionPart::blendTime),
                Codec.BOOL.optionalFieldOf("transfer_elapsed_time", DEFAULT_TRANSFER_ELAPSED_TIME).forGetter(AnimationTransitionPart::transferElapsedTime)
            ).apply(instance, AnimationTransitionPart::new)
        );
    }

    /// When a behavior transition is tested: `abortTaskImmediately` and `checkLatency` are the like-named components
    /// of the engine's behavior state machine transition.
    public record BehaviorTransitionPart(boolean abortTaskImmediately, int checkLatency) implements TransitionPart {
        public static final BehaviorTransitionPart DEFAULT = new BehaviorTransitionPart(false, 0);

        public static final MapCodec<BehaviorTransitionPart> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                Codec.BOOL.optionalFieldOf("abort_task_immediately", false).forGetter(BehaviorTransitionPart::abortTaskImmediately),
                Codec.INT.optionalFieldOf("check_latency", 0).forGetter(BehaviorTransitionPart::checkLatency)
            ).apply(instance, BehaviorTransitionPart::new)
        );
    }

    /// One state: its signature (unique within the machine, also the `{state}` sub-tab title), its canvas position,
    /// what it runs, the transitions leaving it, and the names of the groups it belongs to, shared by the states of
    /// this machine only.
    public record StateData(
        Holder<? extends FSMState> state,
        int x,
        int y,
        StatePart part,
        List<TransitionData> transitions,
        List<String> groups
    ) {
        static Codec<StateData> codec(final Kind<?> kind, final Codec<TransitionData> transitionCodec) {
            return RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        kind.signatureCodec().fieldOf("state").forGetter(StateData::state),
                        Codec.INT.fieldOf("x").forGetter(StateData::x),
                        Codec.INT.fieldOf("y").forGetter(StateData::y),
                        kind.statePartCodec().forGetter(StateData::part),
                        transitionCodec.listOf().optionalFieldOf("transitions", List.of()).forGetter(StateData::transitions),
                        Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(StateData::groups)
                    ).apply(instance, StateData::new)
            );
        }
    }

    /// One state group node: an editor-only name, its canvas position and the transitions its members
    /// inherit. The compile copies each of these onto every state naming this group in
    /// [StateData#groups], unless the state has its own transition to the same target.
    public record GroupData(String name, int x, int y, List<TransitionData> transitions) {
        static Codec<GroupData> codec(final Codec<TransitionData> transitionCodec) {
            return RecordCodecBuilder.create(
                instance ->
                    instance.group(
                        Codec.STRING.fieldOf("name").forGetter(GroupData::name),
                        Codec.INT.fieldOf("x").forGetter(GroupData::x),
                        Codec.INT.fieldOf("y").forGetter(GroupData::y),
                        transitionCodec.listOf().optionalFieldOf("transitions", List.of()).forGetter(GroupData::transitions)
                    ).apply(instance, GroupData::new)
            );
        }
    }

    /// One directed transition to the `to` state, its boolean condition sub-graph, the priority it is taken at and
    /// its kind's part; the source is the owner — the [StateData] holding it, the [GroupData] holding it, or Entry
    /// for an [#entryTransitions] entry. Direction matters: `a → b` and `b → a` are distinct, and a given direction
    /// is unique. The target is always a state: a machine occupies one state at a time, so a group can never be a
    /// target.
    public record TransitionData(Holder<? extends FSMState> to, ScriptGraphData conditionGraph, int transitionPriority, TransitionPart part) {
        /// Priority of a freshly drawn transition: every edge ranks equally until one is raised.
        public static final int DEFAULT_TRANSITION_PRIORITY = 0;

        static Codec<TransitionData> codec(final Kind<?> kind) {
            return RecordCodecBuilder.create(
                instance -> instance.group(
                    kind.signatureCodec().fieldOf("to").forGetter(TransitionData::to),
                    ScriptGraphData.CODEC.optionalFieldOf("condition_graph", ScriptGraphData.EMPTY).forGetter(TransitionData::conditionGraph),
                    Codec.INT.optionalFieldOf("transition_priority", DEFAULT_TRANSITION_PRIORITY).forGetter(TransitionData::transitionPriority),
                    kind.transitionPartCodec().forGetter(TransitionData::part)
                ).apply(instance, TransitionData::new)
            );
        }
    }
}
