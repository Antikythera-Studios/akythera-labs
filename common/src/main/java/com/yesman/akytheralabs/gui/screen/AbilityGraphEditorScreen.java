package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.panel.script.VariableEditController;
import com.yesman.akytheralabs.gui.panel.script.VariablePanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.AbilityEntryPointNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/// Editor screen for an ability-system entry whose behavior is one or more node graphs.
///
/// One screen serves the ability, which has an entry point per event, and the cue, which has one.
/// Each [Canvas] is a tab; the screen owns the canvases and writes them back through the sinks the
/// caller supplied.
public final class AbilityGraphEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ENTRY_X = 40;
    private static final int ENTRY_Y = 40;

    /// One tab: the graph it starts from, the objects its entry node offers, and where its edits go.
    ///
    /// @param sink takes the flattened canvas on every save
    public record Canvas(
        Component title,
        ScriptGraphData initial,
        AbilityEntryPointNodeWidget.EntryKind entryKind,
        Consumer<ScriptGraphData> sink
    ) {}

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final TabPanel graphTabs;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;

    private final ResourceKey<?> resourceKey;
    private final List<Canvas> canvases;
    private final Map<Canvas, ScriptNodeGraphWidget> widgets = new LinkedHashMap<>();
    private final List<VariableDraft> variableDrafts = new ArrayList<>();
    private final Consumer<List<ScriptVariables.VariableInfo>> variableSink;

    /// History stack top at the last save; the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public AbilityGraphEditorScreen(
        final ResourceKey<?> resourceKey,
        final Object detailsTarget,
        final List<Canvas> canvases,
        final List<ScriptVariables.VariableInfo> variables,
        final Consumer<List<ScriptVariables.VariableInfo>> variableSink
    ) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;
        this.canvases = List.copyOf(canvases);
        this.variableSink = variableSink;

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
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        graphTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        for (Canvas canvas : this.canvases) {
            ScriptNodeGraphWidget widget = buildCanvas(canvas);

            widgets.put(canvas, widget);
            graphTabs.addTab(canvas.title(), widget);
        }

        for (ScriptVariables.VariableInfo info : variables) {
            variableDrafts.add(VariableDraft.from(info));
        }

        VariableEditController variableController = new VariableEditController(
            minecraft.font, variableDrafts, (from, to) -> {}, (name, type) -> null
        );

        variablePanel = new VariablePanel(minecraft.font, this, variableController);

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this);
        detailsPanel.showComponent(detailsTarget);

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        // A TabPanel is not a PanelArea, so it rides in a host that relays the resize to it.
        AdjustablePanelManager.PanelArea graphHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(graphTabs);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                graphTabs.relocate(screenRectangle);
            }
        };

        // Three columns: variables, the graph tabs, and the details of the entry itself.
        List<Integer> ratios =
            AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 20, 76);
        List<AdjustablePanelManager.PanelSection.SetupProxy> columns = panelManager.getRoot().subdivide(
            2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, ratios.get(0), ratios.get(1)
        );

        columns.get(0).area(variablePanel);
        columns.get(1).area(graphHostArea);
        columns.get(2).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// A canvas seeded with its entry node, or rebuilt from what was saved.
    private ScriptNodeGraphWidget buildCanvas(final Canvas canvas) {
        ScriptNodeGraphWidget widget = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        widget.setUsage(ScriptFunction.Usage.SCRIPT, ScriptFunction.Usage.ABILITY_TASK);

        ScriptNodeGraphWidget.EntryNodeFactory entryFactory =
            (x, y, outLabels) -> new AbilityEntryPointNodeWidget(
                minecraft.font,
                canvas.title(),
                AbilityEntryPointNodeWidget.EntryKind.ofLabels(outLabels),
                x, y
            );

        if (canvas.initial().isEmpty()) {
            widget.addNode(new AbilityEntryPointNodeWidget(
                minecraft.font, canvas.title(), canvas.entryKind(), ENTRY_X, ENTRY_Y
            ));
        } else {
            widget.importGraph(canvas.initial(), entryFactory);
        }

        return widget;
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

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
    }

    // ***************************************************************
    // Save
    // ***************************************************************

    /// Flattens every canvas back into its holder, then writes the entry to disk.
    private CompletableFuture<Void> save() {
        flush();

        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);

        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> {
                if (AkytheraEditorLayoutManager.getInstance().getModal() == awaitModal) {
                    AkytheraEditorLayoutManager.getInstance().closeModal();
                }

                if (error != null) {
                    AkytheraLabs.LOGGER.error("Failed to save entry '{}'", resourceKey.identifier(), error);

                    return;
                }

                savePoint = HistoryManager.getInstance().currentSavePoint();
            }, minecraft);
    }

    private void flush() {
        widgets.forEach((canvas, widget) -> canvas.sink().accept(widget.exportGraph()));
        variableSink.accept(variableDrafts.stream().map(VariableDraft::toInfo).toList());
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

        return modal != null ? Optional.of(modal) : super.getChildAt(x, y);
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

        return modal != null ? modal : super.getFocused();
    }
}
