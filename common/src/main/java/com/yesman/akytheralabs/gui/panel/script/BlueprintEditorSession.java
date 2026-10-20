package com.yesman.akytheralabs.gui.panel.script;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptFailure;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintMembers;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.VariableDropModal;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlueprintFieldGetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlueprintFieldSetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.CompileFailure;
import com.yesman.akytheralabs.gui.widget.script.FunctionCallNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.MethodEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.NodeSuggestContext;
import com.yesman.akytheralabs.gui.widget.script.NodeWidgetFactory;
import com.yesman.akytheralabs.gui.widget.script.ParentCallNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ReturnNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.node.FunctionCallNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.MethodEntryNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ParentCallNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ReturnNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.VariableGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.VariableSetterNodeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// The blueprint half of an editor screen: the Event Graph holding one entry node per event of the parent's
/// [BlueprintLayout], a closeable canvas tab per overridden or declared function, the [FunctionPanel], and
/// the Details rows of the parent's properties.
///
/// It edits copies of its [BlueprintScriptData] and writes them back on [#flush]. The property rows are the
/// exception and write straight into the data.
public final class BlueprintEditorSession {
    /// Seed layout of the Event Graph's entry nodes, stacked top to bottom in layout order.
    private static final int ENTRY_X = 60;
    private static final int ENTRY_Y = 60;
    private static final int ENTRY_GAP = 130;

    /// Graph-space distance between the seeded nodes of a new function canvas.
    private static final int NODE_GAP = 240;

    private static final String EVENTS_CATEGORY = "Events";
    private static final String FUNCTIONS_CATEGORY = "Functions";
    private static final String FIELDS_CATEGORY = "Blueprint Fields";

    /// What a canvas rebuilds its rows with: no row of a blueprint canvas asks the host for an entry.
    private static final ScriptNodeGraphWidget.EntryNodeFactory NO_ENTRY = (_, _, _) -> null;

    /// The key a Compile marks the Event Graph's nodes under. A function canvas is keyed [#functionKey].
    public static final String EVENT_GRAPH_KEY = "event";

    private final Font font;
    private final BlueprintLayout layout;
    private final Class<?> ownerType;
    private final BlueprintScriptData data;
    private final TabPanel tabs;
    private final List<VariableDraft> variableDrafts;
    private final BiFunction<ScriptNodeGraphWidget, NodeSuggestContext, List<NodeSuggestModal.Suggestion<NodeWidgetFactory>>> palette;
    private final Consumer<List<AbstractNodeWidget>> selectionHandler;
    private final ScriptFunction.Usage[] usages;
    private final BlueprintMembers members;
    private final ScriptNodeGraphWidget eventGraph;
    private final FunctionPanel functionPanel;

    /// The canvas of each overridden function, by method name. Stale for a function whose tab is open.
    private final Map<String, ScriptGraphData> overrides = new LinkedHashMap<>();

    private final List<UserFunctionDraft> functions = new ArrayList<>();

    /// The canvas of each declared function. Stale for a function whose tab is open.
    private final Map<UserFunctionDraft, ScriptGraphData> functionGraphs = new HashMap<>();

    /// The open function tabs, by function name.
    private final Map<String, ScriptNodeGraphWidget> openCanvases = new LinkedHashMap<>();

    /// The open tabs of canvases kept outside the data, by the key each was opened under.
    private final Map<Object, ExternalTab> externalTabs = new LinkedHashMap<>();

    /// The canvases kept outside the data, which every rewrite of the closed canvases reaches too. Null when the
    /// host keeps none.
    @Nullable
    private ExternalCanvases externalCanvases;

    /// The value each property row edits, by property key, decoded from the data on first show. A null
    /// value is an unset leaf.
    private final Map<String, @Nullable Object> propertyValues = new HashMap<>();

    /// The keys of properties whose edited value does not encode yet. The data keeps the last value that did.
    private final Set<String> incompleteProperties = new HashSet<>();

    /// The function the Functions panel has selected, or null.
    private @Nullable String selected;

    /// Told the declared function a Functions panel pick selects, or null when the pick is an override.
    private Consumer<@Nullable UserFunctionDraft> functionSelectionHandler = _ -> {};

    /// The declared functions' signatures the canvases were last built against.
    private List<BlueprintSignature> builtSignatures;

    /// @param palette          the host's node palette, which this session extends with the blueprint's
    ///                         calls, returns and fields
    /// @param selectionHandler told the nodes a canvas selects, as [ScriptNodeGraphWidget#setSelectionHandler]
    /// @param usages           the [ScriptFunction.Usage]s every canvas offers
    public BlueprintEditorSession(
        final Font font,
        final Screen screen,
        final BlueprintLayout layout,
        final Class<?> ownerType,
        final BlueprintScriptData data,
        final TabPanel tabs,
        final List<VariableDraft> variableDrafts,
        final BiFunction<ScriptNodeGraphWidget, NodeSuggestContext, List<NodeSuggestModal.Suggestion<NodeWidgetFactory>>> palette,
        final Consumer<List<AbstractNodeWidget>> selectionHandler,
        final ScriptFunction.Usage... usages
    ) {
        this.font = font;
        this.layout = layout;
        this.ownerType = ownerType;
        this.data = data;
        this.tabs = tabs;
        this.variableDrafts = variableDrafts;
        this.palette = palette;
        this.selectionHandler = selectionHandler;
        this.usages = usages;

        overrides.putAll(data.getOverrides());

        for (BlueprintScriptData.UserFunction function : data.getFunctions()) {
            UserFunctionDraft draft = UserFunctionDraft.from(function, this::renameFunction);

            functions.add(draft);
            functionGraphs.put(draft, function.graph());
        }

        members = BlueprintMembers.of(layout, name -> {
            UserFunctionDraft draft = draftOf(name);

            return draft == null ? null : draft.signature();
        });

        eventGraph = newCanvas(null, members);
        eventGraph.importGraph(data.getEventGraph(), NO_ENTRY);

        // A new Event Graph starts with every event's entry. Past that, an entry the user deleted stays
        // deleted, and the node suggestions place it again.
        if (data.getEventGraph().isEmpty()) {
            int y = ENTRY_Y;

            for (BlueprintLayout.Signature event : layout.events()) {
                eventGraph.addNode(new MethodEntryNodeWidget(font, event.name(), members.method(event.name()), ENTRY_X, y));
                y += ENTRY_GAP;
            }
        }

        functionPanel = new FunctionPanel(font, screen, this);
        builtSignatures = functions.stream().map(UserFunctionDraft::signature).toList();
    }

    public ScriptNodeGraphWidget eventGraph() {
        return eventGraph;
    }

    public FunctionPanel functionPanel() {
        return functionPanel;
    }

    public BlueprintLayout layout() {
        return layout;
    }

    /// The Event Graph, every open function canvas and every open canvas kept outside the data.
    public List<ScriptNodeGraphWidget> canvases() {
        return Stream.of(Stream.of(eventGraph), openCanvases.values().stream(), externalTabs.values().stream().map(ExternalTab::canvas))
            .flatMap(canvases -> canvases)
            .toList();
    }

    /// Sets [#externalCanvases].
    public void setExternalCanvases(final @Nullable ExternalCanvases externalCanvases) {
        this.externalCanvases = externalCanvases;
    }

    /// Opens, or brings up, a canvas kept outside the data under `key`: it implements `signature` among the
    /// blueprint's members, starts from `graph`, and hands its graph to `store` once it opens, when its tab closes
    /// and on [#flush]. An empty `graph` starts with the entry of `signature` wired into a Return.
    public void openExternalCanvas(
        final Object key, final Component title, final BlueprintSignature signature, final ScriptGraphData graph, final Consumer<ScriptGraphData> store
    ) {
        ExternalTab open = externalTabs.get(key);

        if (open != null) {
            tabs.setActiveIndex(tabs.indexOfContent(open.canvas()));
            return;
        }

        ScriptNodeGraphWidget canvas = newCanvas(signature.name(), members.with(Map.of(signature.name(), signature)));

        canvas.importGraph(graph.isEmpty() ? new ScriptGraphData(
            List.of(
                new GraphNode(0, ENTRY_X, ENTRY_Y, new MethodEntryNodeData(signature.name())),
                new GraphNode(1, ENTRY_X + NODE_GAP, ENTRY_Y, new ReturnNodeData())
            ),
            List.of(new ScriptGraphData.WireData(0, 0, 1, 0))
        ) : graph, NO_ENTRY);

        externalTabs.put(key, new ExternalTab(canvas, title, store));
        store.accept(canvas.exportGraph());

        tabs.addCloseableTab(title, canvas, () -> {
            store.accept(canvas.exportGraph());
            externalTabs.remove(key);
        });
        tabs.setActiveIndex(tabs.indexOfContent(canvas));
    }

    /// The open canvas kept outside the data under `key`, or null when its tab is closed.
    public @Nullable ScriptNodeGraphWidget externalCanvas(final Object key) {
        ExternalTab open = externalTabs.get(key);

        return open == null ? null : open.canvas();
    }

    /// Re-titles the open tab of every canvas kept outside the data by what `titleOf` answers for its key, and
    /// closes, storing its graph first, every one it answers null for.
    public void refreshExternalCanvases(final Function<Object, @Nullable Component> titleOf) {
        for (Map.Entry<Object, ExternalTab> open : List.copyOf(externalTabs.entrySet())) {
            Component title = titleOf.apply(open.getKey());

            if (title == null) {
                tabs.removeTab(tabs.indexOfContent(open.getValue().canvas()));
            } else if (!title.equals(open.getValue().title())) {
                tabs.setTitle(open.getValue().canvas(), title);
                externalTabs.put(open.getKey(), new ExternalTab(open.getValue().canvas(), title, open.getValue().store()));
            }
        }
    }

    public void setFunctionSelectionHandler(final Consumer<@Nullable UserFunctionDraft> functionSelectionHandler) {
        this.functionSelectionHandler = functionSelectionHandler;
    }

    // ***************************************************************
    // Functions
    // ***************************************************************

    /// The overridden functions in layout order, then the declared ones.
    public List<String> functionNames() {
        List<String> names = new ArrayList<>();

        for (BlueprintLayout.Signature function : layout.functions()) {
            if (overrides.containsKey(function.name())) names.add(function.name());
        }

        functions.forEach(function -> names.add(function.getName()));

        return names;
    }

    /// The parent's functions not overridden yet.
    public List<String> overridableFunctions() {
        return layout.functions().stream()
            .map(BlueprintLayout.Signature::name)
            .filter(name -> !overrides.containsKey(name))
            .toList();
    }

    public boolean isOverride(final String name) {
        return overrides.containsKey(name);
    }

    public @Nullable String selectedFunction() {
        return selected;
    }

    /// Selects the function `name` and brings its canvas up, seeding a canvas that holds nothing yet: an
    /// override with its entry, a Parent call and a Return that answers what the parent answered, a declared
    /// function with its entry and a Return.
    public void openFunction(final String name) {
        selected = name;

        ScriptNodeGraphWidget open = openCanvases.get(name);

        // The tab comes up first, so what the tab change shows in Details gives way to the function
        if (open != null) {
            tabs.setActiveIndex(tabs.indexOfContent(open));
            functionSelectionHandler.accept(draftOf(name));
            return;
        }

        UserFunctionDraft draft = draftOf(name);
        ScriptGraphData saved = draft == null ? overrides.get(name) : functionGraphs.get(draft);
        BlueprintSignature signature = members.method(name);

        if ((saved == null || saved.isEmpty()) && signature != null) {
            List<GraphNode> nodes = new ArrayList<>();
            List<ScriptGraphData.WireData> wires = new ArrayList<>();
            boolean override = signature.kind() == BlueprintSignature.Kind.OVERRIDE;

            nodes.add(new GraphNode(0, ENTRY_X, ENTRY_Y, new MethodEntryNodeData(name)));

            if (override) {
                nodes.add(new GraphNode(1, ENTRY_X + NODE_GAP, ENTRY_Y, new ParentCallNodeData()));
                wires.add(new ScriptGraphData.WireData(0, 0, 1, 0));
            }

            int returnId = nodes.size();

            nodes.add(new GraphNode(returnId, ENTRY_X + NODE_GAP * nodes.size(), ENTRY_Y, new ReturnNodeData()));
            wires.add(new ScriptGraphData.WireData(returnId - 1, 0, returnId, 0));

            if (override && signature.returnType() != null) {
                wires.add(new ScriptGraphData.WireData(1, 1, returnId, 1));
            }

            saved = new ScriptGraphData(nodes, wires);
        }

        ScriptNodeGraphWidget canvas = newCanvas(name, members);

        canvas.importGraph(saved == null ? ScriptGraphData.EMPTY : saved, NO_ENTRY);
        openCanvases.put(name, canvas);

        tabs.addCloseableTab(Component.literal(name), canvas, () -> {
            String current = Objects.requireNonNull(canvas.enclosingFunction());

            store(current, canvas.exportGraph());
            openCanvases.remove(current);
        });
        tabs.setActiveIndex(tabs.indexOfContent(canvas));
        functionSelectionHandler.accept(draftOf(name));
    }

    /// Declares a function with a free `newFunction` name, and opens it.
    public void addFunction() {
        String name = "newFunction";

        for (int suffix = 1; members.method(name) != null || layout.field(name) != null; suffix++) {
            name = "newFunction" + suffix;
        }

        UserFunctionDraft draft = new UserFunctionDraft(name, List.of(), null, this::renameFunction);

        functions.add(draft);
        functionGraphs.put(draft, ScriptGraphData.EMPTY);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                closeTab(draft.getName());
                functions.remove(draft);
                functionPanel.rebuildList();
            }

            @Override
            public void redo() {
                if (!functions.contains(draft)) functions.add(draft);
                functionPanel.rebuildList();
            }
        });

        functionPanel.rebuildList();
        openFunction(name);
    }

    /// Overrides the parent's function `name`, and opens it.
    public void overrideFunction(final String name) {
        overrides.put(name, ScriptGraphData.EMPTY);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                closeTab(name);
                overrides.remove(name);
                functionPanel.rebuildList();
            }

            @Override
            public void redo() {
                overrides.putIfAbsent(name, ScriptGraphData.EMPTY);
                functionPanel.rebuildList();
            }
        });

        functionPanel.rebuildList();
        openFunction(name);
    }

    /// Drops the override or the declared function `name`, closing its tab. Calls to it stay on the
    /// canvases, flagged.
    public void removeFunction(final String name) {
        closeTab(name);

        UserFunctionDraft draft = draftOf(name);
        int index = functions.indexOf(draft);
        ScriptGraphData overridden = overrides.remove(name);
        ScriptGraphData graph = draft == null ? null : functionGraphs.get(draft);

        if (draft != null) functions.remove(draft);

        if (name.equals(selected)) {
            selected = null;
            functionSelectionHandler.accept(null);
        }

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                if (overridden != null) overrides.put(name, overridden);

                if (draft != null) {
                    functions.add(Math.min(index, functions.size()), draft);
                    functionGraphs.put(draft, Objects.requireNonNull(graph));
                }

                functionPanel.rebuildList();
            }

            @Override
            public void redo() {
                closeTab(name);
                overrides.remove(name);
                functions.remove(draft);
                functionPanel.rebuildList();
            }
        });

        functionPanel.rebuildList();
    }

    /// Rebuilds every canvas when a Details edit changed a declared function's signature.
    public void onSignaturesEdited() {
        List<BlueprintSignature> signatures = functions.stream().map(UserFunctionDraft::signature).toList();

        if (signatures.equals(builtSignatures)) return;

        builtSignatures = signatures;

        for (ScriptNodeGraphWidget canvas : canvases()) {
            canvas.importGraph(canvas.exportGraph(), NO_ENTRY);
        }

        functionPanel.rebuildList();
    }

    /// Follows a declared function from `from` to `to`: its tab, its entry and every call of it.
    private void renameFunction(final String from, final String to) {
        ScriptNodeGraphWidget open = openCanvases.remove(from);

        if (open != null) {
            openCanvases.put(to, open);
            tabs.setTitle(open, Component.literal(to));
        }

        UnaryOperator<NodeData> renamed = node -> switch (node) {
            case FunctionCallNodeData call when call.function().equals(from) -> new FunctionCallNodeData(to);
            case MethodEntryNodeData entry when entry.method().equals(from) -> new MethodEntryNodeData(to);
            default -> node;
        };

        mapClosedCanvases(graph -> graph.mapNodes(renamed));

        for (ScriptNodeGraphWidget canvas : canvases()) {
            if (from.equals(canvas.enclosingFunction())) canvas.setBlueprintMembers(members, to);

            canvas.importGraph(canvas.exportGraph().mapNodes(renamed), NO_ENTRY);
        }

        if (from.equals(selected)) selected = to;

        builtSignatures = functions.stream().map(UserFunctionDraft::signature).toList();
        functionPanel.rebuildList();
    }

    private @Nullable UserFunctionDraft draftOf(final String name) {
        for (UserFunctionDraft function : functions) {
            if (function.getName().equals(name)) return function;
        }

        return null;
    }

    /// Keeps `graph` as the canvas of the function `name`.
    private void store(final String name, final ScriptGraphData graph) {
        UserFunctionDraft draft = draftOf(name);

        if (draft != null) {
            functionGraphs.put(draft, graph);
        } else if (overrides.containsKey(name)) {
            overrides.put(name, graph);
        }
    }

    private void closeTab(final String name) {
        ScriptNodeGraphWidget open = openCanvases.get(name);

        if (open != null) tabs.removeTab(tabs.indexOfContent(open));
    }

    /// A canvas implementing `function`, or the Event Graph for null, knowing `canvasMembers`.
    private ScriptNodeGraphWidget newCanvas(final @Nullable String function, final BlueprintMembers canvasMembers) {
        ScriptNodeGraphWidget canvas = ScriptNodeGraphWidget.create(font, ownerType);

        // The host palette, then the entries of the events, the calls of the declared functions, the Return
        // and Parent nodes of the canvas' own function, and the parent's fields
        canvas.setSuggestionProvider((graph, context) -> {
            List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = palette.apply(graph, context);
            boolean statement = context.flow() && !context.dataFlowOnly() && context.providedType() == null;

            // Only a free-standing spawn on the Event Graph places an entry, and an event placed already is
            // listed but cannot be picked again.
            if (statement && context.pureFunctions() && graph.enclosingFunction() == null) {
                Set<String> placed = graph.methodEntryNodes().stream()
                    .map(MethodEntryNodeWidget::method)
                    .collect(Collectors.toSet());

                for (BlueprintLayout.Signature event : layout.events()) {
                    String name = event.name();
                    BlueprintSignature signature = members.method(name);

                    suggestions.add(new NodeSuggestModal.Suggestion<>(
                        signature == null ? Component.literal(name) : signature.title(),
                        (nodeFont, x, y) -> new MethodEntryNodeWidget(nodeFont, name, signature, x, y),
                        EVENTS_CATEGORY,
                        !placed.contains(name)));
                }
            }

            if (statement) {
                for (UserFunctionDraft declared : functions) {
                    String name = declared.getName();

                    suggestions.add(new NodeSuggestModal.Suggestion<>(
                        Component.literal(ParseUtils.camelToSpacedPascal(name)),
                        (nodeFont, x, y) -> new FunctionCallNodeWidget(nodeFont, name, members.method(name), x, y),
                        FUNCTIONS_CATEGORY));
                }

                BlueprintSignature enclosing = graph.enclosingFunction() == null ? null : canvasMembers.method(graph.enclosingFunction());

                if (enclosing != null) {
                    // A canvas holds at most one Return, which every ending flow wires into.
                    if (!graph.hasNode(ReturnNodeWidget.class)) {
                        suggestions.add(new NodeSuggestModal.Suggestion<>(
                            Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_RETURN),
                            (nodeFont, x, y) -> new ReturnNodeWidget(nodeFont, enclosing, x, y),
                            FUNCTIONS_CATEGORY));
                    }

                    if (enclosing.kind() == BlueprintSignature.Kind.OVERRIDE) {
                        suggestions.add(new NodeSuggestModal.Suggestion<>(
                            Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_PARENT_CALL, enclosing.title()),
                            (nodeFont, x, y) -> new ParentCallNodeWidget(nodeFont, enclosing, x, y),
                            FUNCTIONS_CATEGORY));
                    }
                }

                for (BlueprintLayout.Variable field : layout.fields()) {
                    suggestions.add(new NodeSuggestModal.Suggestion<>(
                        Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_SET_FIELD, field.name()),
                        (nodeFont, x, y) -> new BlueprintFieldSetterNodeWidget(nodeFont, field.name(), field.field().getType(), x, y),
                        FIELDS_CATEGORY));
                }
            }

            if (context.valueProviders() || statement && context.pureFunctions()) {
                for (BlueprintLayout.Variable field : layout.fields()) {
                    if (!AbstractNodeWidget.valueAssignable(field.field().getType(), context.expectedType())) continue;

                    suggestions.add(new NodeSuggestModal.Suggestion<>(
                        Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_GET_FIELD, field.name()),
                        (nodeFont, x, y) -> new BlueprintFieldGetterNodeWidget(nodeFont, field.name(), field.field().getType(), x, y),
                        FIELDS_CATEGORY));
                }
            }

            return suggestions;
        });

        canvas.setUsage(usages);
        canvas.setBlueprintMembers(canvasMembers, function);
        canvas.setSelectionHandler(selectionHandler);

        return canvas;
    }

    // ***************************************************************
    // Fields and variables
    // ***************************************************************

    /// A [BlueprintLayout.Variable] released over the active canvas opens a [VariableDropModal] at the
    /// cursor, whose rows spawn its Get or Set node there.
    public boolean dropField(final BlueprintLayout.Variable field, final double screenX, final double screenY) {
        if (!(tabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        Class<?> type = field.field().getType();

        VariableDropModal modal = new VariableDropModal(
            font, field.name(),
            Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_GET_FIELD, field.name()),
            Component.translatable(LangKeys.GUI_BLUEPRINT_NODE_SET_FIELD, field.name()),
            () -> graph.spawnNodeAt(
                (nodeFont, x, y) -> new BlueprintFieldGetterNodeWidget(nodeFont, field.name(), type, x, y), screenX, screenY),
            () -> graph.spawnNodeAt(
                (nodeFont, x, y) -> new BlueprintFieldSetterNodeWidget(nodeFont, field.name(), type, x, y), screenX, screenY));

        ModalManager.getInstance().openModalWidget(modal, (int) screenX, (int) screenY);

        return true;
    }

    /// Follows a renamed member variable on every canvas, open or closed.
    public void renameVariable(final String from, final String to) {
        canvases().forEach(canvas -> canvas.renameVariableNodes(from, to));

        mapClosedCanvases(graph -> graph.mapNodes(node -> switch (node) {
            case VariableGetterNodeData getter when getter.variable().equals(from) ->
                new VariableGetterNodeData(to, getter.variableType());
            case VariableSetterNodeData setter when setter.variable().equals(from) ->
                new VariableSetterNodeData(to, setter.variableType());
            default -> node;
        }));
    }

    /// Re-types a member variable's nodes on every canvas, open or closed: an open one as
    /// [ScriptNodeGraphWidget#retypeVariableNodes] does, a closed one in its stored data. Answers the edit that
    /// reverses them all, or null when no node reads it. A closed canvas keeps its wires until its tab opens.
    public @Nullable WorkHistory retypeVariable(final String name, final ScriptType type) {
        List<WorkHistory> edits = canvases().stream()
            .map(canvas -> canvas.retypeVariableNodes(name, type))
            .filter(Objects::nonNull)
            .toList();

        Map<String, ScriptGraphData> overridesBefore = new HashMap<>(overrides);
        Map<UserFunctionDraft, ScriptGraphData> functionsBefore = new HashMap<>(functionGraphs);

        overrides.replaceAll((_, graph) -> retype(graph, name, type));
        functionGraphs.replaceAll((_, graph) -> retype(graph, name, type));

        Map<String, ScriptGraphData> overridesAfter = new HashMap<>(overrides);
        Map<UserFunctionDraft, ScriptGraphData> functionsAfter = new HashMap<>(functionGraphs);
        WorkHistory external = mapExternalCanvases(graph -> graph.mapNodes(node -> switch (node) {
            case VariableGetterNodeData getter when getter.variable().equals(name) ->
                new VariableGetterNodeData(name, Optional.of(type));
            case VariableSetterNodeData setter when setter.variable().equals(name) ->
                new VariableSetterNodeData(name, Optional.of(type));
            default -> node;
        }));

        if (edits.isEmpty() && external == null && overridesAfter.equals(overridesBefore) && functionsAfter.equals(functionsBefore)) return null;

        return new WorkHistory() {
            @Override
            public void undo() {
                edits.reversed().forEach(WorkHistory::undo);
                restoreClosedCanvases(overrides, overridesAfter, overridesBefore);
                restoreClosedCanvases(functionGraphs, functionsAfter, functionsBefore);

                if (external != null) external.undo();
            }

            @Override
            public void redo() {
                edits.forEach(WorkHistory::redo);
                restoreClosedCanvases(overrides, overridesBefore, overridesAfter);
                restoreClosedCanvases(functionGraphs, functionsBefore, functionsAfter);

                if (external != null) external.redo();
            }
        };
    }

    /// Puts `to`'s canvas back for every closed canvas of `canvases` still holding `from`'s. A tab opened in the
    /// meantime left `canvases` and is not touched.
    private static <K> void restoreClosedCanvases(
        final Map<K, ScriptGraphData> canvases, final Map<K, ScriptGraphData> from, final Map<K, ScriptGraphData> to
    ) {
        to.forEach((key, graph) -> {
            if (canvases.get(key) == from.get(key)) canvases.put(key, graph);
        });
    }

    /// `graph` with the nodes of the member variable `name` typed `type`.
    private static ScriptGraphData retype(final ScriptGraphData graph, final String name, final ScriptType type) {
        return graph.mapNodes(node -> switch (node) {
            case VariableGetterNodeData getter when getter.variable().equals(name) ->
                new VariableGetterNodeData(name, Optional.of(type));
            case VariableSetterNodeData setter when setter.variable().equals(name) ->
                new VariableSetterNodeData(name, Optional.of(type));
            default -> node;
        });
    }

    /// Replaces the canvas of every function whose tab is closed, and every canvas kept outside the data, by what
    /// `mapper` makes of it.
    public void mapClosedCanvases(final UnaryOperator<ScriptGraphData> mapper) {
        overrides.replaceAll((_, graph) -> mapper.apply(graph));
        functionGraphs.replaceAll((_, graph) -> mapper.apply(graph));
        mapExternalCanvases(mapper);
    }

    /// [ExternalCanvases#map] on [#externalCanvases], or null when there are none.
    private @Nullable WorkHistory mapExternalCanvases(final UnaryOperator<ScriptGraphData> mapper) {
        return externalCanvases == null ? null : externalCanvases.map(mapper);
    }

    // ***************************************************************
    // Properties
    // ***************************************************************

    /// One Details slot per property of the parent that can hold a value, laid out by its type's
    /// [ValueShape] and written into the data in its codec's JSON form.
    ///
    /// Writing the shape's unset value removes the property, which leaves the parent's own. A value that
    /// does not encode yet, such as a list element with nothing picked, keeps the last value that did in the
    /// data and fails [#validate]. A stored value that does not decode shows as its JSON, read-only.
    public List<EditorWidgetFactory.ValueSlot> propertySlots() {
        List<EditorWidgetFactory.ValueSlot> slots = new ArrayList<>();
        Map<String, JsonElement> values = data.getProperties();

        for (BlueprintLayout.Property property : layout.properties()) {
            ValueShape shape = ValueShape.of(property.type());
            String key = property.key();
            String label = ParseUtils.camelToSpacedPascal(property.name());

            if (!shape.encodable()) continue;

            if (!propertyValues.containsKey(key)) {
                try {
                    propertyValues.put(key, values.containsKey(key) ? shape.fromJson(values.get(key)) : shape.unset());
                } catch (RuntimeException e) {
                    slots.add(new EditorWidgetFactory.PropertySlot(
                        label, new ValueShape.Leaf(JsonElement.class, null), data, () -> values.get(key), _ -> {}));
                    continue;
                }
            }

            slots.add(new EditorWidgetFactory.PropertySlot(
                label, shape, data,
                () -> propertyValues.get(key),
                value -> {
                    propertyValues.put(key, value);
                    incompleteProperties.remove(key);

                    if (Objects.equals(value, shape.unset())) {
                        values.remove(key);
                        return;
                    }

                    try {
                        values.put(key, shape.toJson(value));
                    } catch (RuntimeException e) {
                        incompleteProperties.add(key);
                    }
                }
            ));
        }

        return slots;
    }

    /// The Details rows of a canvas selection: the parent's properties when nothing is selected, the literal
    /// inputs of a single node, and none for several nodes.
    public List<? extends EditorWidgetFactory.ValueSlot> detailsOf(final List<AbstractNodeWidget> selection) {
        return switch (selection.size()) {
            case 0 -> propertySlots();
            case 1 -> selection.getFirst().literalSlots();
            default -> List.of();
        };
    }

    // ***************************************************************
    // Save, compile and debug
    // ***************************************************************

    /// Writes the Event Graph, every function canvas and signature, and the member variables into the data.
    public void flush() {
        data.setEventGraph(eventGraph.exportGraph());
        openCanvases.forEach((name, canvas) -> store(name, canvas.exportGraph()));
        externalTabs.values().forEach(open -> open.store().accept(open.canvas().exportGraph()));

        data.getOverrides().clear();
        data.getOverrides().putAll(overrides);

        data.getFunctions().clear();

        for (UserFunctionDraft function : functions) {
            data.getFunctions().add(function.toData(functionGraphs.getOrDefault(function, ScriptGraphData.EMPTY)));
        }

        data.setVariables(variableDrafts.stream().map(VariableDraft::toData).toList());
    }

    /// Validates every declared function's signature, every member variable's name and default value, and every
    /// property value, and names the first that failed, or null when all passed. A function name must be a Java
    /// identifier no other member of the blueprint has, every input needs a type, no member variable may share
    /// its name with a local a graph run binds, and every member variable's default value and every property
    /// value must encode. The canvases are checked by [#compileCanvases].
    public @Nullable Component validateMembers() {
        Component failed = null;
        Set<String> names = new HashSet<>();

        for (UserFunctionDraft function : functions) {
            Set<String> parameters = new HashSet<>();
            boolean invalid = !isJavaName(function.getName())
                || layout.method(function.getName()) != null
                || layout.field(function.getName()) != null
                || !names.add(function.getName())
                || function.getInputs().stream().anyMatch(input ->
                    input.getType() == null || !isJavaName(input.getName()) || !parameters.add(input.getName()));

            if (invalid && failed == null) {
                failed = Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_FUNCTION_NAME, function.getName());
            }
        }

        Set<String> bound = new HashSet<>();
        bound.add(BlueprintExecutor.PARENT_LOCAL);
        layout.events().forEach(event -> bound.addAll(event.parameterNames()));
        layout.functions().forEach(function -> bound.addAll(function.parameterNames()));
        functions.forEach(function -> function.getInputs().forEach(input -> bound.add(input.getName())));

        if (externalCanvases != null) bound.addAll(externalCanvases.boundLocals());

        for (VariableDraft variable : variableDrafts) {
            if (bound.contains(variable.getName()) && failed == null) {
                failed = Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_VARIABLE_NAME, variable.getName());
            }

            if (variable.isDefaultIncomplete() && failed == null) {
                failed = Component.translatable(LangKeys.GUI_BLUEPRINT_ERROR_VARIABLE_DEFAULT, variable.getName());
            }
        }

        for (BlueprintLayout.Property property : layout.properties()) {
            if (incompleteProperties.contains(property.key()) && failed == null) {
                failed = Component.translatable(
                    LangKeys.GUI_BLUEPRINT_ERROR_PROPERTY, ParseUtils.camelToSpacedPascal(property.name()));
            }
        }

        return failed;
    }

    private static boolean isJavaName(final String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;

        return name.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
    }

    /// The key a Compile marks the nodes of the function `name`'s canvas under.
    public static String functionKey(final String name) {
        return "function/" + name;
    }

    /// Validates every canvas of `data`, a script owned by `ownerType` of a parent laid out by `layout`, into
    /// `compilation`: the Event Graph under [#EVENT_GRAPH_KEY], each override and declared function under
    /// [#functionKey].
    public static void compileCanvases(
        final Compilation compilation, final BlueprintScriptData data, final BlueprintLayout layout, final Class<?> ownerType
    ) {
        BlueprintMembers members = data.members(layout);

        compilation.canvas(EVENT_GRAPH_KEY, canvasOf(ownerType, members, null, data.getEventGraph()),
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH));

        data.getOverrides().forEach((name, graph) ->
            compilation.canvas(functionKey(name), canvasOf(ownerType, members, name, graph), Component.literal(name)));

        for (BlueprintScriptData.UserFunction function : data.getFunctions()) {
            compilation.canvas(functionKey(function.name()), canvasOf(ownerType, members, function.name(), function.graph()),
                Component.literal(function.name()));
        }
    }

    /// A canvas showing `graph`, built as this session builds the canvas implementing `function` (the Event
    /// Graph for null), for a compile to validate with no screen.
    public static ScriptNodeGraphWidget canvasOf(
        final Class<?> ownerType, final BlueprintMembers members, final @Nullable String function, final ScriptGraphData graph
    ) {
        ScriptNodeGraphWidget canvas = ScriptNodeGraphWidget.create(Minecraft.getInstance().font, ownerType);

        canvas.setBlueprintMembers(members, function);
        canvas.importGraph(graph, NO_ENTRY);

        return canvas;
    }

    /// Shows the node marks of `failure` on every canvas on screen.
    public void showCompileFailure(final CompileFailure failure) {
        failure.showOn(EVENT_GRAPH_KEY, eventGraph);
        openCanvases.forEach((name, canvas) -> failure.showOn(functionKey(name), canvas));

        if (externalCanvases == null) return;

        externalTabs.forEach((key, open) -> {
            String compileKey = externalCanvases.compileKey(key);

            if (compileKey != null) failure.showOn(compileKey, open.canvas());
        });
    }

    /// Shows `records`, keyed by run site, on the canvases drawing them: every event's on the Event Graph,
    /// and each function's on its open tab.
    public void showDebugRecords(final Map<String, ExecutionRecord> records) {
        eventGraph.setDebugRecord(layout.events().stream()
            .map(event -> records.get(event.name()))
            .filter(Objects::nonNull)
            .toArray(ExecutionRecord[]::new));

        openCanvases.forEach((name, canvas) -> {
            ExecutionRecord record = records.get(name);

            if (record == null) {
                canvas.clearDebugRecord();
            } else {
                canvas.setDebugRecord(record);
            }
        });
    }

    /// Marks `failure` on the canvas drawing the run site `site`: the Event Graph for an event, the open tab
    /// for a function. False when no canvas on screen draws it.
    public boolean markRuntimeError(final String site, final ScriptFailure failure) {
        if (layout.events().stream().anyMatch(event -> event.name().equals(site))) {
            return eventGraph.markRuntimeError(failure);
        }

        ScriptNodeGraphWidget open = openCanvases.get(site);

        return open != null && open.markRuntimeError(failure);
    }

    /// Canvases a host keeps outside the session's data, such as the graphs of a widget's events. Every rewrite of
    /// the closed canvases reaches them too.
    public interface ExternalCanvases {
        /// Replaces every one of them by what `mapper` makes of it, and answers the edit that puts them back, or
        /// null when none changed.
        @Nullable WorkHistory map(UnaryOperator<ScriptGraphData> mapper);

        /// The locals their runs bind, which no member variable may be named.
        Set<String> boundLocals();

        /// The key a Compile marks the nodes of the canvas opened under `key` with, or null when it marks none.
        @Nullable String compileKey(Object key);
    }

    /// The open tab of a canvas kept outside the data, and where it keeps its graph.
    private record ExternalTab(ScriptNodeGraphWidget canvas, Component title, Consumer<ScriptGraphData> store) {
    }
}
