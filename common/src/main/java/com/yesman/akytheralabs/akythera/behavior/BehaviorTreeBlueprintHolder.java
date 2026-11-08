package com.yesman.akytheralabs.akythera.behavior;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.behavior.MobBehaviorState;
import com.yesman.akythera.api.world.behavior.PerceptionSettings;
import com.yesman.akythera.api.world.behavior.TreeNodeBlueprint;
import com.yesman.akythera.api.world.behavior.statemachine.BehaviorStateMachine;
import com.yesman.akythera.api.world.behavior.statemachine.BehaviorStateMachineData;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.SourceNames;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.ScriptBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Modifiable data holder for a [BehaviorTreeBlueprint]: the blackboard and event graph as one
/// [BlueprintScriptData] laid out by the asset's script with the brain as the graphs' Self, the root tree canvas,
/// the state machines by name, what the brain perceives, and the entity the editor previews the senses on.
///
/// [#makeObject] is the compile step. A machine's state groups are expanded here, the way the animator compile does:
/// each group transition is copied onto every member that has no transition of its own to the same state.
public final class BehaviorTreeBlueprintHolder extends AbstractDataHolder<BehaviorTreeBlueprint>
    implements ScriptBlueprintHolder<BehaviorTreeBlueprint>, DTOProvider
{
    public static final Codec<BehaviorTreeBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script),
            BehaviorTreeData.CODEC.optionalFieldOf("tree", BehaviorTreeData.EMPTY).forGetter(h -> h.tree),
            Codec.unboundedMap(Codec.STRING, StateMachineData.codec(StateMachineData.Kind.BEHAVIOR))
                .optionalFieldOf("state_machines", Map.of())
                .forGetter(h -> h.stateMachines),
            PerceptionHolder.EDITOR_CODEC.optionalFieldOf("perception").forGetter(h -> Optional.of(h.perception)),
            Identifier.CODEC.optionalFieldOf("preview_entity_type").forGetter(ParseUtils.provideOptional(BehaviorTreeBlueprintHolder::getPreviewEntityType))
        ).apply(instance, (script, tree, machines, perception, previewEntityType) -> {
            BehaviorTreeBlueprintHolder holder = new BehaviorTreeBlueprintHolder();

            holder.script = script;
            script.typeProperties(holder.layout());
            holder.tree = tree;
            holder.stateMachines = new LinkedHashMap<>(machines);
            perception.ifPresent(senses -> holder.perception = senses);
            previewEntityType.ifPresent(holder::setPreviewEntityType);

            return holder;
        })
    );

    private BlueprintScriptData script = new BlueprintScriptData();

    private BehaviorTreeData tree = BehaviorTreeData.EMPTY;

    private Map<String, StateMachineData> stateMachines = new LinkedHashMap<>();

    private PerceptionHolder perception = new PerceptionHolder();

    /// The entity type the editor previews the senses on, or null for the default.
    private @Nullable Identifier previewEntityType;

    @Override
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(BehaviorTreeInstance.class));
    }

    @Override
    public Class<?> scriptOwner() {
        return BrainComponent.class;
    }

    @Override
    public BlueprintScriptData getScript() {
        return script;
    }

    public BehaviorTreeData getTree() {
        return tree;
    }

    public void setTree(final BehaviorTreeData tree) {
        this.tree = tree;
    }

    /// The live map, written back by the editor's state machine tabs.
    public Map<String, StateMachineData> getStateMachines() {
        return stateMachines;
    }

    public PerceptionHolder getPerception() {
        return perception;
    }

    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    public void setPreviewEntityType(final @Nullable Identifier previewEntityType) {
        this.previewEntityType = previewEntityType;
    }

    /// The runtime blueprint carries no canvas, so a read keeps only the values: the variables and the properties.
    @Override
    public void readFrom(final BehaviorTreeBlueprint object) {
        this.script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), object.body().properties(),
            object.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
        this.script.typeProperties(layout());
        this.perception.readFrom(object.perception());
    }

    /// @throws IllegalStateException when a tree canvas holds a node the engine can't decode
    @Override
    public BehaviorTreeBlueprint makeObject() {
        BlueprintBody body = script.compile(layout(), BrainComponent.class);
        Map<String, BehaviorStateMachineData> machines = new LinkedHashMap<>();

        stateMachines.forEach((name, machine) -> machines.put(name, compileMachine(name, machine)));

        return new BehaviorTreeBlueprint(body, decodeTree(tree), machines, perception.makeObject());
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), BrainComponent.class, null);
        compilation.unresolved(script.failedNodes());

        Stream.concat(Stream.of(tree), stateMachines.values().stream().flatMap(machine -> machine.states().stream())
                .map(state -> ((StateMachineData.BehaviorStatePart)state.part()).tree()))
            .flatMap(canvas -> canvas.compile().stream())
            .forEach(json -> TreeNodeBlueprint.CODEC.parse(JsonOps.INSTANCE, json).ifError(error -> compilation.fail(Component.literal(error.message()))));

        compilation.build(this::makeObject);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(script.referencedReferences(), stateMachines.values().stream().flatMap(StateMachineData::referencedReferences));
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return script.firstFailure();
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    /// Tokens for `behavior_tree_blueprint_template.ftl`: the constructor arguments of the [BehaviorTreeBlueprint], each
    /// a Java expression. A node is written as the constructor of its blueprint record, and the sight shape through
    /// `components/collider.ftl`.
    @Override
    public Map<String, Object> getDTO() {
        BehaviorTreeBlueprint blueprint = makeObject();
        PerceptionSettings senses = blueprint.perception();
        String sight = Optional.ofNullable(perception.getSight()).map(holder -> {
            Map<String, Object> dto = new HashMap<>(holder.getShape().getDTO());
            dto.put("enabled", true);

            return "Optional.of(new PerceptionSettings.Sight(" + SourceTemplateEngine.render("components/collider.ftl", dto) + ", "
                + senses.sight().orElseThrow().lineOfSight() + "))";
        }).orElse("Optional.empty()");

        return Map.of(
            "body", NodeTreeSourceRenderer.body(blueprint.body(), 4),
            "roots", NodeTreeSourceRenderer.listOf(blueprint.roots().stream().map(root -> treeSource(root, 5)).toList(), 4),
            "state_machines", NodeTreeSourceRenderer.mapOf(blueprint.stateMachines().entrySet().stream()
                .map(machine -> NodeTreeSourceRenderer.entry(machine.getKey(), NodeTreeSourceRenderer.ctor("BehaviorStateMachineData", 6,
                    NodeTreeSourceRenderer.listOf(machine.getValue().entryTransitions().stream()
                        .map(transition -> transitionSource(transition, 8))
                        .toList(), 7),
                    NodeTreeSourceRenderer.listOf(machine.getValue().states().stream()
                        .map(state -> NodeTreeSourceRenderer.ctor("BehaviorStateMachineData.State", 8,
                            ScriptNodeSourceCompiler.dataBlockLookup("MobBehaviorState", state.state()),
                            NodeTreeSourceRenderer.listOf(state.roots().stream().map(root -> treeSource(root, 10)).toList(), 9),
                            NodeTreeSourceRenderer.listOf(state.transitions().stream().map(transition -> transitionSource(transition, 10)).toList(), 9)
                        ))
                        .toList(), 7)
                )))
                .toList(), 4),
            "perception", NodeTreeSourceRenderer.ctor("PerceptionSettings", 4,
                sight,
                senses.hearing().map(hearing -> "Optional.of(new PerceptionSettings.Hearing(" + ScriptNodeSourceCompiler.valueLiteral(hearing.minVolume()) + ", "
                        + ScriptNodeSourceCompiler.valueLiteral(hearing.radius()) + "))")
                    .orElse("Optional.empty()"),
                senses.vibration().map(_ -> "Optional.of(new PerceptionSettings.Vibration())").orElse("Optional.empty()"),
                senses.touch().map(touch -> "Optional.of(new PerceptionSettings.Touch(" + touch.pushCooldown() + "))").orElse("Optional.empty()")
            )
        );
    }

    private static String treeSource(final TreeNodeBlueprint placement, final int indent) {
        return NodeTreeSourceRenderer.ctor("TreeNodeBlueprint", indent,
            constructorSource(placement.node()),
            NodeTreeSourceRenderer.listOf(placement.children().stream().map(child -> treeSource(child, indent + 2)).toList(), indent + 1),
            NodeTreeSourceRenderer.listOf(placement.conditions().stream()
                .map(condition -> "new TreeNodeBlueprint.Condition(" + constructorSource(condition.condition()) + ")")
                .toList(), indent + 1),
            NodeTreeSourceRenderer.listOf(placement.subtasks().stream()
                .map(subtask -> "new TreeNodeBlueprint.Subtask(" + constructorSource(subtask.subtask()) + ", " + subtask.interval() + ")")
                .toList(), indent + 1),
            ScriptNodeSourceCompiler.valueLiteral(placement.abortMode())
        );
    }

    /// `value` as a Java expression: a script type as its lookup, any other record as the call of its canonical
    /// constructor on its components, a list as `List.of`, a registry key as its lookup, and anything else as its
    /// literal.
    private static String constructorSource(final @Nullable Object value) {
        return switch (value) {
            case ScriptType type -> NodeTreeSourceRenderer.scriptType(type);
            case Record record -> "new " + SourceNames.typeName(record.getClass()) + "(" + Arrays.stream(record.getClass().getRecordComponents())
                .map(component -> constructorSource(componentOf(record, component)))
                .collect(Collectors.joining(", ")) + ")";
            case List<?> list -> "List.of(" + list.stream().map(BehaviorTreeBlueprintHolder::constructorSource).collect(Collectors.joining(", ")) + ")";
            case Optional<?> optional -> optional.map(present -> "Optional.of(" + constructorSource(present) + ")").orElse("Optional.empty()");
            case ResourceKey<?> key -> "ResourceKey.create(ResourceKey.createRegistryKey(Identifier.parse("
                + ScriptNodeSourceCompiler.valueLiteral(key.registry().toString()) + ")), Identifier.parse("
                + ScriptNodeSourceCompiler.valueLiteral(key.identifier().toString()) + "))";
            case null, default -> ScriptNodeSourceCompiler.valueLiteral(value);
        };
    }

    private static @Nullable Object componentOf(final Record record, final RecordComponent component) {
        try {
            component.getAccessor().setAccessible(true);

            return component.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unreadable " + record.getClass().getName() + "#" + component.getName(), e);
        }
    }

    private static String transitionSource(final BehaviorStateMachineData.Transition transition, final int indent) {
        return NodeTreeSourceRenderer.ctor("BehaviorStateMachineData.Transition", indent,
            ScriptNodeSourceCompiler.dataBlockLookup("MobBehaviorState", transition.to()),
            NodeTreeSourceRenderer.pure(transition.condition(), indent + 1),
            String.valueOf(transition.abortTaskImmediately()),
            String.valueOf(transition.checkLatency()),
            String.valueOf(transition.transitionPriority()),
            ScriptNodeSourceCompiler.valueLiteral(transition.canvas())
        );
    }

    /// The engine form of the placements ROOT runs on a canvas.
    ///
    /// @throws IllegalStateException when the canvas holds a node the engine can't decode
    private static List<TreeNodeBlueprint> decodeTree(final BehaviorTreeData canvas) {
        return canvas.compile().stream()
            .map(json -> TreeNodeBlueprint.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new))
            .toList();
    }

    private static BehaviorStateMachineData compileMachine(final String name, final StateMachineData machine) {
        Map<String, StateMachineData.GroupData> groups = new LinkedHashMap<>();
        List<BehaviorStateMachineData.State> states = new ArrayList<>();

        machine.groups().forEach(group -> groups.put(group.name(), group));

        for (StateMachineData.StateData state : machine.states()) {
            if (!state.state().isBound()) continue;

            String from = ScriptCanvasKeys.stateName(state.state());
            List<BehaviorStateMachineData.Transition> transitions = new ArrayList<>();
            Set<String> ownTargets = new HashSet<>();

            for (StateMachineData.TransitionData transition : state.transitions()) {
                ownTargets.add(ScriptCanvasKeys.stateName(transition.to()));
                transitions.add(compileTransition(transition, ScriptCanvasKeys.transition(name, from, ScriptCanvasKeys.stateName(transition.to()))));
            }

            for (String groupName : state.groups()) {
                StateMachineData.GroupData group = groups.get(groupName);

                if (group == null) continue;

                for (StateMachineData.TransitionData transition : group.transitions()) {
                    String to = ScriptCanvasKeys.stateName(transition.to());

                    if (to.equals(from) || !ownTargets.add(to)) continue;

                    transitions.add(compileTransition(transition, ScriptCanvasKeys.groupTransition(name, groupName, to)));
                }
            }

            states.add(new BehaviorStateMachineData.State(
                (MobBehaviorState)state.state().value(),
                decodeTree(((StateMachineData.BehaviorStatePart)state.part()).tree()),
                transitions
            ));
        }

        List<BehaviorStateMachineData.Transition> entryTransitions = machine.entryTransitions().stream()
            .map(transition -> compileTransition(transition, ScriptCanvasKeys.transition(name, ScriptCanvasKeys.ENTRY, ScriptCanvasKeys.stateName(transition.to()))))
            .toList();

        return new BehaviorStateMachineData(entryTransitions, states);
    }

    private static BehaviorStateMachineData.Transition compileTransition(final StateMachineData.TransitionData transition, final String canvas) {
        StateMachineData.BehaviorTransitionPart part = (StateMachineData.BehaviorTransitionPart)transition.part();
        PureNode condition = ConditionGraphCompiler.compile(transition.conditionGraph(), BrainComponent.class, BehaviorStateMachine.BRAIN_LOCAL);

        return new BehaviorStateMachineData.Transition(
            (MobBehaviorState)transition.to().value(), condition, part.abortTaskImmediately(), part.checkLatency(),
            transition.transitionPriority(), canvas
        );
    }
}
