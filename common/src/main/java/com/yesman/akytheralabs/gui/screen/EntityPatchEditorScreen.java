package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.akythera.entitypatch.component.BoneSocketHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EquipmentComponentHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.panel.script.VariableEditController;
import com.yesman.akytheralabs.gui.panel.script.VariablePanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.VariableDropModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.viewer.BoneSocketPreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ItemModelComponent;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// Editor screen for an [EntityPatchBlueprintHolder] (an [EntityPatchBlueprint]): a three-column
/// layout under the top menu bar — Components + Variables on the left, a Preview / Event Graph tab
/// pair in the center, a Details panel on the right.
///
/// The Event Graph hosts the blueprint's three entry points — `onConstruct`, `onJoinWorld`, `update`
/// — as [EntityPatchEntryNodeWidget] nodes on one canvas. A focused component's canvas properties
/// open beside it as their own tabs ([#openResultGraphTab]).
///
/// `File > Change Parent` picks the [EntityPatch] subclass the engine builds this patch with, which is
/// the script owner type of every canvas on the screen.
public final class EntityPatchEditorScreen extends Screen
    implements ModalAccessibleScreen, SafeCloseScreen, NodeGraphTabHost
{
    private static final int TOP_BAR_H = 22;

    /// Seed layout for the three event entry nodes, stacked top-to-bottom in `onConstruct` →
    /// `onJoinWorld` → `update` order.
    private static final int ENTRY_X = 60;
    private static final int ENTRY_Y = 60;
    private static final int ENTRY_GAP = 130;

    /// Seed positions for a fresh result canvas: params entry left, result root right.
    private static final int RESULT_ENTRY_X = -180;
    private static final int RESULT_ENTRY_Y = 0;
    private static final int RESULT_ROOT_X = 120;
    private static final int RESULT_ROOT_Y = 0;

    /// Open result-graph tabs by property key, plus their flush-back hooks (run on tab close and on
    /// save).
    private final Map<String, ScriptNodeGraphWidget> openGraphs = new HashMap<>();
    private final List<Runnable> graphFlushes = new ArrayList<>();

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final TabPanel centerTabs;

    /// The live Event Graph canvas hosting the three entry nodes, flushed to the holder on save and
    /// compile.
    private final ScriptNodeGraphWidget eventGraph;

    private final ComponentEditorPanel componentPanel;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;

    /// Working copies of the blueprint's variables; flushed to the holder on save.
    private final List<VariableDraft> variableDrafts;
    private final VariableEditController variableController;

    private final EntityPatchBlueprintHolder holder;
    private final ResourceKey<EntityPatchBlueprint> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top
    /// differs from it.
    private @Nullable WorkHistory savePoint;

    public EntityPatchEditorScreen(ResourceKey<EntityPatchBlueprint> resourceKey, EntityPatchBlueprintHolder holder) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;
        this.holder = holder;

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            _ -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.COMPILE, this::compile)
                .onChoices(TopMenuOption.CHANGE_PARENT, parentChoices())
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        // Preview: the entity picked when the blueprint was created, stored on the holder
        model3DViewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );
        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(2.0F, 50, 68, 68, 68, 255));
        EntityPreviewModel.display(model3DViewer, holder.getPreviewModel(), true, true);

        // Event Graph: a live node canvas seeded with the three entry nodes
        eventGraph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        eventGraph.setSuggestionProvider(this::buildNodeSuggestions);
        eventGraph.setScriptOwnerType(ownerType());
        eventGraph.setUsage(ScriptFunction.Usage.SCRIPT);
        eventGraph.setSelectionHandler(this::onNodeSelected);

        ScriptGraphData savedEventGraph = holder.getEventGraph();
        if (savedEventGraph.isEmpty()) {
            eventGraph.addNode(new EntityPatchEntryNodeWidget(
                minecraft.font, EntityPatchEntryNodeWidget.Kind.ON_CONSTRUCT, ENTRY_X, ENTRY_Y));
            eventGraph.addNode(new EntityPatchEntryNodeWidget(
                minecraft.font, EntityPatchEntryNodeWidget.Kind.ON_JOIN_WORLD, ENTRY_X, ENTRY_Y + ENTRY_GAP));
            eventGraph.addNode(new EntityPatchEntryNodeWidget(
                minecraft.font, EntityPatchEntryNodeWidget.Kind.UPDATE, ENTRY_X, ENTRY_Y + ENTRY_GAP * 2));
        } else {
            // Entry rows rebuild from their persisted kind marker, so the entry factory is unused
            eventGraph.importGraph(savedEventGraph, (_, _, _) -> null);
        }

        centerTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        centerTabs
            .addTab(Component.literal("Preview"), model3DViewer)
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), eventGraph);

        AdjustablePanelManager.PanelArea centerHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(centerTabs);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                centerTabs.relocate(screenRectangle);
            }
        };

        // Components (top-left): edits the blueprint's component tree in place, the patch holder
        // being the tree's root host
        componentPanel = new ComponentEditorPanel(
            minecraft.font, this, EntityPatchComponentHolders.REGISTRY, holder,
            RenderUtils.renderStateOf(patchedEntityTypeId()),
            Map::of, this::rebuildSocketPreviews, this::onComponentFocused);

        // Variables (bottom-left): a working draft list edited inline (name + type combo per row),
        // with no Details rows of their own
        variableDrafts = new ArrayList<>();
        for (ScriptVariables.VariableInfo info : holder.getVariables()) {
            variableDrafts.add(VariableDraft.from(info));
        }
        variableController = new VariableEditController(minecraft.font, variableDrafts, this::onVariableRenamed, this::onVariableRetyped);
        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        variablePanel.setDropTarget(this::onVariableDropped);
        variableController.setViews(variablePanel::rebuildList, () -> {});

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this);

        rebuildSocketPreviews();

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: three columns [left | center | right]; the left column splits top/bottom
        // (Components / Variables). Divider order (== serialize order): col-left(0), col-right(1),
        // left-split(2).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 22, 76, 45);

        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        var leftSplit = columns.get(0).subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(2));

        leftSplit.get(0).area(componentPanel);
        leftSplit.get(1).area(variablePanel);
        columns.get(1).area(centerHostArea);
        // Keep the previewer's render target sized to the (possibly hidden) Preview tab after a resize.
        columns.get(1).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        columns.get(2).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    // ***************************************************************
    // Parent (the patch's constructor)
    // ***************************************************************

    /// One choice per [ScriptFunction.Usage#ENTITY_PATCH_CTOR] function the patched entity can be
    /// built as, filling the top menu's `Change Parent` sub-list.
    private List<UniversalMenu.Choice> parentChoices() {
        Class<? extends Entity> entityClass = RenderUtils.entityClassOf(patchedEntityTypeId());
        List<UniversalMenu.Choice> choices = new ArrayList<>();

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (function.metadata().usage() != ScriptFunction.Usage.ENTITY_PATCH_CTOR) continue;

            List<Class<?>> parameters = function.functionSignature().parameters();
            if (parameters.size() < 2 || !parameters.get(1).isAssignableFrom(entityClass)) continue;

            choices.add(new UniversalMenu.Choice(Component.literal(function.metadata().label()), () -> changeParent(function)));
        }

        return choices;
    }

    /// Re-parents the blueprint, moving the script owner type of every canvas on screen with it and
    /// re-flagging whatever the new owner can no longer reach.
    private void changeParent(ScriptFunction ctor) {
        Consumer<ScriptFunction> writer = value -> {
            holder.setCtor(value);

            Class<?> owner = value.functionSignature().returnType();

            eventGraph.setScriptOwnerType(owner);
            openGraphs.values().forEach(graph -> graph.setScriptOwnerType(owner));

            validateLiveCanvases();
        };

        HistoryManager.getInstance().pushHistory(new DataChange<>(holder.getCtor(), ctor, writer));

        writer.accept(ctor);
    }

    private Class<?> ownerType() {
        return holder.ownerType();
    }

    /// The entity type this patch is keyed by: its registry id's path re-read as an entity type id
    /// (`myproject:minecraft/player` → `minecraft:player`).
    private Identifier patchedEntityTypeId() {
        String path = resourceKey.identifier().getPath();
        int split = path.indexOf('/');

        return split < 0
            ? resourceKey.identifier()
            : Identifier.fromNamespaceAndPath(path.substring(0, split), path.substring(split + 1));
    }

    // ***************************************************************
    // [NodeGraphTabHost]
    // ***************************************************************

    @Override
    public void openResultGraphTab(
        String key,
        Component title,
        ScriptGraphData data,
        List<ContextEntryNodeWidget.ContextParam> params,
        Class<?> resultType,
        Consumer<ScriptGraphData> flush
    ) {
        ScriptNodeGraphWidget existing = openGraphs.get(key);
        if (existing != null) {
            centerTabs.setActiveIndex(centerTabs.indexOfContent(existing));
            return;
        }

        List<ContextEntryNodeWidget.ContextParam> resolved = narrowOwnerParams(params);

        ScriptNodeGraphWidget graph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        graph.setSuggestionProvider(this::buildNodeSuggestions);
        graph.setScriptOwnerType(ownerType());
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        graph.setDataFlowOnly(true);
        graph.setSelectionHandler(this::onNodeSelected);

        if (data.isEmpty()) {
            graph.addNode(new ContextEntryNodeWidget(
                minecraft.font, Component.literal("Params"), resolved, RESULT_ENTRY_X, RESULT_ENTRY_Y));
            graph.addNode(new TransitionResultNodeWidget(
                minecraft.font, title, RESULT_ROOT_X, RESULT_ROOT_Y));
        } else {
            // The entry row's pin types are structural (not persisted) — re-type them from `resolved`
            // by label; an unknown label degrades to a wildcard pin.
            graph.importGraph(data,
                (x, y, labels) ->
                    new ContextEntryNodeWidget(
                        minecraft.font, Component.literal("Params"),
                        labels.stream()
                            .map(
                                label ->
                                resolved.stream().filter(p -> p.name().equals(label))
                                    .findFirst()
                                    .orElse(new ContextEntryNodeWidget.ContextParam(label, Object.class))
                            )
                            .toList(),
                        x, y
                    )
            );
        }

        graph.setResultType(resultType);

        Runnable flushRun = () -> flush.accept(graph.exportGraph());
        openGraphs.put(key, graph);
        graphFlushes.add(flushRun);

        centerTabs.addCloseableTab(title, graph, () -> {
            flushRun.run();
            openGraphs.remove(key);
            graphFlushes.remove(flushRun);
        });
        centerTabs.setActiveIndex(centerTabs.indexOfContent(graph));
    }

    /// Selection handler for every canvas on this screen: a node carrying its own editable properties
    /// takes over the Details panel, anything else hands the panel back to the focused component.
    private void onNodeSelected(@Nullable AbstractNodeWidget node) {
        if (node instanceof MeshLiteralNodeWidget meshLiteral) {
            detailsPanel.showComponent(meshLiteral);
            return;
        }

        componentPanel.refocusDetails();
    }

    /// A param declared as [EntityPatch] is the owner slot: it takes this patch's parent type.
    private List<ContextEntryNodeWidget.ContextParam> narrowOwnerParams(
        List<ContextEntryNodeWidget.ContextParam> params
    ) {
        return params.stream()
            .map(p -> p.type() == EntityPatch.class
                ? new ContextEntryNodeWidget.ContextParam(p.name(), ownerType())
                : p)
            .toList();
    }

    // ***************************************************************
    // Node suggestions (SCRIPT domain only: the Event Graph)
    // ***************************************************************

    /// The literal types offered when a value drag carries no concrete expected type.
    private static final List<Class<?>> DEFAULT_LITERAL_TYPES =
        List.of(boolean.class, int.class, float.class, double.class, String.class);

    /// The category folder member-variable getters are filed under.
    private static final String MEMBER_VARIABLES_CATEGORY = "Member Variables";

    /// Routes a gesture to the suggestion sets its [NodeSuggestContext] asks for.
    private List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> buildNodeSuggestions(ScriptNodeGraphWidget graph, NodeSuggestContext context) {
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = new ArrayList<>();
        boolean pureCanvas = context.dataFlowOnly();

        if (context.flow() && !pureCanvas) addFlowSuggestions(suggestions, context.providedType(), context.functionFilter());
        if (context.valueProviders()) addValueProviderSuggestions(suggestions, context.expectedType(), pureCanvas);
        if (context.pureFunctions()) addPureFunctionSuggestions(suggestions, context.expectedType(), context.providedType(), context.functionFilter());

        // The empty-canvas "All Actions" menu also offers the member getters
        if (context.flow() && context.pureFunctions() && context.providedType() == null && !pureCanvas) {
            addMemberVariableGetters(suggestions, null);
        }

        addTypeConvertSuggestions(suggestions, context);
        return suggestions;
    }

    /// Flow nodes: Sequence / Branch, a "Set" node per variable, and a flow-function node per
    /// **non-pure** SCRIPT [ScriptFunction] passing `filter`.
    private void addFlowSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter
    ) {
        if (providedType == null) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE),
                (font, x, y) -> new SequenceNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE), x, y)));
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH),
                (font, x, y) -> new BranchingNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH), x, y)));
        }

        for (VariableDraft draft : variableDrafts) {
            String name = draft.getName();
            Class<?> type = draft.getType().type();
            if (!AbstractNodeWidget.valueAssignable(providedType, type)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
                (font, x, y) -> new ScriptVariableSetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                MEMBER_VARIABLES_CATEGORY));
        }

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (function.isPure()) continue;
            if (function.metadata().usage() != ScriptFunction.Usage.SCRIPT) continue;
            if (!ownerAccepts(function)) continue;
            if (!filter.test(function)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> new FlowFunctionNodeWidget(font, function, x, y),
                function.metadata().category()));
        }
    }

    /// Value providers filling a **data input**: the Self node, an inline Literal, and a getter per
    /// variable whose type can satisfy `expectedType`. A pure canvas gets no getters.
    private void addValueProviderSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        boolean pureCanvas
    ) {
        if (expectedType == null || expectedType.isAssignableFrom(ownerType())) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF),
                (font, x, y) -> new SelfNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF), x, y)));
        }

        if (LiteralValueSupport.isSupported(expectedType)) {
            suggestions.add(literalSuggestion(expectedType));
        } else if (expectedType == null) {
            for (Class<?> type : DEFAULT_LITERAL_TYPES) suggestions.add(literalSuggestion(type));
        }

        if (!pureCanvas) addMemberVariableGetters(suggestions, expectedType);
    }

    /// A getter node per variable whose type can satisfy `expectedType` (all when null).
    private void addMemberVariableGetters(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType
    ) {
        for (VariableDraft draft : variableDrafts) {
            String name = draft.getName();
            Class<?> type = draft.getType().type();
            if (!AbstractNodeWidget.valueAssignable(type, expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
                (font, x, y) -> new ScriptVariableGetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name), name, type, x, y),
                MEMBER_VARIABLES_CATEGORY));
        }
    }

    /// A node per **pure** value-returning SCRIPT [ScriptFunction] passing `filter`, plus the Math /
    /// Logic nodes (comparators, arithmetic operators, NOT, binary logic).
    private void addPureFunctionSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter
    ) {
        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (!function.isPure()) continue;
            if (function.metadata().usage() != ScriptFunction.Usage.SCRIPT) continue;
            if (isComparatorFunction(function)) continue;
            if (!ownerAccepts(function)) continue;
            if (!filter.test(function)) continue;
            Class<?> returnType = function.functionSignature().returnType();
            if (returnType == void.class || returnType == Void.class) continue;
            if (!AbstractNodeWidget.valueAssignable(returnType, expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> ScriptNodeGraphWidget.isSelect(function)
                    ? new SelectNodeWidget(font, function, x, y)
                    : new PureFunctionNodeWidget(font, function, x, y),
                function.metadata().category()));
        }

        if (AbstractNodeWidget.valueAssignable(AssetAccessor.class, expectedType)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_NODE_MESH),
                (font, x, y) -> new MeshLiteralNodeWidget(
                    font, MeshLiteralNodeWidget.resolve(DataHolder.EMPTY_REFERENCE), x, y),
                "Asset"));
        }

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)) {
            for (ComparatorNodeWidget.Operator op : ComparatorNodeWidget.Operator.values()) {
                // Ordering runs on the numeric comparator; equality runs on any operand.
                if (op != ComparatorNodeWidget.Operator.EQUAL
                    && !AbstractNodeWidget.valueAssignable(providedType, double.class)) continue;

                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new ComparatorNodeWidget(font, op, x, y),
                    op == ComparatorNodeWidget.Operator.EQUAL ? "Logic" : "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(double.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (OperatorNodeWidget.Operator op : OperatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new OperatorNodeWidget(font, op, x, y), "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, boolean.class)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("NOT"), (font, x, y) -> new NotNodeWidget(font, x, y), "Logic"));
            for (LogicalOperationNodeWidget.Operator op : LogicalOperationNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new LogicalOperationNodeWidget(font, op, x, y), "Logic"));
            }
        }
    }

    /// Offers explicit numeric type-conversion entries for a pin dropped on empty canvas.
    private void addTypeConvertSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        NodeSuggestContext context
    ) {
        Class<?> provided = context.providedType();
        if (provided != null) {
            ScriptType from = scriptTypeOf(provided);
            if (from == null) return;
            for (Class<?> target : from.implicitlyCastableTypes().keySet()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal("Convert to " + TypeConvertNodeWidget.typeName(target)),
                    (font, x, y) -> new TypeConvertNodeWidget(font, provided, target, x, y)));
            }
            return;
        }

        Class<?> expected = context.expectedType();
        if (expected == null) return;
        Class<?> expectedBoxed = boxedType(expected);
        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (!st.implicitlyCastableTypes().containsKey(expectedBoxed)) continue;
            Class<?> source = st.type();
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("Convert " + TypeConvertNodeWidget.typeName(source)
                    + " to " + TypeConvertNodeWidget.typeName(expected)),
                (font, x, y) -> new TypeConvertNodeWidget(font, source, expected, x, y)));
        }
    }

    private static boolean isComparatorFunction(ScriptFunction function) {
        return function.functionSignature().parameters().stream().anyMatch(Comparator.class::isAssignableFrom);
    }

    /// Whether `function` can be called on this patch: a member function's receiver slot (parameter 0)
    /// has to accept the parent type.
    private boolean ownerAccepts(ScriptFunction function) {
        if (!function.isMember()) return true;

        List<Class<?>> parameters = function.functionSignature().parameters();
        return !parameters.isEmpty() && ScriptType.castable(ownerType(), parameters.getFirst());
    }

    private NodeSuggestModal.Suggestion<NodeWidgetFactory> literalSuggestion(Class<?> type) {
        return new NodeSuggestModal.Suggestion<>(
            Component.literal("Literal: " + type.getSimpleName()),
            (font, x, y) -> new LiteralNodeWidget(font, type, x, y));
    }

    private static @Nullable ScriptType scriptTypeOf(Class<?> type) {
        Class<?> boxed = boxedType(type);
        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (boxedType(st.type()) == boxed) return st;
        }
        return null;
    }

    private static Class<?> boxedType(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == boolean.class) return Boolean.class;
        if (t == char.class) return Character.class;
        return t;
    }

    // ***************************************************************
    // Variable node hooks + drag-drop
    // ***************************************************************
    private void onVariableRenamed(String oldName, String newName) {
        eventGraph.renameVariableNodes(oldName, newName);
    }

    private @Nullable WorkHistory onVariableRetyped(String name, Class<?> newType) {
        return eventGraph.retypeVariableNodes(name, newType);
    }

    /// A variable released over the Event Graph opens a [VariableDropModal] at the cursor; picking a
    /// row spawns the matching Get / Set node there.
    private boolean onVariableDropped(VariableDraft draft, double screenX, double screenY) {
        if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        String name = draft.getName();
        Class<?> type = draft.getType().type();

        Runnable spawnGet = () -> graph.spawnNodeAt(
            (font, x, y) -> new ScriptVariableGetterNodeWidget(
                font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name), name, type, x, y),
            screenX, screenY);

        VariableDropModal modal = new VariableDropModal(
            minecraft.font, name,
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
            spawnGet,
            () -> graph.spawnNodeAt(
                (font, x, y) -> new ScriptVariableSetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                screenX, screenY));

        AkytheraEditorLayoutManager.getInstance().openModalWidget(modal, (int) screenX, (int) screenY);
        return true;
    }

    // ***************************************************************
    // Widget registration / layout
    // ***************************************************************
    @Override
    protected void init() {
        super.init();

        layoutWidgets();

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
    }

    private void layoutWidgets() {
        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
    }

    // ***************************************************************
    // Save / compile
    // ***************************************************************

    /// Flattens the working state onto the holder and writes it to disk asynchronously.
    private java.util.concurrent.CompletableFuture<Void> save() {
        flushToHolder();

        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);
        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> onSaveComplete(awaitModal, error), minecraft);
    }

    private void onSaveComplete(WidgetContainerModal awaitModal, @Nullable Throwable error) {
        if (AkytheraEditorLayoutManager.getInstance().getModal() == awaitModal) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
        }
        if (error != null) {
            AkytheraLabs.LOGGER.error("Failed to save entity patch '{}'", resourceKey.identifier(), error);
            return;
        }
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Compiles the live canvas into the runtime blueprint in memory only. Marks any invalid node with
    /// a red ERROR banner and aborts on the first failure.
    private void compile() {
        flushToHolder();

        if (validateLiveCanvases()) {
            AkytheraLabs.LOGGER.warn("Compile of entity patch '{}' failed validation", resourceKey.identifier());
            return;
        }

        // Unresolved (failed-decode) nodes are a hard compile error
        if (!holder.getEventGraph().failedNodes().isEmpty()) {
            for (ScriptGraphData.FailedNode f : holder.getEventGraph().failedNodes()) {
                AkytheraLabs.LOGGER.warn("Compile of entity patch '{}' aborted — unresolved node (id {}): {}",
                    resourceKey.identifier(), f.id(), f.message());
            }
            return;
        }

        try {
            EntityPatchBlueprint blueprint = holder.makeObject();
            AkytheraLabs.LOGGER.info("Compiled entity patch '{}': {}", resourceKey.identifier(), blueprint);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to compile entity patch '{}'", resourceKey.identifier(), e);
        }
    }

    /// Validates every canvas with widgets on screen — the Event Graph plus each open result /
    /// condition sub-tab — and returns whether any failed. [ScriptNodeGraphWidget#validate] paints a
    /// node's red "ERROR!" banner, so no canvas is skipped once one has failed.
    private boolean validateLiveCanvases() {
        boolean failed = !eventGraph.validate().isEmpty();

        for (ScriptNodeGraphWidget open : openGraphs.values()) {
            if (!open.validate().isEmpty()) failed = true;
        }

        return failed;
    }

    /// Writes the Event Graph, the variables and any open canvas tab back onto the holder. Components
    /// are edited on the holder's own tree and need no flush.
    private void flushToHolder() {
        holder.setEventGraph(eventGraph.exportGraph());
        holder.setVariables(variableDrafts.stream().map(VariableDraft::toInfo).toList());
        graphFlushes.forEach(Runnable::run);
    }

    /// [ComponentEditorPanel] focus callback: shows the component in the Details panel, with `host` as
    /// its rows' context.
    private void onComponentFocused(
        @Nullable ComponentDataHolder<?> component, @Nullable ComponentAttachableHolder host
    ) {
        detailsPanel.showComponent(component, host);

        model3DViewer.focus(component instanceof BoneSocketHolder socket ? previewOf(socket) : null);
    }

    /// Rebuilds the preview's component stand-ins from the component tree.
    private void rebuildSocketPreviews() {
        // The markers are replaced wholesale, so the focus is carried across by socket holder
        BoneSocketHolder refocus = model3DViewer.getFocused() instanceof BoneSocketPreviewComponent preview
            ? preview.getSocket()
            : null;

        model3DViewer.focus(null);
        model3DViewer.removeAllComponents(BoneSocketPreviewComponent.class);
        model3DViewer.removeAllComponents(ItemModelComponent.class);

        EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);

        if (model == null) return;

        ComponentAttachableHolder.flatten(holder)
            .filter(BoneSocketHolder.class::isInstance)
            .map(BoneSocketHolder.class::cast)
            .forEach(socket -> addSocketPreview(socket, model));

        if (refocus != null) model3DViewer.focus(previewOf(refocus));
    }

    /// One socket's marker, plus an item model drawn at the marker for every equipment component
    /// attached under it.
    private void addSocketPreview(BoneSocketHolder socket, EntityModelComponent model) {
        BoneSocketPreviewComponent marker = new BoneSocketPreviewComponent(
            socket, model,
            () -> detailsPanel.showComponent(socket, hostOf(socket)),
            componentPanel::focusComponent,
            this::openSocketOptions
        );

        model3DViewer.addComponent(marker);

        socket.getComponents().values().stream()
            .filter(EquipmentComponentHolder.class::isInstance)
            .map(EquipmentComponentHolder.class::cast)
            .forEach(equipment -> {
                ItemModelComponent itemModel = new ItemModelComponent(equipment::getPreviewItem);

                itemModel.setDisplayContextSource(equipment::getItemDisplayContext);
                itemModel.placeUnder(marker::worldTransform, TransformHolder.identity(), null);

                model3DViewer.addComponent(itemModel);
            });
    }

    /// The marker standing for `socket`, or null when the preview has no model to hang it off.
    private @Nullable BoneSocketPreviewComponent previewOf(BoneSocketHolder socket) {
        for (int i = 0; ; i++) {
            BoneSocketPreviewComponent preview = model3DViewer.getComponent(BoneSocketPreviewComponent.class, i);

            if (preview == null) return null;
            if (preview.getSocket() == socket) return preview;
        }
    }

    /// The component `socket` is attached under.
    private @Nullable ComponentAttachableHolder hostOf(BoneSocketHolder socket) {
        return ComponentAttachableHolder.flatten(holder)
            .filter(ComponentAttachableHolder.class::isInstance)
            .map(ComponentAttachableHolder.class::cast)
            .filter(host -> host.getComponents().containsValue(socket))
            .findFirst()
            .orElse(null);
    }

    /// Right-press on a focused socket in the preview.
    private void openSocketOptions(BoneSocketHolder socket) {
        ContextMenuContainer menu = ContextMenuContainer.builder(minecraft)
            .entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_SOCKET_FOCUS_DETAILS))
                .onPress(_ -> detailsPanel.showComponent(socket, hostOf(socket)))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_SOCKET_RESET_TRANSFORM))
                .onPress(_ -> resetSocketTransform(socket))
                .done()
            .build();

        int scale = AkytheraLabsConfig.getEditorGuiScale();

        AkytheraEditorLayoutManager.getInstance().openModalWidget(
            menu,
            (int) Math.round(minecraft.mouseHandler.xpos() / scale),
            (int) Math.round(minecraft.mouseHandler.ypos() / scale));
    }

    private void resetSocketTransform(BoneSocketHolder socket) {
        Consumer<Transform> writer = transformWriterFor(socket);

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            socket.getLocalTransform().makeObject(), Transform.IDENTITY, writer));

        writer.accept(Transform.IDENTITY);
    }

    /// Writes a transform into `socket` and refreshes the Details rows showing it.
    private Consumer<Transform> transformWriterFor(BoneSocketHolder socket) {
        return transform -> {
            socket.getLocalTransform().readFrom(transform);
            detailsPanel.showComponent(socket, hostOf(socket));
        };
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // Screen lifecycle / input
    // ***************************************************************
    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            minecraft.font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                AkytheraEditorLayoutManager.getInstance().closeModal();
                save().thenRun(this::onClose);
            },
            () -> {
                if (hasUnsavedChanges()) AkytheraProject.getCurrentProject().reloadEntryFromDisk(resourceKey);
                onClose();
            }
        );
    }

    @Override
    public void onClose() {
        model3DViewer.destroy();
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            handleQuitRequest();
            return true;
        }
        if (EditorKeyMappings.SAVE.matches(event)) {
            save();
            return true;
        }
        if (EditorKeyMappings.REDO.matches(event)) {
            HistoryManager.getInstance().redo();
            return true;
        }
        if (EditorKeyMappings.UNDO.matches(event)) {
            HistoryManager.getInstance().undo();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        var modal = getHoveringModal(x, y);
        if (modal != null) return Optional.of(modal);
        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener g) {
        if (isModal(g)) {
            if (g != null) g.setFocused(true);
        } else {
            super.setFocused(g);
        }
    }

    @Override
    public @Nullable GuiEventListener getFocused() {
        var modal = AkytheraEditorLayoutManager.getInstance().getModal();
        if (modal != null) return modal;
        return super.getFocused();
    }
}
