package com.yesman.akytheralabs.gui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.behavior.AbortMode;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BehaviorTreeRunner;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TreeNode;
import com.yesman.akythera.api.world.behavior.statemachine.BehaviorStateMachine;
import com.yesman.akythera.api.world.behavior.statemachine.StateMachineTask;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.akythera.behavior.BehaviorNodeCatalog;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeBlueprintHolder;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeData;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.editor.debug.BrainProbe;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.behavior.PerceptionPreviewPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.behavior.BehaviorTreeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeSuggestions;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.TransitionResultNodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// The tabs of a behavior tree asset beside its Event Graph: the root tree, each state machine a State Machine task
/// names, each state's tree and each transition's condition. Details shows the selected node's parameters, a
/// transition's settings, or the asset's senses while nothing is selected on the root tree. The preview of the
/// senses takes the place of the Functions panel.
public final class BehaviorTreeEditorExtension implements ScriptBlueprintEditorScreen.Extension {
    private static final int RESULT_X = 300;
    private static final int RESULT_Y = 120;

    private final ResourceKey<BehaviorTreeBlueprint> resourceKey;

    private final BehaviorTreeBlueprintHolder holder;

    private final Font font = Minecraft.getInstance().font;

    private @Nullable TabPanel tabs;

    private @Nullable ComponentDetailsPanel details;

    private @Nullable BehaviorTreeGraphWidget rootTree;

    private @Nullable PerceptionPreviewPanel preview;

    /// The open tabs by canvas key, with what writes each one back.
    private final Map<String, OpenTab> openTabs = new LinkedHashMap<>();

    public BehaviorTreeEditorExtension(final ResourceKey<BehaviorTreeBlueprint> resourceKey, final BehaviorTreeBlueprintHolder holder) {
        this.resourceKey = resourceKey;
        this.holder = holder;
    }

    /// The root tree canvas, or null before [#install].
    public @Nullable BehaviorTreeGraphWidget getRootTree() {
        return rootTree;
    }

    /// The open state machine canvas of `name`, or null.
    public @Nullable StateMachineGraphWidget getOpenStateMachine(final String name) {
        return openTabs.get(name) instanceof OpenTab tab && tab.content() instanceof StateMachineGraphWidget machine ? machine : null;
    }

    /// The open tree canvas of `state` on the machine `name`, or null.
    public @Nullable BehaviorTreeGraphWidget getOpenStateTree(final String name, final String state) {
        return openTabs.get(ScriptCanvasKeys.statePose(name, state)) instanceof OpenTab tab && tab.content() instanceof BehaviorTreeGraphWidget tree ? tree : null;
    }

    @Override
    public void install(final Screen screen, final TabPanel tabs, final ComponentDetailsPanel details) {
        this.tabs = tabs;
        this.details = details;
        this.rootTree = treeCanvas(holder.getTree());
        this.preview = new PerceptionPreviewPanel(font, holder, () -> details.showComponent(holder.getPerception()));

        rootTree.setOnSelectionChanged(selection -> {
            if (selection == null) {
                details.showComponent(holder.getPerception());
            } else {
                showTreeSelection(selection);
            }
        });

        tabs.addTab(Component.literal("Behavior Tree"), rootTree);
        details.showComponent(holder.getPerception());
    }

    @Override
    public void flush() {
        // Sub-tabs write into their machine first, then each machine writes into the holder
        openTabs.values().stream().filter(tab -> !(tab.content() instanceof StateMachineGraphWidget)).forEach(tab -> tab.flush().run());
        openTabs.values().stream().filter(tab -> tab.content() instanceof StateMachineGraphWidget).forEach(tab -> tab.flush().run());

        if (rootTree != null) holder.setTree(rootTree.exportData());
    }

    @Override
    public AdjustablePanelManager.@Nullable PanelArea sidePanel() {
        return preview;
    }

    @Override
    public void close() {
        if (preview != null) preview.destroy();
    }

    // ***************************************************************
    // Debugging
    // ***************************************************************

    /// Entities whose brain runs this asset on the integrated server.
    @Override
    public Predicate<Entity> debugTargetFilter() {
        return entity -> BrainProbe.treeOf(entity) instanceof BehaviorTreeInstance tree && tree.getBlueprint().is(resourceKey);
    }

    /// Rings the nodes the possessed entity's brain runs, on the root tree, on each open machine's current state and
    /// on that state's open tree.
    @Override
    public void onDebugTick(final EntityPatch<?> entityPatch) {
        BehaviorTreeInstance tree = BrainProbe.treeOf(entityPatch);
        boolean mirrored = tree != null && tree.getBlueprint().is(resourceKey);

        openTabs.values().forEach(tab -> {
            if (tab.content() instanceof BehaviorTreeGraphWidget canvas) canvas.setActiveNodeIds(Set.of());
            if (tab.content() instanceof StateMachineGraphWidget machine) machine.setActiveStateIds(Set.of());
        });

        if (rootTree != null) rootTree.setActiveNodeIds(mirrored ? activeIds(rootTree, tree.getRunner()) : Set.of());
    }

    /// The ids of the nodes of `canvas` whose placement `runner` runs.
    private Set<Integer> activeIds(final BehaviorTreeGraphWidget canvas, final BehaviorTreeRunner runner) {
        BehaviorTreeData data = canvas.exportData();
        List<BehaviorTreeData.NodeData> roots = data.rootNodes();
        Set<Integer> active = new HashSet<>();

        for (int i = 0; i < Math.min(roots.size(), runner.getRoots().size()); i++) mirror(data, roots.get(i), runner.getRoots().get(i), active);

        return active;
    }

    /// Adds to `active` the ids of the canvas nodes from `node` down whose `placement` runs, walking `data` and the
    /// runtime tree side by side in the order [BehaviorTreeData#compile] lays them out. A running State Machine task
    /// rings the current state of its open machine tab and the running nodes of that state's open tree.
    private void mirror(final BehaviorTreeData data, final BehaviorTreeData.NodeData node, final TreeNode placement, final Set<Integer> active) {
        if (placement.isRunning()) active.add(node.id());

        if (placement.isRunning() && placement.getNode() instanceof StateMachineTask task && task.getMachine() != null) {
            BehaviorStateMachine machine = task.getMachine();
            String state = machine.getState().identifier().toString();
            StateMachineGraphWidget canvas = getOpenStateMachine(task.getStateMachine());
            BehaviorTreeGraphWidget stateTree = getOpenStateTree(task.getStateMachine(), state);

            if (canvas != null) canvas.setActiveStateIds(Set.of(state));
            if (stateTree != null) stateTree.setActiveNodeIds(activeIds(stateTree, machine.getCurrentRunner()));
        }

        List<BehaviorTreeData.NodeData> children = node.children().stream().map(data::node).filter(Objects::nonNull).toList();

        for (int i = 0; i < Math.min(children.size(), placement.getChildren().size()); i++) {
            mirror(data, children.get(i), placement.getChildren().get(i), active);
        }
    }

    // ***************************************************************
    // Tabs
    // ***************************************************************

    private BehaviorTreeGraphWidget treeCanvas(final BehaviorTreeData data) {
        BehaviorTreeGraphWidget canvas = new BehaviorTreeGraphWidget(
            font, data,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        canvas.setOnNodeActivated(node -> {
            if (node.json() instanceof JsonObject object && object.has("state_machine")) openStateMachine(object.get("state_machine").getAsString());
        });

        return canvas;
    }

    /// Opens (or re-focuses) the state machine `name`, creating an empty one the first time.
    public StateMachineGraphWidget openStateMachine(final String name) {
        if (getOpenStateMachine(name) instanceof StateMachineGraphWidget open) {
            focus(open);
            return open;
        }

        StateMachineGraphWidget machine = new StateMachineGraphWidget(
            font, StateMachineData.Kind.BEHAVIOR, holder.getStateMachines().getOrDefault(name, StateMachineData.EMPTY),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        machine.setOnStateActivated(state -> openStateTree(name, state.name()));
        machine.setOnTransitionActivated((from, to) -> openCondition(ScriptCanvasKeys.transition(name, from, to),
            (from.isEmpty() ? "Entry" : from) + " to " + to,
            () -> machine.getTransitionCondition(from, to), graph -> machine.setTransitionCondition(from, to, graph)));
        machine.setOnGroupTransitionActivated((group, to) -> openCondition(ScriptCanvasKeys.groupTransition(name, group, to),
            group + " to " + to,
            () -> machine.getGroupTransitionCondition(group, to), graph -> machine.setGroupTransitionCondition(group, to, graph)));
        machine.setOnSelectionChanged(selection -> {
            if (selection instanceof StateMachineGraphWidget.Transition transition) {
                showTransition(machine, transition);
            } else if (details != null) {
                details.showSlots(List.of());
            }
        });

        open(name, Component.literal(name), machine, () -> holder.getStateMachines().put(name, machine.exportData()));

        return machine;
    }

    /// Opens (or re-focuses) the tree of `stateName` on the machine `machineName`, opening the machine first.
    public BehaviorTreeGraphWidget openStateTree(final String machineName, final String stateName) {
        if (getOpenStateTree(machineName, stateName) instanceof BehaviorTreeGraphWidget open) {
            focus(open);
            return open;
        }

        StateMachineGraphWidget machine = openStateMachine(machineName);
        String key = ScriptCanvasKeys.statePose(machineName, stateName);

        BehaviorTreeData data = machine.getStatePart(stateName) instanceof StateMachineData.BehaviorStatePart behavior
            ? behavior.tree()
            : BehaviorTreeData.EMPTY;
        BehaviorTreeGraphWidget tree = treeCanvas(data);

        tree.setOnSelectionChanged(selection -> {
            if (selection != null) showTreeSelection(selection);
        });

        open(key, Component.literal(stateName), tree,
            () -> machine.setStatePart(stateName, new StateMachineData.BehaviorStatePart(tree.exportData())));

        return tree;
    }

    private ScriptNodeGraphWidget openCondition(
        final String key, final String title, final Supplier<ScriptGraphData> read, final Consumer<ScriptGraphData> write
    ) {
        if (openTabs.get(key) instanceof OpenTab tab && tab.content() instanceof ScriptNodeGraphWidget open) {
            focus(open);
            return open;
        }

        ScriptNodeGraphWidget graph = ScriptNodeGraphWidget.create(font, BrainComponent.class);
        ScriptNodeSuggestions suggestions = new ScriptNodeSuggestions(() -> BrainComponent.class, List.of());

        graph.setSuggestionProvider(suggestions::build);
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        graph.setDataFlowOnly(true);

        ScriptGraphData data = read.get();

        if (data.isEmpty()) {
            graph.addNode(new TransitionResultNodeWidget(
                font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_TRANSITION_RESULT), RESULT_X, RESULT_Y));
        } else {
            graph.importGraph(data, (_, _, _) -> null);
        }

        open(key, Component.literal(title), graph, () -> write.accept(graph.exportGraph()));

        return graph;
    }

    /// Every transition condition of every state machine, whether its tab is open or not.
    @Override
    public List<GraphTabScreen.GraphTab> graphTabs() {
        flush();

        List<GraphTabScreen.GraphTab> graphTabs = new ArrayList<>();

        holder.getStateMachines().forEach((name, machine) -> {
            for (StateMachineData.TransitionData transition : machine.entryTransitions()) {
                String to = ScriptCanvasKeys.stateName(transition.to());

                graphTabs.add(new GraphTabScreen.GraphTab(name + ": Entry to " + to, () -> openTransition(name, "", to)));
            }

            for (StateMachineData.StateData state : machine.states()) {
                String from = ScriptCanvasKeys.stateName(state.state());

                for (StateMachineData.TransitionData transition : state.transitions()) {
                    String to = ScriptCanvasKeys.stateName(transition.to());

                    graphTabs.add(new GraphTabScreen.GraphTab(name + ": " + from + " to " + to, () -> openTransition(name, from, to)));
                }
            }

            for (StateMachineData.GroupData group : machine.groups()) {
                for (StateMachineData.TransitionData transition : group.transitions()) {
                    String to = ScriptCanvasKeys.stateName(transition.to());

                    graphTabs.add(new GraphTabScreen.GraphTab(name + ": " + group.name() + " to " + to, () -> {
                        StateMachineGraphWidget machineCanvas = openStateMachine(name);

                        return openCondition(ScriptCanvasKeys.groupTransition(name, group.name(), to), group.name() + " to " + to,
                            () -> machineCanvas.getGroupTransitionCondition(group.name(), to),
                            graph -> machineCanvas.setGroupTransitionCondition(group.name(), to, graph));
                    }));
                }
            }
        });

        return graphTabs;
    }

    /// Opens the condition of the transition `from` to `to` of the machine `name`. Empty `from` is Entry.
    private ScriptNodeGraphWidget openTransition(final String name, final String from, final String to) {
        StateMachineGraphWidget machine = openStateMachine(name);

        return openCondition(ScriptCanvasKeys.transition(name, from, to), (from.isEmpty() ? "Entry" : from) + " to " + to,
            () -> machine.getTransitionCondition(from, to), graph -> machine.setTransitionCondition(from, to, graph));
    }

    private void open(final String key, final Component title, final AbstractWidget content, final Runnable flush) {
        if (tabs == null) return;

        openTabs.put(key, new OpenTab(content, flush));
        tabs.addCloseableTab(title, content, () -> {
            flush.run();
            openTabs.remove(key);
        });
        focus(content);
    }

    private void focus(final AbstractWidget content) {
        if (tabs != null) tabs.setActiveIndex(tabs.indexOfContent(content));
    }

    // ***************************************************************
    // Details
    // ***************************************************************

    private void showTreeSelection(final BehaviorTreeGraphWidget.Selection selection) {
        if (details == null) return;

        List<EditorWidgetFactory.ValueSlot> slots = new ArrayList<>();

        switch (selection) {
            case BehaviorTreeGraphWidget.Selection.Box box -> {
                addParameterSlot(slots, box, box.node()::json, box.node()::setJson);
                slots.add(new EditorWidgetFactory.PropertySlot("Abort Mode", new ValueShape.Leaf(AbortMode.class, null), box,
                    box.node()::abortMode, value -> box.node().setAbortMode((AbortMode)value)));
            }
            case BehaviorTreeGraphWidget.Selection.Boxes _ -> {}
            case BehaviorTreeGraphWidget.Selection.Strip strip -> {
                BehaviorTreeGraphWidget.Attachment attachment = strip.attachment();

                addParameterSlot(slots, strip, attachment::json, attachment::setJson);

                if (!strip.condition()) {
                    slots.add(new EditorWidgetFactory.PropertySlot("Interval", new ValueShape.Leaf(Integer.class, null), strip,
                        attachment::interval, value -> attachment.setInterval(Math.max(1, (Integer)value))));
                }
            }
        }

        details.showSlots(slots);
    }

    /// One record row of the parameters of the node `json` describes, written back as the JSON of its node type.
    /// Nothing for JSON that does not decode, or a blueprint with no parameters.
    private static void addParameterSlot(
        final List<EditorWidgetFactory.ValueSlot> slots, final Object owner, final Supplier<JsonElement> json, final Consumer<JsonElement> write
    ) {
        BehaviorNodeBlueprint<?> blueprint = BehaviorNodeCatalog.blueprintOf(json.get());

        if (blueprint == null || !(ValueShape.of(blueprint.getClass()) instanceof ValueShape.RecordShape record) || record.shapes().isEmpty()) return;

        ValueShape.RecordShape shape = new ValueShape.RecordShape(record.type(), record.shapes(), BehaviorNodeBlueprint.CODEC);

        slots.add(new EditorWidgetFactory.PropertySlot("Parameters", shape, owner,
            () -> shape.fromJson(json.get()),
            value -> {
                try {
                    write.accept(shape.toJson(value));
                } catch (RuntimeException e) {
                    AkytheraLabs.LOGGER.warn("The {} parameters refuse the edit", record.type().getSimpleName(), e);
                }
            }));
    }

    private void showTransition(final StateMachineGraphWidget machine, final StateMachineGraphWidget.Transition transition) {
        if (details == null) return;

        Supplier<StateMachineData.BehaviorTransitionPart> part = () -> machine.getTransitionPart(transition) instanceof StateMachineData.BehaviorTransitionPart behavior
            ? behavior
            : StateMachineData.BehaviorTransitionPart.DEFAULT;

        details.showSlots(List.of(
            new EditorWidgetFactory.PropertySlot("Abort Task Immediately", new ValueShape.Leaf(Boolean.class, null), transition,
                () -> part.get().abortTaskImmediately(),
                value -> machine.setTransitionPart(transition, new StateMachineData.BehaviorTransitionPart((Boolean)value, part.get().checkLatency()))),
            new EditorWidgetFactory.PropertySlot("Check Latency", new ValueShape.Leaf(Integer.class, null), transition,
                () -> part.get().checkLatency(),
                value -> machine.setTransitionPart(transition, new StateMachineData.BehaviorTransitionPart(part.get().abortTaskImmediately(), Math.max(0, (Integer)value)))),
            new EditorWidgetFactory.PropertySlot("Transition Priority", new ValueShape.Leaf(Integer.class, null), transition,
                () -> machine.getTransitionPriority(transition),
                value -> machine.setTransitionPriority(transition, (Integer)value))
        ));
    }

    /// An open tab and what writes it back.
    private record OpenTab(AbstractWidget content, Runnable flush) {}
}
