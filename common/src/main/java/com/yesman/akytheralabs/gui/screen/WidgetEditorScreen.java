package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.ScriptFailure;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetContainerScript;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintParentRef;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetComponentHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetComponentHolders;
import com.yesman.akytheralabs.akythera.widget.WidgetGraphs;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.panel.script.VariableEditController;
import com.yesman.akytheralabs.gui.panel.script.VariablePanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.CompileResultToast;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.VariableDropModal;
import com.yesman.akytheralabs.gui.widget.WidgetCanvas;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ComponentGetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeSuggestions;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableGetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableSetterNodeWidget;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Editor screen for a [WidgetBlueprintHolder] (a [WidgetBlueprint]): Widgets, Functions and Variables on the
/// left, a Canvas / Event Graph tab pair in the center, a Details panel on the right.
///
/// The Canvas draws the widgets as the game builds them, see [WidgetCanvas]. The [BlueprintEditorSession] lays the
/// Event Graph, the Functions panel and the Details rows out of the script
/// interface the container class maps to. With no widget focused, Details shows the root: its [WidgetUsage] and
/// title.
///
/// The parent is the container class the engine builds, which decides the [WidgetUsage]: `File > Change Parent`
/// and the Widget Type row both re-open the screen on another one. A source code [WidgetContainerScript] locks
/// everything below the top menu.
public final class WidgetEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen, ComponentPathHost {
    private static final int TOP_BAR_H = 22;

    /// Height of a Details row, in screen pixels.
    private static final int ROW_H = 20;

    /// Width of the parent picker, in screen pixels.
    private static final int PARENT_PICKER_W = 240;

    private static final int LOCK_DIM_COLOR = 0xC0101010;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final TabPanel centerTabs;

    /// The Event Graph, the function canvases and the Functions panel, flushed to the holder on save and compile.
    private final BlueprintEditorSession session;

    private final WidgetCanvas canvas;
    private final ComponentEditorPanel widgetPanel;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;

    /// Fills the root's rows into Details.
    private final EditorWidgetFactory rootRows;

    /// Working copies of the blueprint's variables, flushed to the holder by [#session] on save.
    private final List<VariableDraft> variableDrafts = new ArrayList<>();

    /// The node palette of the Event Graph.
    private final ScriptNodeSuggestions nodeSuggestions = new ScriptNodeSuggestions(this::ownerType, variableDrafts);
    private final VariableEditController variableController;

    private final WidgetBlueprintHolder holder;
    private final ResourceKey<WidgetBlueprint> resourceKey;

    /// History stack top at the last save (or screen open). The title shows a `*` while the live top differs.
    private @Nullable WorkHistory savePoint;

    /// Whether runtime failures were marked on the last frame, so the frame after they are cleared unmarks them.
    private boolean failuresMarked;

    public WidgetEditorScreen(final ResourceKey<WidgetBlueprint> resourceKey, final WidgetBlueprintHolder holder) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;
        this.holder = holder;
        this.rootRows = new EditorWidgetFactory(minecraft.font);

        centerTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        for (BlueprintScriptData.Variable variable : holder.getScript().getVariables()) {
            variableDrafts.add(new VariableDraft(variable));
        }

        session = new BlueprintEditorSession(
            minecraft.font, this, holder.layout(), ownerType(), holder.getScript(), centerTabs, variableDrafts,
            nodeSuggestions::build, this::onNodeSelected, ScriptFunction.Usage.SCRIPT);

        // The graphs of the widgets' events and bindings live on the widgets, outside the session's data
        session.setExternalCanvases(new BlueprintEditorSession.ExternalCanvases() {
            @Override
            public @Nullable WorkHistory map(final UnaryOperator<ScriptGraphData> mapper) {
                Map<WidgetGraphs, Map<String, ScriptGraphData>> before = new HashMap<>();
                Map<WidgetGraphs, Map<String, ScriptGraphData>> after = new HashMap<>();

                widgets().forEach(widget -> {
                    Map<String, ScriptGraphData> graphs = widget.getGraphs().map();
                    Map<String, ScriptGraphData> old = new LinkedHashMap<>(graphs);

                    graphs.replaceAll((_, graph) -> mapper.apply(graph));

                    if (!graphs.equals(old)) {
                        before.put(widget.getGraphs(), old);
                        after.put(widget.getGraphs(), new LinkedHashMap<>(graphs));
                    }
                });

                if (before.isEmpty()) return null;

                return new WorkHistory() {
                    @Override
                    public void undo() {
                        before.forEach((graphs, map) -> {
                            graphs.map().clear();
                            graphs.map().putAll(map);
                        });
                    }

                    @Override
                    public void redo() {
                        after.forEach((graphs, map) -> {
                            graphs.map().clear();
                            graphs.map().putAll(map);
                        });
                    }
                };
            }

            @Override
            public Set<String> boundLocals() {
                return widgets()
                    .flatMap(widget -> {
                        Map<String, BlueprintSignature> signatures = widget.graphSignatures();

                        return widget.getGraphs().map().keySet().stream().map(signatures::get).filter(Objects::nonNull);
                    })
                    .flatMap(signature -> signature.parameters().stream().map(BlueprintSignature.Parameter::name))
                    .collect(Collectors.toSet());
            }
        });

        topMenuBar = new TopMenuBar(
            minecraft.font,
            menuBarTitle(),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.COMPILE, this::compile)
                // Parent picker: every container class, and the source code scripts built on the current one
                .on(TopMenuOption.CHANGE_PARENT, () -> {
                    ScriptFunction currentCtor = holder.getParent().ctor();

                    List<CategorizedSelectModal.Choice<BlueprintParentRef>> sourceCodeScripts = AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
                        .filter(entry -> WidgetContainerScript.class.isAssignableFrom(entry.getValue().type())
                            && Arrays.stream(entry.getValue().type().getConstructors()).anyMatch(constructor ->
                                constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isAssignableFrom(ownerType())))
                        .map(entry -> new CategorizedSelectModal.Choice<BlueprintParentRef>(
                            entry.getKey().identifier().toString(), new BlueprintParentRef.SourceCodeScript(currentCtor, entry.getValue())))
                        .toList();

                    List<CategorizedSelectModal.Choice<BlueprintParentRef>> containerClasses = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.stream()
                        .filter(function -> function.metadata().usage() == ScriptFunction.Usage.WIDGET_CTOR)
                        .map(ctor -> new CategorizedSelectModal.Choice<BlueprintParentRef>(ctor.metadata().label(), new BlueprintParentRef.ParentClass(ctor)))
                        .toList();

                    CategorizedSelectModal<BlueprintParentRef> modal = new CategorizedSelectModal<>(
                        minecraft.font,
                        PARENT_PICKER_W,
                        Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PARENT),
                        List.of(
                            new CategorizedSelectModal.Category<>(Component.translatable(
                                LangKeys.GUI_ENTITY_PATCH_PARENT_SELECT_MODAL_CATEGORY_SOURCE_CODE_SCRIPT), sourceCodeScripts),
                            new CategorizedSelectModal.Category<>(Component.translatable(
                                LangKeys.GUI_WIDGET_PARENT_SELECT_MODAL_CATEGORY_WIDGET_CLASS), containerClasses)
                        ),
                        this::changeParent
                    );

                    // The modal places itself by its units
                    modal.setX1(AnchoredWidget.pixel((width - PARENT_PICKER_W) / 2));
                    modal.setY1(AnchoredWidget.pixel((height - modal.getHeight()) / 2));

                    ModalManager.getInstance().openModalWidgetAtCenter(modal);
                })
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        AdjustablePanelManager.PanelArea centerHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(centerTabs);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                centerTabs.relocate(screenRectangle);
            }
        };

        // Widgets (top-left): the blueprint's widget tree, edited in place, the blueprint being its root host
        widgetPanel = new ComponentEditorPanel(
            minecraft.font, this, Component.translatable(LangKeys.GUI_WIDGET_SCREEN_PANEL_WIDGETS),
            WidgetComponentHolders.REGISTRY, holder, null, Map::of, true,
            // An open widget graph follows its widget's path, and closes with the widget
            () -> session.refreshExternalCanvases(key -> key instanceof WidgetGraphKey(WidgetComponentHolder<?> widget, String name)
                && pathIn(holder, "", widget) instanceof String path ? Component.literal(path + "." + name) : null),
            this::onWidgetFocused);
        widgetPanel.setDropTarget(this::onWidgetDropped);
        widgetPanel.setRenameListener(this::onWidgetRenamed);

        // Canvas: a click focuses the widget in the Widgets panel, which shows it in Details too
        canvas = new WidgetCanvas(minecraft.font, holder, widgetPanel);

        // Details follows the tab: the focused widget beside the canvas, a graph's own selection beside a graph
        centerTabs
            .setOnActiveChanged(_ -> widgetPanel.refocusDetails())
            .addTab(Component.translatable(LangKeys.GUI_WIDGET_SCREEN_TAB_CANVAS), canvas)
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), session.eventGraph());

        // Variables (bottom-left): a working draft list edited inline, under the parent's fields. A picked
        // variable shows its default value in Details
        variableController = new VariableEditController(minecraft.font, variableDrafts, session::renameVariable, session::retypeVariable);
        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        variablePanel.setDropTarget(this::onVariableDropped);
        variablePanel.setBlueprintFields(session.layout().fields(), session::dropField);

        // Details (right): the focused widget, the picked function's signature, or else the root
        detailsPanel = new ComponentDetailsPanel(minecraft.font, this, session::onSignaturesEdited);
        showRoot();
        session.setFunctionSelectionHandler(function -> {
            if (function != null) {
                detailsPanel.showComponent(function);
            } else {
                widgetPanel.refocusDetails();
            }
        });

        // With no variable picked, Details goes back to the focused widget
        Runnable showVariable = () -> {
            VariableDraft selected = variableController.getSelected();

            if (selected == null) {
                widgetPanel.refocusDetails();
                return;
            }

            detailsPanel.showRows(variableController, table -> variableController.addSharedRows(table, selected));
        };

        variableController.setViews(
            () -> {
                variablePanel.rebuildList();

                // A retype replaces the default value the rows are bound to
                if (detailsPanel.isShowing(variableController)) showVariable.run();
            },
            showVariable
        );

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: three columns [left | center | right]; the left column splits top to bottom
        // (Widgets / Functions / Variables). Divider order (== serialize order): col-left(0), col-right(1),
        // left-split(2, 3).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 22, 76, 40, 65);

        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        var leftSplit = columns.get(0).subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(2), r.get(3));

        leftSplit.get(0).area(widgetPanel);
        leftSplit.get(1).area(session.functionPanel());
        leftSplit.get(2).area(variablePanel);
        columns.get(1).area(centerHostArea);
        columns.get(2).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    // ***************************************************************
    // Parent (the container's constructor)
    // ***************************************************************

    /// The entry id followed by the parent's class, e.g. `epicfight:menu (AkytheraScreen)`.
    private Component menuBarTitle() {
        return Component.literal(resourceKey.identifier() + " (" + holder.getParent().displayLabel() + ")");
    }

    private Class<?> ownerType() {
        return holder.ownerType();
    }

    /// Re-parents the blueprint on `picked` as one undo step. A container class re-opens the screen, whose layout
    /// it may change, and a source code script re-locks it. A HUD parent is refused while widgets taking input
    /// remain, naming them.
    private void changeParent(final BlueprintParentRef picked) {
        if (picked.equals(holder.getParent())) return;

        if (WidgetBlueprintHolder.usageOf(picked.ctor()) == WidgetUsage.HUD) {
            List<String> inputWidgets = holder.inputWidgetPaths();

            if (!inputWidgets.isEmpty()) {
                RefusalPopup.open(
                    Component.translatable(LangKeys.GUI_WIDGET_SCREEN_POPUP_HUD_REFUSED_TITLE),
                    Component.translatable(LangKeys.GUI_WIDGET_SCREEN_POPUP_HUD_REFUSED_REASON, String.join(", ", inputWidgets)));

                if (detailsPanel.isShowing(holder)) showRoot();

                return;
            }
        }

        // Undo reaches the screen open at the time
        Consumer<BlueprintParentRef> writer = value -> {
            WidgetEditorScreen current =
                AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof WidgetEditorScreen open
                    && open.resourceKey.equals(resourceKey) ? open : null;

            if (current != null) current.flushToHolder();

            holder.setParent(value);

            if (current == null) return;

            if (value instanceof BlueprintParentRef.SourceCodeScript) {
                current.rebuildWidgets();
                return;
            }

            WidgetEditorScreen reopened = new WidgetEditorScreen(resourceKey, holder);

            reopened.savePoint = current.savePoint;
            current.onClose();
            AkytheraEditorLayoutManager.getInstance().openNewScreen(reopened);
        };

        HistoryManager.getInstance().pushHistory(new DataChange<>(holder.getParent(), picked, writer));

        writer.accept(picked);
    }

    /// Shows the root's rows in Details: the Widget Type, which re-parents the blueprint on the built-in
    /// container of the picked usage, then the root's own fields.
    private void showRoot() {
        detailsPanel.showRows(holder, table -> {
            EditorComboBox<WidgetUsage> usageCombo = new EditorComboBox<>(
                font,
                EditorWidgetFactory.columnStart(0, 1), EditorWidgetFactory.columnSize(0, 1),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                holder::usage,
                usage -> changeParent(new BlueprintParentRef.ParentClass(WidgetBlueprintHolder.defaultCtor(usage))),
                WidgetUsage.values().length,
                Component.empty(),
                List.of(WidgetUsage.values()),
                usage -> ParseUtils.toDisplayName(usage.name())
            );
            usageCombo.setClearable(false);

            table.newRow(EditorWidgetFactory.makeTranslatableLabel(font, LangKeys.GUI_WIDGET_SCREEN_ROW_WIDGET_TYPE)).addWidget(usageCombo);
            rootRows.appendRowsFrom(table, holder);
        });
    }

    // ***************************************************************
    // [ComponentPathHost]
    // ***************************************************************

    @Override
    public SequencedMap<String, Class<? extends PatchComponent>> componentPaths() {
        return ComponentPaths.known(holder);
    }

    // ***************************************************************
    // Panel hooks
    // ***************************************************************

    /// Selection handler for every canvas: a single `Get Component` node takes over Details, any other selection
    /// shows the rows [BlueprintEditorSession#detailsOf] gives it.
    private void onNodeSelected(final List<AbstractNodeWidget> selection) {
        if (selection.size() == 1 && selection.getFirst() instanceof ComponentGetterNodeWidget componentGetter) {
            detailsPanel.showComponent(componentGetter);
            return;
        }

        detailsPanel.showSlots(session.detailsOf(selection));
    }

    /// [ComponentEditorPanel] focus callback: the focused widget on the canvas and in Details, with the blueprint as
    /// its rows' context, or the root when none is focused. While a graph tab is open, Details shows the graph's
    /// selection instead.
    private void onWidgetFocused(final @Nullable ComponentDataHolder<?> widget, final @Nullable ComponentAttachableHolder host) {
        canvas.setSelected(widget);

        // A widget's rows stay off Details while a graph is open
        if (centerTabs.getActiveIndex() >= 0 && centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph) {
            graph.fireSelectionChanged();
            return;
        }

        if (widget == null) {
            showRoot();
        } else {
            detailsPanel.showComponent(widget, holder);
        }
    }

    /// Opens, or brings up, the canvas of `widget`'s event or binding `name` beside the Canvas and the Event Graph.
    public void openWidgetGraph(final WidgetComponentHolder<?> widget, final String name) {
        BlueprintSignature signature = widget.graphSignatures().get(name);
        String path = pathIn(holder, "", widget);

        if (signature == null || path == null) return;

        session.openExternalCanvas(
            new WidgetGraphKey(widget, name), Component.literal(path + "." + name), signature,
            widget.getGraphs().map().getOrDefault(name, ScriptGraphData.EMPTY),
            graph -> widget.getGraphs().map().put(name, graph)
        );
    }

    /// The widgets of the tree, at every depth.
    private Stream<WidgetComponentHolder<?>> widgets() {
        return ComponentAttachableHolder.flatten(holder)
            .filter(WidgetComponentHolder.class::isInstance)
            .map(widget -> (WidgetComponentHolder<?>) widget);
    }

    /// The path of `target` under `host`, or null when it is not there.
    ///
    /// @param prefix the path of `host` followed by [ComponentAttachable#PATH_SEPARATOR], or empty at the root
    private static @Nullable String pathIn(final ComponentAttachableHolder host, final String prefix, final ComponentDataHolder<?> target) {
        for (Map.Entry<String, ComponentDataHolder<?>> entry : host.getComponents().entrySet()) {
            String path = prefix + entry.getKey();

            if (entry.getValue() == target) return path;

            if (entry.getValue() instanceof ComponentAttachableHolder child) {
                String found = pathIn(child, path + ComponentAttachable.PATH_SEPARATOR, target);

                if (found != null) return found;
            }
        }

        return null;
    }

    /// [ComponentEditorPanel] rename callback: every node reading the renamed widget follows it, the canvases on
    /// screen through their widgets, the rest through the holder storing them.
    private void onWidgetRenamed(final String fromPath, final String toPath) {
        session.canvases().forEach(canvas -> canvas.renameComponentGetters(fromPath, toPath));
        session.mapClosedCanvases(canvas -> ComponentPaths.moved(canvas, fromPath, toPath));
        ComponentPaths.rename(holder, holder.getScript(), fromPath, toPath);
    }

    /// [ComponentEditorPanel] drop callback: a widget released over a canvas drops a [ComponentGetterNodeWidget]
    /// reading it, at the cursor.
    private boolean onWidgetDropped(final String path, final double screenX, final double screenY) {
        if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        graph.spawnNodeAt((font, x, y) -> new ComponentGetterNodeWidget(font, path, x, y), screenX, screenY);
        return true;
    }

    /// A variable released over the Event Graph opens a [VariableDropModal] at the cursor; picking a row spawns the
    /// matching Get / Set node there.
    private boolean onVariableDropped(final VariableDraft draft, final double screenX, final double screenY) {
        if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        String name = draft.getName();
        ScriptType type = draft.getType();

        VariableDropModal modal = new VariableDropModal(
            minecraft.font, name,
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
            () -> graph.spawnNodeAt(
                (font, x, y) -> new ScriptVariableGetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name), name, type, x, y),
                screenX, screenY),
            () -> graph.spawnNodeAt(
                (font, x, y) -> new ScriptVariableSetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                screenX, screenY));

        ModalManager.getInstance().openModalWidget(modal, (int) screenX, (int) screenY);
        return true;
    }

    // ***************************************************************
    // Widget registration / layout
    // ***************************************************************
    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        topMenuBar.setMessage(menuBarTitle());
        addRenderableWidget(topMenuBar);

        if (holder.getParent() instanceof BlueprintParentRef.SourceCodeScript sourceCode) {
            // Locked: the panels take no input and draw without the cursor, under a dimmer
            addRenderableOnly((graphics, _, _, a) -> panelManager.extractRenderState(graphics, -1, -1, a));
            addRenderableOnly((graphics, _, _, _) -> {
                graphics.fill(panelManager.getX(), panelManager.getY(), panelManager.getRight(), panelManager.getBottom(), LOCK_DIM_COLOR);
                graphics.centeredText(
                    font,
                    Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MESSAGE_LOCKED, sourceCode.displayLabel()),
                    panelManager.getX() + panelManager.getWidth() / 2,
                    panelManager.getY() + (panelManager.getHeight() - font.lineHeight) / 2,
                    0xFFCCCCCC
                );
            });
        } else {
            addRenderableWidget(panelManager);
        }
    }

    // ***************************************************************
    // Save / compile
    // ***************************************************************

    /// Flattens the working state onto the holder and writes it to disk asynchronously.
    private CompletableFuture<Void> save() {
        flushToHolder();

        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);

        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> {
                if (ModalManager.getInstance().getModal() == awaitModal) ModalManager.getInstance().closeModal();

                if (error != null) {
                    AkytheraLabs.LOGGER.error("Failed to save widget blueprint '{}'", resourceKey.identifier(), error);
                    return;
                }

                savePoint = HistoryManager.getInstance().currentSavePoint();
            }, minecraft);
    }

    /// Compiles the live canvases into the runtime blueprint in memory only. Marks any invalid node with a red
    /// ERROR banner, aborts on the first failure, and pops a toast with the outcome.
    private void compile() {
        flushToHolder();

        // A source code script lowers nothing from the canvases
        if (holder.getParent() instanceof BlueprintParentRef.SourceCodeScript) {
            CompileResultToast.show(true, null);
            return;
        }

        Component failedPage = session.validate();

        if (failedPage != null) {
            AkytheraLabs.LOGGER.warn("Compile of widget blueprint '{}' failed on page '{}'", resourceKey.identifier(), failedPage.getString());
            CompileResultToast.show(false, failedPage);
            return;
        }

        List<String> unknownPaths = ComponentPaths.unknown(holder, holder.getScript());

        if (!unknownPaths.isEmpty()) {
            AkytheraLabs.LOGGER.warn("Compile of widget blueprint '{}' aborted: no widget at {}", resourceKey.identifier(), unknownPaths);
            CompileResultToast.show(false, Component.literal("No widget at " + String.join(", ", unknownPaths)));
            return;
        }

        List<ScriptGraphData.FailedNode> failedNodes = holder.getScript().failedNodes();

        if (!failedNodes.isEmpty()) {
            for (ScriptGraphData.FailedNode f : failedNodes) {
                AkytheraLabs.LOGGER.warn("Compile of widget blueprint '{}' aborted: unresolved node (id {}): {}", resourceKey.identifier(), f.id(), f.message());
            }

            CompileResultToast.show(false, Component.literal("Unresolved nodes: " + failedNodes.size()));
            return;
        }

        try {
            WidgetBlueprint blueprint = holder.makeObject();
            AkytheraLabs.LOGGER.info("Compiled widget blueprint '{}': {}", resourceKey.identifier(), blueprint);
            CompileResultToast.show(true, null);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to compile widget blueprint '{}'", resourceKey.identifier(), e);
            CompileResultToast.show(false, Component.literal(e.getMessage() == null ? resourceKey.identifier().toString() : e.getMessage()));
        }
    }

    /// Writes the session's canvases and variables back onto the holder. Widgets are edited on the holder's own
    /// tree and need no flush.
    private void flushToHolder() {
        session.flush();
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
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, resourceKey.identifier().toString()),
            hasUnsavedChanges(),
            () -> {
                ModalManager.getInstance().closeModal();
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
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    /// Marks the blueprint's runtime failures: on the Event Graph or a function tab, and for a widget event or
    /// binding (`<path>.<name>`) on its open tab and its row in the Widgets panel.
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Map<String, ScriptFailure> failures = ScriptRecorder.failures(resourceKey);

        // Re-marked every frame while failures stand, so the marks follow edits that renumber the rows and reach
        // tabs opened after the failure
        if (!failures.isEmpty() || failuresMarked) {
            failuresMarked = !failures.isEmpty();

            Map<ComponentDataHolder<?>, Throwable> widgetErrors = new IdentityHashMap<>();

            session.canvases().forEach(ScriptNodeGraphWidget::clearRuntimeErrors);

            failures.forEach((site, failure) -> {
                int split = site.lastIndexOf('.');

                if (split < 0 || !(ComponentPaths.at(holder, site.substring(0, split)) instanceof WidgetComponentHolder<?> widget)) {
                    session.markRuntimeError(site, failure);
                    return;
                }

                ScriptNodeGraphWidget canvas = session.externalCanvas(new WidgetGraphKey(widget, site.substring(split + 1)));

                widgetErrors.putIfAbsent(widget, failure.cause());

                if (canvas != null) canvas.markRuntimeError(failure);
            });

            widgetPanel.setRuntimeErrors(widgetErrors);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
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
        var modal = getFocusOwningModal();
        if (modal != null) return modal;
        return super.getFocused();
    }

    /// The tab of a widget's event or binding graph.
    private record WidgetGraphKey(WidgetComponentHolder<?> widget, String name) {
    }
}
