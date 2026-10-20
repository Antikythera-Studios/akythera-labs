package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.ability.AbilityBlueprintHolder;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.editor.NodeClipboard;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
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
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.VariableDropModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.CompileFailure;
import com.yesman.akytheralabs.gui.widget.script.NodeWidgetFactory;
import com.yesman.akytheralabs.gui.widget.script.PlayAbilityCueNodeWidget;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/// Editor screen for an [AbilityBlueprintHolder]: Functions over Variables on the left, the Event Graph and
/// the function tabs in the center, and the Details panel on the right, all laid out by the [Ability] class
/// the parent constructor builds. The Details panel shows that class's properties, the signature of the
/// declared function picked in the Functions panel, or the default value and sync direction of the picked
/// variable.
///
/// `File > Change Parent` picks another ability class and re-opens the screen, whose layout the class may
/// change. A parent that binds its own state holds no variables: adding one is refused under it, and it
/// cannot be picked while the ability has any.
public final class AbilityEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    /// Width of the parent picker, in screen pixels.
    private static final int PARENT_PICKER_W = 240;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final TabPanel graphTabs;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;
    private final BlueprintEditorSession session;

    private final ResourceKey<AbilityBlueprint> resourceKey;
    private final AbilityBlueprintHolder holder;

    /// Working copies of the blueprint's variables; flushed to the holder by [#session] on save.
    private final List<VariableDraft> variableDrafts = new ArrayList<>();

    /// History stack top at the last save; the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public AbilityEditorScreen(final ResourceKey<AbilityBlueprint> resourceKey, final AbilityBlueprintHolder holder) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;
        this.holder = holder;

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

        ScriptNodeSuggestions nodeSuggestions = new ScriptNodeSuggestions(holder::ownerType, variableDrafts);

        session = new BlueprintEditorSession(
            minecraft.font, this, holder.layout(), holder.ownerType(), holder.getScript(), graphTabs, variableDrafts,
            (graph, context) -> {
                List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = nodeSuggestions.build(graph, context);

                if (context.flow() && context.providedType() == null && !context.dataFlowOnly()) {
                    AkytheraProject.getCurrentProject()
                        .getMergedHolders(AkytheraRegistries.DataPack.ABILITY_CUE_KEY).stream()
                        .map(EditorHolderCodecs::entryId)
                        .sorted()
                        .forEach(cueId -> suggestions.add(new NodeSuggestModal.Suggestion<>(
                            Component.literal(cueId.toString()),
                            (font, x, y) -> new PlayAbilityCueNodeWidget(
                                font, EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, cueId), x, y),
                            "Ability/Cue"
                        )));

                    AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
                        .filter(entry -> AbilityCueScript.runnable(entry.getValue().type()))
                        .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                        .forEach(entry -> suggestions.add(new NodeSuggestModal.Suggestion<>(
                            Component.literal(entry.getKey().identifier().toString()),
                            (font, x, y) -> new PlayAbilityCueNodeWidget(font, entry.getValue(), x, y),
                            "Ability/Cue"
                        )));
                }

                return suggestions;
            },
            this::showDetails,
            ScriptFunction.Usage.SCRIPT, ScriptFunction.Usage.ABILITY_TASK
        );

        graphTabs.addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), session.eventGraph());

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier() + " (" + holder.ownerType().getSimpleName() + ")"),
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
                    session.canvases().forEach(ScriptNodeGraphWidget::clearValidationErrors);
                    Compilation.compileInEditor(holder, compilation -> compilation.fail(session.validateMembers()));
                })
                // Parent picker: every ability constructor. A pick re-opens the screen, and undo reaches
                // the screen open at the time.
                .on(TopMenuOption.CHANGE_PARENT, () -> {
                    List<CategorizedSelectModal.Choice<ScriptFunction>> ctors = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.stream()
                        .filter(function -> function.metadata().usage() == ScriptFunction.Usage.ABILITY_CTOR)
                        .map(ctor -> new CategorizedSelectModal.Choice<>(ctor.metadata().label(), ctor))
                        .toList();

                    CategorizedSelectModal<ScriptFunction> modal = new CategorizedSelectModal<>(
                        minecraft.font,
                        PARENT_PICKER_W,
                        Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PARENT),
                        List.of(new CategorizedSelectModal.Category<>(
                            Component.translatable(LangKeys.GUI_ABILITY_SCREEN_PARENT_CATEGORY), ctors)),
                        picked -> {
                            if (picked == holder.getCtor()) return;

                            if (!variableDrafts.isEmpty() && AbilityBlueprintHolder.stateSlot(picked) == null) {
                                RefusalPopup.open(
                                    Component.translatable(LangKeys.GUI_ABILITY_SCREEN_POPUP_VARIABLES_REFUSED_TITLE),
                                    Component.translatable(
                                        LangKeys.GUI_ABILITY_SCREEN_REFUSAL_PARENT_STATE,
                                        picked.functionSignature().returnType().getSimpleName())
                                );

                                return;
                            }

                            Consumer<ScriptFunction> writer = value -> {
                                AbilityEditorScreen current =
                                    AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof AbilityEditorScreen open
                                        && open.resourceKey.equals(resourceKey) ? open : null;

                                if (current != null) current.session.flush();

                                holder.setCtor(value);

                                if (current == null) return;

                                AbilityEditorScreen reopened = new AbilityEditorScreen(resourceKey, holder);

                                reopened.savePoint = current.savePoint;
                                current.onClose();
                                AkytheraEditorLayoutManager.getInstance().openNewScreen(reopened);
                            };

                            HistoryManager.getInstance().pushHistory(new DataChange<>(holder.getCtor(), picked, writer));

                            writer.accept(picked);
                        }
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

        VariableEditController variableController = new VariableEditController(
            minecraft.font, variableDrafts, session::renameVariable, session::retypeVariable);

        if (AbilityBlueprintHolder.stateSlot(holder.getCtor()) == null) {
            variableController.refuseAdding(
                Component.translatable(LangKeys.GUI_ABILITY_SCREEN_POPUP_VARIABLES_REFUSED_TITLE),
                Component.translatable(LangKeys.GUI_ABILITY_SCREEN_REFUSAL_ADD_VARIABLE, holder.ownerType().getSimpleName())
            );
        }

        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        variablePanel.setBlueprintFields(session.layout().fields(), session::dropField);
        variablePanel.markSyncTargets();
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

        // A picked variable shows its name, type, default value and, when its type can cross the wire, its
        // sync direction. With none picked, Details goes back to the properties
        Runnable showVariable = () -> {
            VariableDraft selected = variableController.getSelected();

            if (selected == null) {
                showDetails(List.of());
                return;
            }

            detailsPanel.showRows(variableController, table -> {
                variableController.addSharedRows(table, selected);
                if (selected.getType().isSyncable()) variableController.addSyncRow(table, selected);
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
    /// selection shows the parent's properties.
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

    /// Re-shows the marks of a failed compile every frame, so they reach tabs opened after it.
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CompileFailure failure = holder.getCompileFailure();

        if (failure != null) session.showCompileFailure(failure);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
