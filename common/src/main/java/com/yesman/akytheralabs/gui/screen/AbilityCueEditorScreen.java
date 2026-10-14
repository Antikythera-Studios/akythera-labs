package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.ability.AbilityCueBlueprintHolder;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.editor.NodeClipboard;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.panel.script.VariableEditController;
import com.yesman.akytheralabs.gui.panel.script.VariablePanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.CompileResultToast;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.VariableDropModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// Editor screen for an [AbilityCueBlueprintHolder]: Functions over Variables on the left, the Event Graph
/// and the function tabs in the center, and the Details panel on the right, all laid out by
/// [AbilityCueScript] with [AbilityCue] as the graphs' Self.
/// The Details panel shows the cue's properties, the signature of the declared function picked in the
/// Functions panel, or the default value of the picked variable.
public final class AbilityCueEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final TabPanel graphTabs;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;
    private final BlueprintEditorSession session;

    private final ResourceKey<AbilityCueBlueprint> resourceKey;

    /// Working copies of the blueprint's variables; flushed to the holder by [#session] on save.
    private final List<VariableDraft> variableDrafts = new ArrayList<>();

    /// History stack top at the last save; the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public AbilityCueEditorScreen(final ResourceKey<AbilityCueBlueprint> resourceKey, final AbilityCueBlueprintHolder holder) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;

        graphTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        for (BlueprintScriptData.Variable variable : holder.getScript().getVariables()) {
            variableDrafts.add(new VariableDraft(variable));
        }

        ScriptNodeSuggestions nodeSuggestions = new ScriptNodeSuggestions(() -> AbilityCue.class, variableDrafts);

        session = new BlueprintEditorSession(
            minecraft.font, this, holder.layout(), AbilityCue.class, holder.getScript(), graphTabs, variableDrafts,
            nodeSuggestions::build,
            this::showDetails,
            ScriptFunction.Usage.SCRIPT
        );

        graphTabs.addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), session.eventGraph());

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.COMPILE, () -> {
                    session.flush();

                    Component failedPage = session.validate();

                    if (failedPage != null) {
                        AkytheraLabs.LOGGER.warn("Compile of ability cue '{}' failed on page '{}'", resourceKey.identifier(), failedPage.getString());
                        CompileResultToast.show(false, failedPage);
                        return;
                    }

                    List<ScriptGraphData.FailedNode> failedNodes = holder.getScript().failedNodes();

                    if (!failedNodes.isEmpty()) {
                        CompileResultToast.show(false, Component.literal("Unresolved nodes: " + failedNodes.size()));
                        return;
                    }

                    try {
                        AkytheraLabs.LOGGER.info("Compiled ability cue '{}': {}", resourceKey.identifier(), holder.makeObject());
                        CompileResultToast.show(true, null);
                    } catch (RuntimeException e) {
                        AkytheraLabs.LOGGER.error("Failed to compile ability cue '{}'", resourceKey.identifier(), e);
                        CompileResultToast.show(false, Component.literal(resourceKey.identifier().toString()));
                    }
                })
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        VariableEditController variableController = new VariableEditController(
            minecraft.font, variableDrafts, session::renameVariable, session::retypeVariable);

        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        variablePanel.setBlueprintFields(session.layout().fields(), session::dropField);
        // A variable released over a canvas opens a VariableDropModal at the cursor; picking a row
        // spawns the matching Get / Set node there
        variablePanel.setDropTarget((draft, screenX, screenY) -> {
            if (!(graphTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
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
        });

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this, session::onSignaturesEdited);
        showDetails(List.of());

        // A picked variable shows its name, type and default value. With none picked, Details goes back to the
        // properties
        Runnable showVariable = () -> {
            VariableDraft selected = variableController.getSelected();

            if (selected == null) {
                showDetails(List.of());
                return;
            }

            detailsPanel.showRows(variableController, table -> {
                variableController.addSharedRows(table, selected);
            });
        };

        variableController.setViews(
            () -> {
                variablePanel.rebuildList();

                // A retype replaces the default value the rows are bound to
                if (detailsPanel.isShowing(variableController)) showVariable.run();
            },
            showVariable
        );
        session.setFunctionSelectionHandler(function -> {
            if (function != null) {
                detailsPanel.showComponent(function);
            } else {
                showDetails(List.of());
            }
        });

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

        // Three columns [left | center | right]; the left column splits top/bottom (Functions /
        // Variables). Divider order (== serialize order): col-left(0), col-right(1), left-split(2).
        List<Integer> ratios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 20, 76, 45);
        List<AdjustablePanelManager.PanelSection.SetupProxy> columns = panelManager.getRoot().subdivide(
            2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, ratios.get(0), ratios.get(1)
        );
        List<AdjustablePanelManager.PanelSection.SetupProxy> leftSplit = columns.get(0).subdivide(
            1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, ratios.get(2)
        );

        leftSplit.get(0).area(session.functionPanel());
        leftSplit.get(1).area(variablePanel);
        columns.get(1).area(graphHostArea);
        columns.get(2).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Shows the Details rows of a canvas selection, see [BlueprintEditorSession#detailsOf]. An empty
    /// selection shows the cue's properties.
    private void showDetails(List<AbstractNodeWidget> selection) {
        detailsPanel.showSlots(session.detailsOf(selection));
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

    /// Flattens the session into the holder, then writes the entry to disk.
    private CompletableFuture<Void> save() {
        session.flush();

        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);

        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> {
                if (ModalManager.getInstance().getModal() == awaitModal) {
                    ModalManager.getInstance().closeModal();
                }

                if (error != null) {
                    AkytheraLabs.LOGGER.error("Failed to save entry '{}'", resourceKey.identifier(), error);

                    return;
                }

                savePoint = HistoryManager.getInstance().currentSavePoint();
            }, minecraft);
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

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (NodeClipboard.getInstance().keyPressed(event, this, graphTabs.getActiveContent())) return true;

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
        var modal = ModalManager.getInstance().getInputModal();

        return modal != null ? modal : super.getFocused();
    }
}
