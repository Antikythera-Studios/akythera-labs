package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.module.ParticleModuleContext;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.particle.ModuleParamHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleModuleScriptHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleModulesProducer;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
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
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.particle.EmitterTrackPanel;
import com.yesman.akytheralabs.gui.widget.script.ContextEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ParticleParameterGetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeSuggestions;
import com.yesman.akytheralabs.gui.widget.script.TransitionResultNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.node.ParticleParameterGetterNodeData;
import com.yesman.akytheralabs.gui.widget.viewer.EmitterMarkerComponent;
import com.yesman.akytheralabs.gui.widget.viewer.EmitterPivotComponent;
import com.yesman.akytheralabs.gui.widget.viewer.EmitterTransformComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ParticleSystemPreviewComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/// Edits one particle system: the preview over the emitter track on the left, and the selected emitter's (or
/// variable's) Details over the Parameters on the right.
///
/// The preview is the first tab of a tab panel. The other tabs are node graphs opened from an emitter's module rows:
/// the canvas of a built-in module's parameter ([#openParameterGraph]), and the Event Graph of a module the open
/// project holds ([#openModuleGraph]).
///
/// A saved parameter dragged from the Parameters panel onto a node graph drops a node that reads it. The preview
/// plays with the parameters as last saved, and a save builds its system again. Removing a parameter that a parameter
/// canvas reads asks first.
public final class ParticleSystemScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    /// The volume the camera frames, in blocks.
    private static final float FRAMED_WIDTH = 2.0F;
    private static final float FRAMED_HEIGHT = 3.0F;

    /// Where a new parameter canvas seeds its entry and its result, in graph space.
    private static final int PARAMETER_ENTRY_X = -180;
    private static final int PARAMETER_RESULT_X = 120;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final TabPanel centerTabs;
    private final ComponentDetailsPanel detailsPanel;
    private final EmitterTrackPanel trackPanel;
    private final ParticleSystemPreviewComponent preview;
    private final ParticleSystemHolder holder;

    private final ResourceKey<ParticleSystemBlueprint> resourceKey;

    /// Each emitter's markers in the preview: its emitter transform first, then its pivot.
    private final Map<ParticleEmitterHolder, List<EmitterMarkerComponent>> emitterMarkers = new IdentityHashMap<>();

    /// The open parameter canvases, by parameter.
    private final Map<ModuleParamHolder, ScriptNodeGraphWidget> parameterGraphs = new IdentityHashMap<>();

    /// Write each open parameter canvas back into its parameter.
    private final List<Runnable> parameterFlushes = new ArrayList<>();

    /// The open project modules, by entry.
    private final Map<ResourceKey<?>, BlueprintEditorSession> moduleSessions = new LinkedHashMap<>();

    /// Working copies of the system's variables, written to the holder on save.
    private final List<VariableDraft> variableDrafts = new ArrayList<>();

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public ParticleSystemScreen(final ResourceKey<ParticleSystemBlueprint> resourceKey, final ParticleSystemHolder holder) {
        super(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_TITLE));

        this.resourceKey = resourceKey;
        this.holder = holder;

        for (BlueprintScriptData.Variable variable : holder.getVariables()) {
            variableDrafts.add(new VariableDraft(variable));
        }

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
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.FRAME_CAMERA, this::frameCamera)
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        model3DViewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        ParticleSystemPreviewComponent preview = new ParticleSystemPreviewComponent(holder);

        this.preview = preview;

        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(1.0F, 16, 68, 68, 68, 255));
        model3DViewer.addComponent(preview);

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this).viewer(model3DViewer);

        // The getter nodes of the parameter canvases follow a rename and a retype
        VariableEditController variableController = new VariableEditController(
            minecraft.font, variableDrafts,
            (from, to) -> rewriteParameterGetters(from, getter -> new ParticleParameterGetterNodeData(to, getter.parameterType())),
            (name, type) -> rewriteParameterGetters(name, getter -> new ParticleParameterGetterNodeData(name, Optional.of(type)))
        );
        VariablePanel variablePanel = new VariablePanel(minecraft.font, this, variableController);

        // The places that read the variable: the parameter canvases with a getter node of it
        variableController.warnOnRemoving(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_VARIABLE_IN_USE_TITLE), draft -> {
            List.copyOf(parameterFlushes).forEach(Runnable::run);

            List<Component> lines = new ArrayList<>();

            for (ParticleEmitterHolder emitter : holder.getEmitters()) {
                emitter.modules().forEach(module -> module.getParams().stream()
                    .filter(param -> param.readsParameter(draft.getName()))
                    .forEach(param -> lines.add(Component.literal(emitter.getName() + " / " + module.getRef().displayLabel() + " / " + param.displayName()))));
            }

            if (!lines.isEmpty()) lines.addFirst(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_VARIABLE_IN_USE_BODY, draft.getName()));

            return lines;
        });
        // A picked emitter replaces a picked variable in Details, and focuses its emitter transform marker unless one of
        // its markers holds the focus. With neither picked, Details shows the system's rows
        EmitterTrackPanel trackPanel = new EmitterTrackPanel(holder, preview, emitter -> {
            if (emitter != null) {
                variableController.select(null);

                if (!(model3DViewer.getFocused() instanceof EmitterMarkerComponent marker && marker.getEmitter() == emitter)) {
                    model3DViewer.focus(emitterMarkers.get(emitter).getFirst());
                }
            } else {
                if (model3DViewer.getFocused() instanceof EmitterMarkerComponent) model3DViewer.focus(null);
                if (!detailsPanel.isShowing(variableController)) detailsPanel.showComponent(holder);
            }
        }, panel -> {
            // Markers follow the emitters, picking an emitter in the track when clicked
            emitterMarkers.entrySet().removeIf(entry -> {
                boolean removed = !holder.getEmitters().contains(entry.getKey());

                if (removed) {
                    for (EmitterMarkerComponent marker : entry.getValue()) {
                        if (model3DViewer.getFocused() == marker) model3DViewer.focus(null);

                        model3DViewer.removeComponent(marker);
                    }
                }

                return removed;
            });

            for (ParticleEmitterHolder emitter : holder.getEmitters()) {
                emitterMarkers.computeIfAbsent(emitter, added -> {
                    Consumer<ParticleEmitterHolder> onPicked = picked -> {
                        if (panel.getSelected() != picked) panel.setSelected(picked);
                    };
                    // Only the selected emitter's markers show
                    BooleanSupplier shown = () -> panel.getSelected() == added;
                    List<EmitterMarkerComponent> markers = List.of(
                        new EmitterTransformComponent(added, onPicked, panel::refreshSelectionView, shown),
                        new EmitterPivotComponent(added, onPicked, panel::refreshSelectionView, shown)
                    );

                    markers.forEach(model3DViewer::addComponent);

                    return markers;
                });
            }
        });

        this.trackPanel = trackPanel;
        detailsPanel.showComponent(holder);

        centerTabs = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        centerTabs.addTab(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_TAB_PREVIEW), model3DViewer);

        // A variable released over a node graph drops the node that reads it there, once the variable is saved
        variablePanel.setDropTarget((draft, screenX, screenY) -> {
            if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph) || !graph.isPointInside(screenX, screenY)) return false;

            String name = draft.getName();
            ScriptType type = draft.getType();

            if (holder.getVariables().stream().noneMatch(saved -> saved.name().equals(name) && saved.type().equals(type))) {
                RefusalPopup.open(
                    Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_PARAMETER_UNSAVED_TITLE),
                    Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_PARAMETER_UNSAVED_BODY, name)
                );

                return true;
            }

            graph.spawnNodeAt((font, x, y) -> new ParticleParameterGetterNodeWidget(font, name, type, x, y), screenX, screenY);

            return true;
        });

        // A TabPanel is not a PanelArea, so it rides in a host that relays the resize to it
        AdjustablePanelManager.PanelArea centerHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(centerTabs);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                centerTabs.relocate(screenRectangle);
            }
        };

        Runnable showVariable = () -> {
            VariableDraft selected = variableController.getSelected();

            if (selected == null) {
                detailsPanel.showComponent(trackPanel.getSelected() != null ? trackPanel.getSelected() : holder);
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

        // Divider order: columns (0), left split (1), right split (2)
        List<Integer> ratios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 70, 60, 60);
        List<AdjustablePanelManager.PanelSection.SetupProxy> columns = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, ratios.get(0));
        List<AdjustablePanelManager.PanelSection.SetupProxy> leftSplit = columns.get(0)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, ratios.get(1));
        List<AdjustablePanelManager.PanelSection.SetupProxy> rightSplit = columns.get(1)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, ratios.get(2));

        leftSplit.get(0).area(centerHostArea);
        leftSplit.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        leftSplit.get(1).area(trackPanel);
        rightSplit.get(0).area(detailsPanel);
        rightSplit.get(1).area(variablePanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);

        frameCamera();
    }

    private void frameCamera() {
        model3DViewer.frameCamera(FRAMED_WIDTH, FRAMED_HEIGHT);
    }

    // ***************************************************************
    // Node graph tabs
    // ***************************************************************

    /// Opens, or shows again, the canvas of a built-in module's parameter: a data flow graph from the particle and the
    /// system's parameters into the parameter's value. The canvas is written back when its tab closes and on save.
    ///
    /// @param onChanged runs when the write-back gives the parameter a graph it did not have, or takes it away
    public void openParameterGraph(final ModuleParamHolder param, final Component title, final Runnable onChanged) {
        ScriptNodeGraphWidget existing = parameterGraphs.get(param);

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

        graph.setSuggestionProvider(new ScriptNodeSuggestions(() -> AkytheraParticle.class, List.of())::build);
        graph.setScriptOwnerType(AkytheraParticle.class);
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        graph.setDataFlowOnly(true);
        loadParameterGraph(param, graph);

        Runnable flush = () -> {
            boolean hadGraph = param.hasGraph();

            param.setGraph(graph.exportGraph());

            if (hadGraph != param.hasGraph()) onChanged.run();
        };

        parameterGraphs.put(param, graph);
        parameterFlushes.add(flush);
        centerTabs.addCloseableTab(title, graph, () -> {
            flush.run();
            parameterGraphs.remove(param);
            parameterFlushes.remove(flush);
        });
        centerTabs.setActiveIndex(centerTabs.indexOfContent(graph));
    }

    /// Fills `graph` with `param`'s canvas, or with a new one: an entry whose one pin is the particle, and the result.
    private void loadParameterGraph(final ModuleParamHolder param, final ScriptNodeGraphWidget graph) {
        List<ContextEntryNodeWidget.ContextParam> locals = List.of(new ContextEntryNodeWidget.ContextParam(ParticleModuleContext.PARTICLE_LOCAL, AkytheraParticle.class));
        Component title = Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_NODE_LOCALS);

        if (param.getGraph().isEmpty()) {
            graph.addNode(new ContextEntryNodeWidget(minecraft.font, title, locals, PARAMETER_ENTRY_X, 0));
            graph.addNode(new TransitionResultNodeWidget(minecraft.font, Component.literal(param.displayName()), PARAMETER_RESULT_X, 0));
        } else {
            graph.importGraph(param.getGraph(), (x, y, _) -> new ContextEntryNodeWidget(minecraft.font, title, locals, x, y));
        }

        graph.setResultType(param.declared().value());
    }

    /// Replaces every node that reads the parameter `name`, in each parameter canvas, with what `rewrite` makes of
    /// it.
    ///
    /// @return the edit that takes the replacement back and applies it again, or null when no canvas reads `name`
    private @Nullable WorkHistory rewriteParameterGetters(final String name, final UnaryOperator<ParticleParameterGetterNodeData> rewrite) {
        List.copyOf(parameterFlushes).forEach(Runnable::run);

        Map<ModuleParamHolder, ScriptGraphData> before = new IdentityHashMap<>();
        Map<ModuleParamHolder, ScriptGraphData> after = new IdentityHashMap<>();
        BiConsumer<ModuleParamHolder, ScriptGraphData> write = (param, graph) -> {
            ScriptNodeGraphWidget opened = parameterGraphs.get(param);

            param.setGraph(graph);

            if (opened != null) loadParameterGraph(param, opened);
        };

        holder.getEmitters().stream().flatMap(ParticleEmitterHolder::modules).flatMap(module -> module.getParams().stream()).forEach(param -> {
            ScriptGraphData rewritten = param.getGraph().mapNodes(
                node -> node instanceof ParticleParameterGetterNodeData getter && getter.parameter().equals(name) ? rewrite.apply(getter) : node
            );

            if (rewritten != param.getGraph()) {
                before.put(param, param.getGraph());
                after.put(param, rewritten);
            }
        });

        if (after.isEmpty()) return null;

        after.forEach(write);

        return new WorkHistory() {
            @Override
            public void undo() {
                before.forEach(write);
            }

            @Override
            public void redo() {
                after.forEach(write);
            }
        };
    }

    /// Opens, or shows again, the Event Graph of the open project's module `id`: an entry of the particle updater data
    /// pack registry, or of the initializer one. Nothing opens for a module the project does not hold.
    public void openModuleGraph(final boolean updater, final Identifier id) {
        ResourceKey<Object> key = ResourceKey.create(ParticleModulesProducer.moduleRegistry(updater), id);
        BlueprintEditorSession existing = moduleSessions.get(key);

        if (existing != null) {
            centerTabs.setActiveIndex(centerTabs.indexOfContent(existing.eventGraph()));
            return;
        }

        ParticleModuleScriptHolder<Object> module = AkytheraProject.getCurrentProject().<Object, ParticleModuleScriptHolder<Object>>getDataHolder(key).orElse(null);

        if (module == null) return;

        List<VariableDraft> moduleVariables = new ArrayList<>();

        module.getScript().getVariables().forEach(variable -> moduleVariables.add(new VariableDraft(variable)));

        // With no node picked, Details goes back to what the track has selected
        BlueprintEditorSession session = new BlueprintEditorSession(
            minecraft.font, this, module.layout(), AkytheraParticleEmitter.class, module.getScript(), centerTabs, moduleVariables,
            new ScriptNodeSuggestions(() -> AkytheraParticleEmitter.class, moduleVariables)::build,
            selection -> {
                BlueprintEditorSession opened = moduleSessions.get(key);

                if (selection.isEmpty() || opened == null) {
                    trackPanel.refreshSelectionView();
                } else {
                    detailsPanel.showSlots(opened.detailsOf(selection));
                }
            },
            ScriptFunction.Usage.SCRIPT
        );

        moduleSessions.put(key, session);
        centerTabs.addCloseableTab(Component.literal(id.toString()), session.eventGraph(), () -> {
            session.flush();
            moduleSessions.remove(key);
        });
        centerTabs.setActiveIndex(centerTabs.indexOfContent(session.eventGraph()));
    }

    // ***************************************************************
    // Save
    // ***************************************************************

    /// Writes the open canvases and the variable drafts to their holders, builds the preview's system again, then
    /// writes the system and every open module to disk. Flags the emitters that have no particle.
    private CompletableFuture<Void> save() {
        flushNodeGraphs();
        moduleSessions.keySet().forEach(AkytheraProject.getCurrentProject()::saveEntryAsync);
        holder.setVariables(variableDrafts.stream().map(VariableDraft::toData).toList());
        preview.rebuild();
        trackPanel.flagEmittersWithoutParticle();

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

    /// Writes every open node graph tab back into its holder: the parameter canvases and the module Event Graphs.
    public void flushNodeGraphs() {
        List.copyOf(parameterFlushes).forEach(Runnable::run);
        moduleSessions.values().forEach(BlueprintEditorSession::flush);
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
        if (event.isEscape() && trackPanel.cancelKeyGrab()) return true;

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

        if (EditorKeyMappings.INSERT_KEYFRAME.matches(event)) {
            int scale = AkytheraLabsConfig.getEditorGuiScale();
            int mouseX = (int)Math.round(minecraft.mouseHandler.xpos() / scale);
            int mouseY = (int)Math.round(minecraft.mouseHandler.ypos() / scale);

            boolean overPreview = centerTabs.getActiveContent() == model3DViewer && model3DViewer.isMouseOver(mouseX, mouseY);

            if ((trackPanel.isMouseOver(mouseX, mouseY) || overPreview) && trackPanel.openInsertMenu(mouseX, mouseY)) return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        AbstractWidget modal = getHoveringModal(x, y);
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is released when it
    /// closes.
    @Override
    public @Nullable GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
