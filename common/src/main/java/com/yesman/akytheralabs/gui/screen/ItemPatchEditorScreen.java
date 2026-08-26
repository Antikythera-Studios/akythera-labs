package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
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
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/// Editor screen for an [ItemPatchBlueprintHolder] (an [ItemPatchBlueprint]).
///
/// Modeled on [EntityPatchEditorScreen], minus the script surface: two side columns — Components
/// (left), Details (right) — around a center Preview / graph [TabPanel]. Item patches have no
/// flows or variables, so there is no seeded Event Graph; graph tabs open per component property
/// holding a canvas ([#openResultGraphTab]). With no component focused, the Details panel shows the
/// blueprint's own fields (the parent reference).
public final class ItemPatchEditorScreen extends Screen
    implements ModalAccessibleScreen, SafeCloseScreen, NodeGraphTabHost
{
    private static final int TOP_BAR_H = 22;

    /// The script owner type of this screen's canvases: `self` and unwired owner ("Target") pins are
    /// an [ItemPatch].
    private static final Class<?> OWNER_TYPE = ItemPatch.class;

    /// The literal types offered when a value drag carries no concrete expected type.
    private static final List<Class<?>> DEFAULT_LITERAL_TYPES =
        List.of(boolean.class, int.class, float.class, double.class, String.class);

    /// Seed positions for a fresh result canvas: params entry left, result root right.
    private static final int RESULT_ENTRY_X = -180;
    private static final int RESULT_ENTRY_Y = 0;
    private static final int RESULT_ROOT_X = 120;
    private static final int RESULT_ROOT_Y = 0;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final TabPanel centerTabs;

    private final ComponentEditorPanel componentPanel;
    private final ComponentDetailsPanel detailsPanel;

    /// Open pure-node graph tabs by property key, plus their flush-back hooks (run on tab close
    /// and on save) — the [AnimatorScriptEditorScreen] pattern.
    private final Map<String, ScriptNodeGraphWidget> openGraphs = new HashMap<>();
    private final List<Runnable> graphFlushes = new ArrayList<>();

    private final ItemPatchBlueprintHolder holder;
    private final ResourceKey<ItemPatchBlueprint> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live
    /// top differs from it.
    private @Nullable WorkHistory savePoint;

    public ItemPatchEditorScreen(ResourceKey<ItemPatchBlueprint> resourceKey, ItemPatchBlueprintHolder holder) {
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
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        // The engine previewer has no item or static-mesh component, so the preview carries only
        // the camera, the background and the grid floor.
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

        centerTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        centerTabs.addTab(Component.literal("Preview"), model3DViewer);

        AdjustablePanelManager.PanelArea centerHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(centerTabs);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                centerTabs.relocate(screenRectangle);
            }
        };

        // Components (left): edits the blueprint's component tree in place — the patch holder is
        // the tree's root host, see EntityPatchEditorScreen.
        componentPanel = new ComponentEditorPanel(
            minecraft.font, this, ComponentBlueprint.ITEM_PATCH_COMPONENT_TAG,
            ItemPatchComponentHolders.REGISTRY, holder, null, () -> {}, this::onComponentFocused);

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this);
        // Nothing focused yet → blueprint-level fields (the parent reference).
        detailsPanel.showComponent(holder);

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: three columns [Components | center tabs | Details].
        // Divider order (== serialize order): col-left(0), col-right(1).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 22, 76);

        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        columns.get(0).area(componentPanel);
        columns.get(1).area(centerHostArea);
        // Keep the previewer's render target sized to the (possibly hidden) Preview tab after a resize.
        columns.get(1).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        columns.get(2).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    // ***************************************************************
    // Node graph tabs
    // ***************************************************************

    /// Opens (or re-focuses) the closeable graph tab for one pure-node component property.
    /// `flush` receives the exported graph on tab close and on save.
    public void openPureGraphTab(String key, Component title, ScriptGraphData data, Consumer<ScriptGraphData> flush) {
        ScriptNodeGraphWidget existing = openGraphs.get(key);
        if (existing != null) {
            centerTabs.setActiveIndex(centerTabs.indexOfContent(existing));
            return;
        }

        ScriptNodeGraphWidget graph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        graph.setSuggestionProvider(this::buildNodeSuggestions);
        graph.setScriptOwnerType(OWNER_TYPE);
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        if (!data.isEmpty()) graph.importGraph(data, (_, _, _) -> null);

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

        ScriptNodeGraphWidget graph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        graph.setSuggestionProvider(this::buildNodeSuggestions);
        graph.setScriptOwnerType(OWNER_TYPE);
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        graph.setDataFlowOnly(true);

        if (data.isEmpty()) {
            graph.addNode(new ContextEntryNodeWidget(
                minecraft.font, Component.literal("Params"), params, RESULT_ENTRY_X, RESULT_ENTRY_Y));
            graph.addNode(new TransitionResultNodeWidget(
                minecraft.font, Component.literal("Condition"), RESULT_ROOT_X, RESULT_ROOT_Y));
        } else {
            // The entry row's pin types are structural (not persisted) — re-type them from `params`
            // by label; an unknown label degrades to a wildcard pin.
            graph.importGraph(data,
                (x, y, labels) ->
                    new ContextEntryNodeWidget(
                        minecraft.font, Component.literal("Params"),
                        labels.stream()
                            .map(
                                label ->
                                params.stream().filter(p -> p.name().equals(label))
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

    /// Pure-only suggestions: literals, pure SCRIPT functions, type converts arrive with the first
    /// real use. No flow nodes and no variables — item patches have no script surface.
    private List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> buildNodeSuggestions(ScriptNodeGraphWidget graph, NodeSuggestContext context) {
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = new ArrayList<>();

        Class<?> expectedType = context.expectedType();

        if (LiteralValueSupport.isSupported(expectedType)) {
            suggestions.add(literalSuggestion(expectedType));
        } else if (expectedType == null) {
            for (Class<?> type : DEFAULT_LITERAL_TYPES) suggestions.add(literalSuggestion(type));
        }

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (!function.isPure()) continue;
            if (function.metadata().usage() != ScriptFunction.Usage.SCRIPT) continue;
            // Comparison functions are surfaced as dedicated type-detecting comparator nodes
            // (appended below) instead of plain pure-function nodes.
            if (function.functionSignature().parameters().stream().anyMatch(java.util.Comparator.class::isAssignableFrom)) continue;
            if (!context.functionFilter().test(function)) continue;
            Class<?> returnType = function.functionSignature().returnType();
            if (returnType == void.class || returnType == Void.class) continue;
            if (!AbstractNodeWidget.valueAssignable(returnType, expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> new PureFunctionNodeWidget(font, function, x, y),
                function.metadata().category()));
        }

        // The "Math" / "Logic" nodes follow, gated like the animator screen's
        // addPureFunctionSuggestions: output must fit the target slot, dragged value (if any) must
        // be a valid operand.
        Class<?> providedType = context.providedType();

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (ComparatorNodeWidget.Operator op : ComparatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new ComparatorNodeWidget(font, op, x, y),
                    "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(double.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (OperatorNodeWidget.Operator op : OperatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new OperatorNodeWidget(font, op, x, y),
                    "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, boolean.class)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("NOT"),
                (font, x, y) -> new NotNodeWidget(font, x, y),
                "Logic"));

            for (LogicalOperationNodeWidget.Operator op : LogicalOperationNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new LogicalOperationNodeWidget(font, op, x, y),
                    "Logic"));
            }
        }

        return suggestions;
    }

    private NodeSuggestModal.Suggestion<NodeWidgetFactory> literalSuggestion(Class<?> type) {
        return new NodeSuggestModal.Suggestion<>(
            Component.literal("Literal: " + type.getSimpleName()),
            (font, x, y) -> new LiteralNodeWidget(font, type, x, y));
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
            AkytheraLabs.LOGGER.error("Failed to save item patch '{}'", resourceKey.identifier(), error);
            return;
        }
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Builds the runtime blueprint in memory only — surfaces dangling references / bad modifier
    /// ids before export, mirroring the entity screen's compile step.
    private void compile() {
        flushToHolder();

        if (validateLiveCanvases()) {
            AkytheraLabs.LOGGER.warn("Compile of item patch '{}' failed validation", resourceKey.identifier());
            return;
        }

        try {
            ItemPatchBlueprint blueprint = holder.makeObject();
            AkytheraLabs.LOGGER.info("Compiled item patch '{}': {}", resourceKey.identifier(), blueprint);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to compile item patch '{}'", resourceKey.identifier(), e);
        }
    }

    /// Validates every canvas with widgets on screen — each open result / condition sub-tab — and
    /// returns whether any failed. Every canvas is validated even once one has failed:
    /// [ScriptNodeGraphWidget#validate] is what paints a node's red "ERROR!" banner, so stopping early
    /// would leave the offending node on a later tab unmarked.
    private boolean validateLiveCanvases() {
        boolean failed = false;

        for (ScriptNodeGraphWidget open : openGraphs.values()) {
            if (!open.validate().isEmpty()) failed = true;
        }

        return failed;
    }

    /// Flushes any open graph tabs. Components and the parent reference are edited on the holder
    /// in place, see [EntityPatchEditorScreen]'s flush.
    private void flushToHolder() {
        graphFlushes.forEach(Runnable::run);
    }

    /// Focus routes to the Details panel; nothing focused shows the blueprint's own fields. `host` is
    /// the shown component's context, see [EntityPatchEditorScreen#onComponentFocused].
    private void onComponentFocused(
        @Nullable ComponentDataHolder<?> component, @Nullable ComponentAttachableHolder host
    ) {
        detailsPanel.showComponent(component == null ? holder : component, host);
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // Screen lifecycle / input — mirrors EntityPatchEditorScreen
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
