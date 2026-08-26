package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationStateTransition;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.editor.debug.AnimatorProbe;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import com.yesman.akytheralabs.editor.debug.ScriptExecutionRecords;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import com.yesman.akythera.client.gui.widget.viewer.*;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.akythera.animation.AnimGraphCompiler;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.AssetBrowserPanel;
import com.yesman.akytheralabs.gui.panel.script.ScriptNodeDetailsPanel;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.panel.script.VariableEditController;
import com.yesman.akytheralabs.gui.panel.script.VariablePanel;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.RegistryLookupModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.*;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.gui.widget.script.node.AnimInterfaceNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.AnimLayerNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.CachedPoseGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.CachedPoseNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.StateMachineNodeData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Editor screen for an [AnimatorScriptHolder] (a [DataDrivenAnimatorScript.AnimatorScriptBlueprint]).
///
/// Modeled on Unreal's Animation Blueprint editor: a three-column layout under the
/// top menu bar — preview + variables on the left, the blueprint graph tabs in the
/// center, node details + asset browser on the right.
///
/// Design stage only: every panel except the [Model3DViewer] is a text placeholder, and no value editing is
/// wired up. The preview model is chosen *before* the screen opens (see
/// [ContentWidget.AnimatorScriptContent]) and stored on the holder, so the previewer shows it here.
public final class AnimatorScriptEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen, DebuggableScreen {
    private static final int TOP_BAR_H = 22;

    /// Seed layout for the Event Graph's two event nodes: the Constructor sits at `ENTRY_Y`, the
    /// Update one `ENTRY_GAP` below it (Constructor above Update, UE-style).
    private static final int ENTRY_X = 60;
    private static final int ENTRY_Y = 60;
    private static final int ENTRY_GAP = 140;

    /// Seed position of the Anim Graph's [OutputPoseNodeWidget] — set well to the right so dragged-in
    /// pose sources have room to its left (poses flow left → right into Result, UE-style).
    private static final int OUTPUT_POSE_X = 360;
    private static final int OUTPUT_POSE_Y = 100;

    /// Graph-space shift applied to a paste so the new nodes sit one grid step down-right of the
    /// originals instead of exactly on top of them (UE-style).
    private static final int PASTE_OFFSET = 24;

    /// Bounds of the transition Blend Time row, in seconds. `0` is a hard cut; the step is one server
    /// tick.
    private static final float BLEND_TIME_MIN = 0.0F;
    private static final float BLEND_TIME_MAX = 10.0F;
    private static final float BLEND_TIME_STEP = 0.05F;

    /// Bounds of the transition Transition Priority row. The engine ranks edges against each other, so
    /// the row imposes no range of its own.
    private static final int TRANSITION_PRIORITY_MIN = Integer.MIN_VALUE;
    private static final int TRANSITION_PRIORITY_MAX = Integer.MAX_VALUE;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final TabPanel blueprintEditor;

    /// The "Event Graph" node canvas, hosting the Update and Constructor event nodes. Saved to the
    /// holder's update graph.
    private final ScriptNodeGraphWidget updateGraph;

    /// The live "Anim Graph" pose canvas (UE's AnimGraph) — a data-flow graph terminating at an
    /// [OutputPoseNodeWidget]. Saved to the holder's pose graph. Pose sources are dragged in from
    /// the Asset Browser (sequences / blendspaces).
    private final ScriptNodeGraphWidget poseGraph;

    /// Asset Browser → Anim Graph drag payload: the asset id and whether it is a blendspace (vs. a
    /// sequence). Null when no asset drag is in flight. Mirrors [BlendSpace2DEditorScreen]'s pattern.
    @Nullable
    private AssetDrag draggingAsset;

    private record AssetDrag(Identifier id, boolean blendSpace) {}

    /// Every Anim Graph state machine's topology, keyed by machine name. The mutable source of truth
    /// the editor edits; flushed to the holder on [#save] / [#compile]. Open editors write their
    /// topology back here on tab close.
    private final Map<String, StateMachineData> stateMachines;

    /// State machines with a currently-open sub-tab, by name → its live canvas. Lets a re-activation
    /// focus the existing tab instead of opening a duplicate.
    private final Map<String, StateMachineGraphWidget> openStateMachines = new HashMap<>();

    /// Open state-pose / transition-condition sub-tabs, by a unique key → their canvas (for re-focus
    /// instead of duplicate tabs).
    private final Map<String, ScriptNodeGraphWidget> openSubGraphs = new HashMap<>();

    /// Every open node editor (state-machine tab + sub-graph tabs), each with a `flush` that writes
    /// its current data all the way back to [#stateMachines]. Run on save / compile and on tab close.
    /// Each flush is self-contained (it re-exports the owning machine in full), so running them in any
    /// order yields a consistent result.
    private final List<OpenEditor> openEditors = new ArrayList<>();

    private record OpenEditor(AbstractWidget content, Runnable onFlush) {}

    /// Sub-graphs (state pose / transition condition) carry no ENTRY rows — their root rebuilds
    /// directly — so their import never consults an entry factory.
    private final ScriptNodeGraphWidget.EntryNodeFactory subGraphEntryFactory = (_, _, _) -> null;

    /// Whether [#compile] has run since this screen opened. Until it has, a freshly-opened sub-tab is
    /// left unmarked — nothing has been compiled, so there is no verdict to put on its nodes.
    private boolean compiled;

    /// The open canvases that are transition conditions — the only ones a [SourceDriverNodeWidget]
    /// resolves on, since only a transition check binds the driver it reads. Identity-keyed: a canvas
    /// is the same canvas only if it is the same widget.
    private final Set<ScriptNodeGraphWidget> conditionCanvases =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /// Mutable working copies of the blueprint's variables; flushed back to the
    /// holder only on [#save].
    private final List<VariableDraft> variableDrafts;
    private final VariableEditController variableController;
    private final VariablePanel variablePanel;
    private final ScriptNodeDetailsPanel scriptNodeDetailsPanel;

    /// The named node (State Machine / Cached Pose) currently shown in the Details panel (its name
    /// editable there), or null when the Details panel shows a variable / nothing. Tracked to dedup
    /// repeated selection events.
    @Nullable
    private AbstractNodeWidget detailsNamedNode;

    /// Set when a captured run named rows the Event Graph does not have — the pack and the canvas were
    /// built from different graphs, so nothing is highlighted until the next reload.
    private boolean debugSnapshotStale;

    private final AnimatorScriptHolder holder;
    private final ResourceKey<DataDrivenAnimatorScript.@NonNull AnimatorScriptBlueprint> resourceKey;

    /// History stack top at the last save (or screen open). The title shows a `*` while the live top
    /// differs from this; a completed save re-snapshots it. See [HistoryManager#currentSavePoint].
    private @Nullable WorkHistory savePoint;

    public AnimatorScriptEditorScreen(
        ResourceKey<DataDrivenAnimatorScript.@NonNull AnimatorScriptBlueprint> resourceKey,
        AnimatorScriptHolder holder
    ) {
        super(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TITLE));

        this.resourceKey = resourceKey;
        this.holder = holder;

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            _ -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.COMPILE, this::compile)
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.DEBUG, () -> DebuggableScreen.openDebugSelector(font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        // Shows the model picked before this screen opened.
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

        // The two graph tabs, Event Graph and Anim Graph.
        blueprintEditor = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // "Event Graph" tab: a node canvas seeded with the Update and Constructor event nodes.
        updateGraph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        updateGraph.setSuggestionProvider(this::buildNodeSuggestions);

        // The animator script's owner type, so a right-click on empty canvas offers the
        // actions available on it (mirrors UE's context-sensitive blueprint menu). Set before the
        // graph is populated so loaded nodes inherit it.
        updateGraph.setScriptOwnerType(Animator.class);

        // The Event Graph is the locomotion-logic graph, so it offers only SCRIPT functions; the
        // Anim Graph (pose) tab offers ANIM_GRAPH ones (UE's per-graph node palette).
        updateGraph.setUsage(ScriptFunction.Usage.SCRIPT);

        // Rebuild the saved canvas, or seed a fresh Event Graph with the Update + Constructor event
        // nodes. Each entry's title / pin shape is event-specific, so the graph asks back for it via
        // this factory — which recovers the event kind from the row's persisted output labels (a row
        // carrying data-output labels is the Update event; otherwise the Constructor).
        ScriptNodeGraphWidget.EntryNodeFactory entryFactory =
            (x, y, outLabels) ->
                outLabels.stream().anyMatch(label -> !label.isBlank())
                    ? new EntryPointNodeWidget(
                        minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_UPDATE),
                        EntryPointNodeWidget.EntryKind.UPDATE, x, y
                    )
                    : new EntryPointNodeWidget(
                        minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_CONSTRUCT),
                        EntryPointNodeWidget.EntryKind.CONSTRUCTOR, x, y
                    );

        ScriptGraphData savedUpdateGraph = holder.getUpdateGraph();

        if (savedUpdateGraph.isEmpty()) {
            // Update is added first so it keeps node id 0 — the compiler falls back to the first
            // entry as the update flow — while sitting below the Constructor on the canvas.
            updateGraph.addNode(
                new EntryPointNodeWidget(
                    minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_UPDATE),
                    EntryPointNodeWidget.EntryKind.UPDATE, ENTRY_X, ENTRY_Y + ENTRY_GAP
                )
            );
            updateGraph.addNode(
                new EntryPointNodeWidget(
                    minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_CONSTRUCT),
                    EntryPointNodeWidget.EntryKind.CONSTRUCTOR, ENTRY_X, ENTRY_Y
                )
            );
        } else {
            updateGraph.importGraph(savedUpdateGraph, entryFactory);
            backfillMissingEventNodes();
        }

        // "Anim Graph" tab: a live data-flow pose canvas terminating at the Output Pose node. Pose
        // sources (sequence / blendspace players) are dragged in from the Asset Browser.
        poseGraph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        poseGraph.setSuggestionProvider(this::buildNodeSuggestions);
        poseGraph.setScriptOwnerType(Animator.class);
        // Anim Graph offers pose nodes plus script pure functions (float math etc. for BlendSpace inputs).
        poseGraph.setUsage(ScriptFunction.Usage.ANIM_GRAPH, ScriptFunction.Usage.SCRIPT);
        poseGraph.setDataFlowOnly(true);
        // Double-clicking a State Machine node opens its closeable sub-tab.
        poseGraph.setNodeActivationHandler(this::onNodeActivated);
        // Selecting a State Machine node shows its (editable) name in the Details panel.
        poseGraph.setSelectionHandler(this::onNodeSelected);

        stateMachines = new HashMap<>(holder.getStateMachines());

        ScriptGraphData savedPoseGraph = holder.getPoseGraph();
        if (savedPoseGraph.isEmpty()) {
            poseGraph.addNode(new OutputPoseNodeWidget(
                minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_OUTPUT_POSE),
                OUTPUT_POSE_X, OUTPUT_POSE_Y));
        } else {
            // Pose graphs carry no ENTRY rows (the Output Pose root rebuilds directly), so the entry
            // factory is never consulted here — passing the Event Graph's is harmless.
            poseGraph.importGraph(savedPoseGraph, entryFactory);
        }

        blueprintEditor
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), updateGraph)
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_ANIM_GRAPH), poseGraph);

        AdjustablePanelManager.PanelArea blueprintHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(blueprintEditor);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                blueprintEditor.relocate(screenRectangle);
            }
        };

        // Variables (list) + Details (selected variable editors), sharing one controller
        // and one mutable draft list. The blueprint's flows are untouched here.
        variableDrafts = new ArrayList<> ();

        for (ScriptVariables.VariableInfo info : holder.getVariables()) {
            variableDrafts.add(VariableDraft.from(info));
        }

        variableController = new VariableEditController(minecraft.font, variableDrafts, this::onVariableRenamed, this::onVariableRetyped);
        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        // Drag a variable out of the list and onto the canvas to spawn its Get / Set node there.
        variablePanel.setDropTarget(this::onVariableDropped);
        scriptNodeDetailsPanel = new ScriptNodeDetailsPanel(minecraft.font, this, variableController);
        variableController.setViews(
            () -> {
                variablePanel.rebuildList();
                detailsNamedNode = null;
                scriptNodeDetailsPanel.showVariable(variableController.getSelected());
            },
            () -> {
                detailsNamedNode = null;
                scriptNodeDetailsPanel.showVariable(variableController.getSelected());
            }
        );

        // Asset Browser (bottom-right): draggable Sequences / Blend Spaces. Dragging one onto the
        // Anim Graph spawns the matching pose-player node bound to that asset (see [#dropAssetAsNode]).
        AssetBrowserPanel<Holder<AnimationSequence>> sequenceBrowser = new AssetBrowserPanel<>(
            minecraft.font,
            //AnimatorScriptEditorScreen::buildSequenceItems,
            () -> AkytheraProject.getCurrentProject()
                .getMergedHolders(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).stream()
                .sorted(Comparator.comparing(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")))
                .toList(),
            h -> h.unwrapKey().map(ResourceKey::identifier).orElse(Identifier.parse("unknown")).toString(),
            h -> draggingAsset = new AssetDrag(h.unwrapKey().orElseThrow().identifier(), false),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        AssetBrowserPanel<Holder<BlendSpace2D>> blendSpaceBrowser = new AssetBrowserPanel<>(
            minecraft.font,
            //AnimatorScriptEditorScreen::buildBlendSpaceItems,
            () -> AkytheraProject.getCurrentProject()
                .getMergedHolders(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY).stream()
                .sorted(Comparator.comparing(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")))
                .toList(),
            h -> h.unwrapKey().map(ResourceKey::identifier).orElse(Identifier.parse("unknown")).toString(),
            h -> draggingAsset = new AssetDrag(h.unwrapKey().orElseThrow().identifier(), true),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        TabPanel assetBrowserPanel = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        assetBrowserPanel
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_PANEL_ASSET_BROWSER_SEQUENCES), sequenceBrowser)
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_PANEL_ASSET_BROWSER_BLEND_SPACES), blendSpaceBrowser);

        AdjustablePanelManager.PanelArea assetBrowserHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(assetBrowserPanel);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                assetBrowserPanel.relocate(screenRectangle);
            }
        };

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: three columns [left | center | right]; left & right each split top/bottom
        // (left = preview / variables, right = node details / asset browser).
        // Divider order (== serialize order): col-left(0), col-right(1), left-split(2), right-split(3).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 24, 74, 48, 48);

        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        var leftSplit = columns.get(0).subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(2));
        var rightSplit = columns.get(2).subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(3));

        leftSplit.get(0).resizeCallback(model3DViewer::relocate);
        leftSplit.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        leftSplit.get(1).area(variablePanel);
        columns.get(1).area(blueprintHostArea);
        rightSplit.get(0).area(scriptNodeDetailsPanel);
        rightSplit.get(1).area(assetBrowserHostArea);

        // Unsaved-changes marker: the title shows a `*` whenever the live history top diverges from the
        // last save point. Snapshot the point after all seeding above so a freshly opened script is clean.
        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Ensures the Event Graph always shows both event nodes. Saves made before the Constructor event
    /// existed hold only the Update entry, so this adds the Constructor (above the Update one) when it
    /// is missing. Appended after import so the Update entry keeps node id 0 — the compiler's
    /// update-flow root. One-shot: once both exist and are saved, nothing is added.
    private void backfillMissingEventNodes() {
        EntryPointNodeWidget update = null;
        boolean hasConstructor = false;

        for (EntryPointNodeWidget entry : updateGraph.entryNodes()) {
            switch (entry.entryKind()) {
                case UPDATE -> update = entry;
                case CONSTRUCTOR -> hasConstructor = true;
            }
        }

        // Defensive: a save with no Update entry at all (should not happen) — restore one first so
        // there is always an update-flow root.
        if (update == null) {
            update = new EntryPointNodeWidget(
                minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_UPDATE),
                EntryPointNodeWidget.EntryKind.UPDATE, ENTRY_X, ENTRY_Y + ENTRY_GAP
            );

            updateGraph.addNode(update);
        }

        if (!hasConstructor) {
            int constructorY = update.getY() - ENTRY_GAP;
            // Keep the backfilled Constructor on-screen: if it would sit above the canvas top, drop
            // the pair down (Constructor above, Update below).
            if (constructorY < ENTRY_Y) {
                constructorY = ENTRY_Y;
                update.setY(constructorY + ENTRY_GAP);
            }

            updateGraph.addNode(
                new EntryPointNodeWidget(
                    minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_CONSTRUCT),
                    EntryPointNodeWidget.EntryKind.CONSTRUCTOR, update.getX(), constructorY
                )
            );
        }
    }

    /// The literal types offered when a value drag carries no concrete expected type
    /// (e.g. an `Object` slot) — the everyday primitives plus string.
    private static final List<Class<?>> DEFAULT_LITERAL_TYPES =
        List.of(boolean.class, int.class, float.class, double.class, String.class);

    /// Routes a gesture to the suggestion set matching its [NodeSuggestContext]: an execution
    /// drag gets flow nodes, a data-input drag gets value providers, a parameter-output drag
    /// gets pure functions taking that value, and an empty-canvas right-click gets both flow
    /// nodes and the owner's pure functions. Categories are additive (a right-click shows
    /// several at once), and the context's `functionFilter` narrows the [ScriptFunction]
    /// entries. Re-evaluated on each open so newly added variables appear.
    private List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> buildNodeSuggestions(
        ScriptNodeGraphWidget graph, NodeSuggestContext context
    ) {
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = new ArrayList<>();

        if (context.flow()) addFlowSuggestions(suggestions, context.providedType(), context.functionFilter(), context.usage(), context.dataFlowOnly());
        if (context.valueProviders()) addValueProviderSuggestions(suggestions, context.expectedType(), context.usage());
        if (context.pureFunctions()) addPureFunctionSuggestions(suggestions, context.expectedType(), context.providedType(), context.functionFilter(), context.usage());

        // The empty-canvas "All Actions" menu (flow + pure) also offers the member-variable
        // getters, filed under "Member Variables", so Get<var> is reachable there as in UE.
        // A value-consumer (parameter) drag — flow + pure but with a providedType — wants
        // consumers of the dragged value, not value-producing getters, so it is excluded.
        // Member variables are a script concept: the pose (ANIM_GRAPH) graphs don't offer them.
        if (isScriptDomain(context) && context.flow() && context.pureFunctions() && context.providedType() == null) {
            addMemberVariableGetters(suggestions, null);
        }

        addAnimGraphSuggestions(graph, suggestions, context);
        addSourceDriverSuggestion(graph, suggestions, context);
        addTypeConvertSuggestions(suggestions, context);

        return suggestions;
    }

    /// The Source State Driver node, offered on a transition-condition canvas only — nothing binds
    /// its value anywhere else. Gated like the member-variable getters: on a data-input drag whose
    /// slot a driver fits, and on the empty-canvas "All Actions" menu, but not on a value-output drag
    /// (which wants consumers of the dragged value, and this node consumes nothing).
    private void addSourceDriverSuggestion(
        ScriptNodeGraphWidget graph,
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        NodeSuggestContext context
    ) {
        if (!conditionCanvases.contains(graph)) return;

        boolean valueDrag = context.valueProviders();
        boolean allActions = context.flow() && context.pureFunctions() && context.providedType() == null;
        if (!valueDrag && !allActions) return;
        if (!AbstractNodeWidget.valueAssignable(AnimationDriver.class, context.expectedType())) return;

        suggestions.add(new NodeSuggestModal.Suggestion<>(
            Component.literal(SourceDriverNodeWidget.TITLE),
            (font, x, y) -> new SourceDriverNodeWidget(font, x, y)
        ));
    }

    /// Offers explicit numeric type-conversion ([TypeConvertNodeWidget]) entries for a pin dropped on empty
    /// canvas: dragging a value **output** lists "Convert to X" for each type the dragged type can narrow to
    /// ([ScriptType#explicitCastableTypes]); dragging a value **input** lists "Convert Y to <expected>" for
    /// each type Y that narrows to the expected type. Picking one spawns a converter the host then wires to
    /// the dropped pin. Widening casts are implicit and not offered (a direct wire already works).
    private void addTypeConvertSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        NodeSuggestContext context
    ) {
        Class<?> provided = context.providedType();

        if (provided != null) {
            ScriptType from = scriptTypeOf(provided);
            if (from == null) return;

            for (Class<?> target : from.explicitCastableTypes().keySet()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal("Convert to " + TypeConvertNodeWidget.typeName(target)),
                    (font, x, y) -> new TypeConvertNodeWidget(font, provided, target, x, y)));
            }

            return;
        }

        Class<?> expected = context.expectedType();
        if (expected == null) return;

        Class<?> expectedBoxed = ParseUtils.box(expected);

        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (!st.explicitCastableTypes().containsKey(expectedBoxed)) continue;
            Class<?> source = st.type();
            suggestions.add(
                new NodeSuggestModal.Suggestion<>(
                    Component.literal(
                        "Convert " + TypeConvertNodeWidget.typeName(source) + " to "
                            + TypeConvertNodeWidget.typeName(expected)
                    ),
                    (font, x, y) -> new TypeConvertNodeWidget(font, source, expected, x, y)
                )
            );
        }
    }

    /// The registered [ScriptType] whose java type matches `type` (boxing-aware), or null when no
    /// script type backs it.
    private static @Nullable ScriptType scriptTypeOf(Class<?> type) {
        Class<?> boxed = ParseUtils.box(type);

        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (ParseUtils.box(st.type()) == boxed) return st;
        }

        return null;
    }

    /// The submenu the pose mixers are filed under.
    private static final String BLEND_CATEGORY = "Blend";

    /// The pose-specific nodes offered in any ANIM_GRAPH canvas (the root Anim Graph or a state's pose
    /// sub-graph): the pose **sources** — a State Machine, and a "Use cached pose" getter per cached
    /// pose defined on the **root** Anim Graph — plus the pose **mixers** (Blend / Apply Additive /
    /// Layered Blend per Bone), offered wherever a Pose value fits. The pose **sink**
    /// (a "New Cached Pose" cacher) is offered **only on the root Anim Graph**: a cached pose is defined
    /// once at the root and merely referenced (via getters) from state pose graphs. Player nodes are
    /// not here — they come via Asset-Browser drop.
    private void addAnimGraphSuggestions(
        ScriptNodeGraphWidget graph,
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        NodeSuggestContext context
    ) {
        if (!context.usage().contains(ScriptFunction.Usage.ANIM_GRAPH)) return;

        if (context.pureFunctions() && AbstractNodeWidget.valueAssignable(Pose.class, context.expectedType())) {
            String machineName = uniqueMachineName();
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_STATE_MACHINE),
                (font, x, y) -> new StateMachineNodeWidget(font, machineName, x, y)
            ));

            // The pose mixers. Every pose canvas offers them: each one takes the poses it mixes as
            // inputs, so it needs nothing the hosting graph owns.
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("Blend"),
                (font, x, y) -> new TwoPoseBlendNodeWidget(font, x, y),
                BLEND_CATEGORY
            ));
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("Apply Additive"),
                (font, x, y) -> new AdditiveBlendNodeWidget(font, x, y),
                BLEND_CATEGORY
            ));
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("Layered Blend per Bone"),
                (font, x, y) -> new LayeredBlendPerBoneNodeWidget(font, x, y),
                BLEND_CATEGORY
            ));

            // Anim Layer nodes register one engine AnimationLayer per AnimationLayerSignature. Every
            // signature is offered whether or not one is already placed — a duplicate is reported by
            // the compile ([#collectDuplicateProperties]), not blocked here. Layers are stored in
            // `construct`, which only sees the root graph, so they are offered on the root Anim Graph
            // only.
            if (graph == poseGraph) {
                // Merge engine values with editor-created (labs) signatures (split registry).
                for (Identifier id : AkytheraProject.getCurrentProject()
                        .getMergedDataBlockIdentifiers(AnimationLayerSignature.ENUM_MANAGER)) {
                    String name = id.toString();
                    suggestions.add(new NodeSuggestModal.Suggestion<>(
                        Component.literal(name),
                        (font, x, y) -> new AnimLayerNodeWidget(
                            font,
                            AkytheraProject.getCurrentProject()
                                .getDataBlockHolderOrDangling(AnimationLayerSignature.ENUM_MANAGER, id),
                            x, y),
                        "Anim Layer"
                    ));
                }
            }

            // Cached poses live on the root Anim Graph; their getters are usable from there and from
            // any state's pose graph, so the list always comes from the root poseGraph.
            for (String cached : poseGraph.cachedPoseNames()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal("Use cached pose '" + cached + "'"),
                    (font, x, y) -> new CachedPoseGetterNodeWidget(
                        font, cached, CachedPoseGetterNodeData.DEFAULT_CONTROL_REFERENCE_DRIVER, x, y)
                ));
            }

            // Animation Interface nodes — replaceable pose slots keyed by an AnimationInterfaceKey,
            // offered per resource type (the engine's two interface kinds). A key registers once per
            // animator (BlendingAnimator#addAnimInterface throws on a duplicate), and every key is
            // offered regardless: a duplicate is reported by the compile
            // ([#collectDuplicateProperties]), not blocked here.
            for (Identifier id : AkytheraProject.getCurrentProject()
                    .getMergedDataBlockIdentifiers(AnimationInterfaceKey.ENUM_MANAGER)) {
                String name = id.toString();

                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(name),
                    (font, x, y) -> new AnimSequenceInterfaceNodeWidget(
                        font,
                        AkytheraProject.getCurrentProject()
                            .getDataBlockHolderOrDangling(AnimationInterfaceKey.ENUM_MANAGER, id),
                        null, x, y),
                    "Anim Sequence Interface"
                ));
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(name),
                    (font, x, y) -> new BlendSpaceInterfaceNodeWidget(
                        font,
                        AkytheraProject.getCurrentProject()
                            .getDataBlockHolderOrDangling(AnimationInterfaceKey.ENUM_MANAGER, id),
                        null, x, y),
                    "Blend Space 2D Interface"
                ));
            }
        }

        // The cache **definition** (sink) may only be created on the root Anim Graph.
        if (graph == poseGraph && context.flow() && (context.providedType() == null
            || AbstractNodeWidget.valueAssignable(Pose.class, context.providedType()))) {
            String cacheName = uniqueCachedPoseName();
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("New Cached Pose"),
                (font, x, y) -> new PoseCachingNodeWidget(font, cacheName, x, y)
            ));
        }
    }

    /// A cached-pose name not already used on the root Anim Graph (where every cache is defined).
    private String uniqueCachedPoseName() {
        java.util.Set<String> used = new java.util.HashSet<>(poseGraph.cachedPoseNames());
        for (int i = 1; ; i++) {
            String candidate = "Cached Pose " + i;
            if (!used.contains(candidate)) return candidate;
        }
    }

    /// Whether `context`'s canvas accepts SCRIPT nodes — the Event Graph and transition conditions, and
    /// now the Anim Graph / pose graphs too (which also accept SCRIPT for pure functions / member
    /// getters driving e.g. BlendSpace inputs). Gates the empty-canvas "All Actions" member getters.
    private static boolean isScriptDomain(NodeSuggestContext context) {
        return context.usage().contains(ScriptFunction.Usage.SCRIPT);
    }

    /// A state-machine name not already used by a [StateMachineData] entry or a live State Machine
    /// node, so each machine keys distinctly into [#stateMachines].
    private String uniqueMachineName() {
        Set<String> used = usedMachineNames();
        for (int i = 1; ; i++) {
            String candidate = "State Machine " + i;
            if (!used.contains(candidate)) return candidate;
        }
    }

    /// Every state-machine name this script has taken: the [#stateMachines] keys plus the name every
    /// State Machine node carries — on the root Anim Graph, on any open sub-graph tab, and in every
    /// stored state's driver. A machine keys into [#stateMachines] uniquely, so a name in here is not
    /// available.
    private Set<String> usedMachineNames() {
        Set<String> used = new HashSet<>(stateMachines.keySet());
        used.addAll(poseGraph.stateMachineNames());

        for (ScriptNodeGraphWidget open : openSubGraphs.values()) {
            used.addAll(open.stateMachineNames());
        }

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData state : machine.states()) {
                machineNamesIn(state.poseGraph()).forEach(used::add);
            }
        }

        return used;
    }

    /// The machine name every State Machine row of `graph` carries, blanks excluded.
    private static Stream<String> machineNamesIn(ScriptGraphData graph) {
        return graph.nodes().stream()
            .filter(row -> row.data() instanceof StateMachineNodeData)
            .map(row -> ((StateMachineNodeData) row.data()).machineName())
            .filter(name -> !name.isEmpty());
    }

    /// Double-click handler for the Anim Graph: opens a State Machine node's sub-tab.
    private void onNodeActivated(AbstractNodeWidget node) {
        if (node instanceof StateMachineNodeWidget sm) openStateMachineTab(sm);
    }

    /// Selection handler for any node canvas: when a single State Machine node is selected, the Details
    /// panel shows its **editable** name; any other selection reverts the panel to the selected variable.
    private void onNodeSelected(@Nullable AbstractNodeWidget node) {
        // Pressing a Sequence Player node previews its bound animation on the model previewer.
        if (node instanceof AnimSequencePlayerNodeWidget sequencePlayer) {
            previewSequence(sequencePlayer.sequenceId());
        }

        // A sequence-typed Animation Interface previews its default animation the same way.
        if (node instanceof AnimSequenceInterfaceNodeWidget seqInterface
            && seqInterface.defaultAnimationId() != null) {
            previewSequence(seqInterface.defaultAnimationId());
        }

        if (node instanceof StateMachineNodeWidget sm) {
            if (detailsNamedNode == sm) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = sm;
            scriptNodeDetailsPanel.showNameRow(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME,
                sm::machineName, newName -> renameStateMachine(sm, newName));
        } else if (node instanceof PoseCachingNodeWidget cp) {
            if (detailsNamedNode == cp) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = cp;
            scriptNodeDetailsPanel.showNameRow(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME,
                cp::cacheName, newName -> renameCachedPose(cp, newName));
        } else if (node instanceof AnimInterfaceNodeWidget animInterface) {
            if (detailsNamedNode == animInterface) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = animInterface;
            showAnimInterfaceDetails(animInterface);
        } else if (node instanceof AnimSequencePlayerNodeWidget sequencePlayer) {
            if (detailsNamedNode == sequencePlayer) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = sequencePlayer;
            showSequencePlayerDetails(sequencePlayer);
        } else if (node instanceof BlendSpacePlayerNodeWidget blendSpacePlayer) {
            if (detailsNamedNode == blendSpacePlayer) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = blendSpacePlayer;
            showBlendSpacePlayerDetails(blendSpacePlayer);
        } else if (node instanceof CachedPoseGetterNodeWidget cachedPoseGetter) {
            if (detailsNamedNode == cachedPoseGetter) return;
            variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
            detailsNamedNode = cachedPoseGetter;
            showCachedPoseGetterDetails(cachedPoseGetter);
        } else {
            if (detailsNamedNode == null) return;
            detailsNamedNode = null;
            scriptNodeDetailsPanel.showVariable(variableController.getSelected());
        }
    }

    /// Shows the Details panel for a Sequence Player node: the one sequence it plays, rebindable
    /// through the picker. Picking previews the new clip, mirroring what selecting the node does.
    private void showSequencePlayerDetails(AnimSequencePlayerNodeWidget node) {
        scriptNodeDetailsPanel.showIdentifierRows(List.of(assetRow(
            LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_SEQUENCE,
            AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
            node::sequenceId,
            picked -> {
                node.setSequenceId(picked);
                previewSequence(picked);
            })));
    }

    /// Shows the Details panel for a Use Cached Pose node: whether the reference drives the cached
    /// pose's own driver, compiled into
    /// [com.yesman.akythera.core.animation.driver.CachedPoseReference#controlReferenceDriver].
    private void showCachedPoseGetterDetails(CachedPoseGetterNodeWidget node) {
        scriptNodeDetailsPanel.showValueRows(List.of(new ScriptNodeDetailsPanel.BooleanRow(
            LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_CONTROL_REFERENCE_DRIVER,
            node::controlReferenceDriver,
            node::setControlReferenceDriver)));
    }

    /// Shows the Details panel for a Blendspace Player node: the one blendspace it plays, rebindable
    /// through the picker (which also re-derives the node's axis pin labels).
    private void showBlendSpacePlayerDetails(BlendSpacePlayerNodeWidget node) {
        scriptNodeDetailsPanel.showIdentifierRows(List.of(assetRow(
            LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_BLEND_SPACE,
            AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,
            node::blendSpaceId,
            node::setBlendSpaceId)));
    }

    /// Shows the Details panel for an Animation Interface node: **both** its key — re-keyable over the
    /// interface keys still free on this script — and its default animation, over the node's resource
    /// type (anim sequences for a sequence interface, blendspaces for a Blend Space 2D one). Picking a
    /// default on a sequence interface also previews it, like the Sequence Player.
    private void showAnimInterfaceDetails(AnimInterfaceNodeWidget node) {
        String label = LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_DEFAULT_ANIMATION;

        // One branch per resource type: the row's registry decides the entry type, so the two cases
        // can't share a call (their `ResourceKey`s have no common entry type to infer).
        ScriptNodeDetailsPanel.IdentifierRow animation = node instanceof AnimSequenceInterfaceNodeWidget
            ? assetRow(label, AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, node::defaultAnimationId,
                picked -> {
                    node.setDefaultAnimationId(picked);
                    if (picked != null) previewSequence(picked);
                })
            : assetRow(label, AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, node::defaultAnimationId,
                node::setDefaultAnimationId);

        scriptNodeDetailsPanel.showIdentifierRows(List.of(interfaceKeyRow(node), animation));
    }

    /// A Details row for an asset-valued node property: the node's current id, marked red while it
    /// resolves in neither the project nor the level, and a magnifier opening the entry lookup over
    /// `registryKey` (project ∪ level, the same set the Asset Browser drag offers). A pick runs
    /// `apply` and is recorded as one undoable step (see [#commitNodeIdentifier]).
    private <T> ScriptNodeDetailsPanel.IdentifierRow assetRow(
        String labelKey,
        ResourceKey<? extends Registry<T>> registryKey,
        Supplier<@Nullable Identifier> current,
        Consumer<@Nullable Identifier> apply
    ) {
        return new ScriptNodeDetailsPanel.IdentifierRow(
            labelKey,
            current,
            id -> AkytheraProject.getCurrentProject().hasEntry(registryKey, id),
            box -> openPickerUnder(box, RegistryLookupModal.dataPackEntries(
                font, Component.translatable(labelKey), registryKey, false,
                key -> commitNodeIdentifier(current, apply, key.identifier(), box)))
        );
    }

    /// The Animation Interface key row: the lookup offers every merged [AnimationInterfaceKey] block.
    /// A key registers once per animator, but re-keying to one another node already uses is reported by
    /// the compile ([#collectDuplicateProperties]) rather than kept off the list.
    private ScriptNodeDetailsPanel.IdentifierRow interfaceKeyRow(AnimInterfaceNodeWidget node) {
        Supplier<@Nullable Identifier> current = () -> Identifier.tryParse(node.interfaceKeyName());

        return new ScriptNodeDetailsPanel.IdentifierRow(
            LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_INTERFACE_KEY,
            current,
            id -> {
                Holder<AnimationInterfaceKey> holder = AkytheraProject.getCurrentProject()
                    .getDataBlockHolder(AnimationInterfaceKey.ENUM_MANAGER, id);
                return holder != null && holder.isBound();
            },
            box -> {
                List<ResourceKey<AnimationInterfaceKey>> options = AkytheraProject.getCurrentProject()
                    .getMergedDataBlockIdentifiers(AnimationInterfaceKey.ENUM_MANAGER).stream()
                    .map(id -> ResourceKey.create(AnimationInterfaceKey.ENUM_MANAGER.directoryKey(), id))
                    .toList();

                openPickerUnder(box, new RegistryLookupModal<>(
                    font,
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_INTERFACE_KEY),
                    options,
                    // An unparseable key reads as null; nothing to re-key to, so such an undo is a no-op.
                    key -> commitNodeIdentifier(current, id -> {
                        if (id != null) node.setInterfaceKeyName(id.toString());
                    }, key.identifier(), box)));
            }
        );
    }

    /// Writes `picked` onto the node, refreshes the row's box, and records the change as one
    /// [DataChange] — so a node property edit is undoable and marks the title dirty, like every other
    /// edit on this screen. A pick equal to the current value is dropped.
    private static void commitNodeIdentifier(
        Supplier<@Nullable Identifier> current,
        Consumer<@Nullable Identifier> apply,
        Identifier picked,
        PopupBox<Identifier> box
    ) {
        Identifier old = current.get();
        if (picked.equals(old)) return;

        apply.accept(picked);
        box.setValue(picked);

        HistoryManager.getInstance().pushHistory(new DataChange<>(old, picked, value -> {
            apply.accept(value);
            box.setValue(value);
        }));
    }

    /// Drops `modal` open under `box` — sized to it and flipped / edge-aligned when the viewport would
    /// clip it, the placement every [PopupBox] picker in the editor uses (see
    /// [AkytheraEditorLayoutManager#openModalWidgetUnder]).
    private static void openPickerUnder(PopupBox<Identifier> box, RegistryLookupModal<?> modal) {
        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Selection handler for a State Machine tab: a single selected state shows its (re-assignable)
    /// [FSMAnimationState] signature in the Details panel, a selected group its editor name and a
    /// selected transition its blend time; any other selection (empty / multi / Entry) reverts the
    /// panel to the selected variable.
    private void onMachineSelectionChanged(
        String machineName, StateMachineGraphWidget machine, StateMachineGraphWidget.@Nullable Selection selection
    ) {
        switch (selection) {
            case null -> {
                detailsNamedNode = null;
                scriptNodeDetailsPanel.showVariable(variableController.getSelected());
            }
            case StateMachineGraphWidget.StateNode state -> {
                variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
                detailsNamedNode = null; // a machine state is not an AbstractNodeWidget-tracked selection
                showStateDetails(machineName, machine, state);
            }
            case StateMachineGraphWidget.GroupNode group -> {
                variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
                detailsNamedNode = null;
                showGroupDetails(machineName, machine, group);
            }
            case StateMachineGraphWidget.Transition transition -> {
                variableController.select(null); // clear variable highlight (also resets detailsNamedNode)
                detailsNamedNode = null;
                showTransitionDetails(machine, transition);
            }
        }
    }

    /// Builds and shows the Details combo for a selected machine state: the merged [FSMAnimationState]
    /// signatures minus the reserved Entry / Exit states and minus every signature another state on the
    /// same machine already carries (so a pick can never duplicate a state), the state's own current
    /// signature kept in the list. Picking one re-signs the state (after flushing any of its now
    /// stale-keyed sub-tabs); the `(none)` row and a blank / duplicate are rejected and revert.
    private void showStateDetails(
        String machineName, StateMachineGraphWidget machine, StateMachineGraphWidget.StateNode state
    ) {
        Set<String> taken = machine.stateNames();
        taken.remove(state.name()); // the state's own current signature stays selectable

        Set<Identifier> reserved = Set.of(
            AkytheraAnimationState.ENTRY.identifier(),
            AkytheraAnimationState.EXIT.identifier());

        List<Identifier> options = new ArrayList<>();

        for (Identifier id : AkytheraProject.getCurrentProject()
                .getMergedDataBlockIdentifiers(FSMAnimationState.ENUM_MANAGER)) {
            if (reserved.contains(id)) continue;
            if (taken.contains(id.toString())) continue;
            options.add(id);
        }

        options.sort(Comparator.comparing(Identifier::toString));

        scriptNodeDetailsPanel.showAnimationState(
            options,
            () -> Identifier.tryParse(state.name()),
            picked -> {
                if (picked == null) return false;
                if (picked.toString().equals(state.name())) return true;
                // Flush + close this state's now stale-keyed sub-tabs (they key off the old name) before
                // the re-sign so their write-back lands on the old name; the change then retargets it.
                closeStateSubTabs(machineName, state.name());
                return machine.changeStateSignature(state, picked.toString());
            });
    }

    /// Shows the selected state group's editor-only name. The name never reaches the engine — the
    /// compile expands the group into per-member transitions — but it keys the group's condition
    /// sub-tabs, so those are flushed and closed before a rename lands; a blank or taken name reverts.
    private void showGroupDetails(
        String machineName, StateMachineGraphWidget machine, StateMachineGraphWidget.GroupNode group
    ) {
        scriptNodeDetailsPanel.showNameRow(
            LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME,
            group::name,
            newName -> {
                if (newName.equals(group.name())) return true;
                closeGroupSubTabs(machineName, group.name());
                return machine.changeGroupName(group, newName);
            });
    }

    /// Shows the selected transition's three engine-bound properties, each compiled into the
    /// like-named [AnimationStateTransition] component: the seconds the machine cross-fades over while
    /// taking it, whether the state it leaves hands its elapsed time to the one it enters, and the
    /// priority it is taken at.
    private void showTransitionDetails(
        StateMachineGraphWidget machine, StateMachineGraphWidget.Transition transition
    ) {
        scriptNodeDetailsPanel.showValueRows(List.of(
            new ScriptNodeDetailsPanel.FloatRow(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_BLEND_TIME,
                BLEND_TIME_MIN, BLEND_TIME_MAX, BLEND_TIME_STEP,
                () -> machine.getTransitionBlendTime(transition),
                blendTime -> machine.setTransitionBlendTime(transition, blendTime)),
            new ScriptNodeDetailsPanel.BooleanRow(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_TRANSFER_ELAPSED_TIME,
                () -> machine.getTransitionTransferElapsedTime(transition),
                transfer -> machine.setTransitionTransferElapsedTime(transition, transfer)),
            new ScriptNodeDetailsPanel.IntRow(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_TRANSITION_PRIORITY,
                TRANSITION_PRIORITY_MIN, TRANSITION_PRIORITY_MAX,
                () -> machine.getTransitionPriority(transition),
                priority -> machine.setTransitionPriority(transition, priority))));
    }

    /// Closes (flushing first) every open condition sub-tab of `groupName` on `machineName` — their
    /// keys embed the group name, so a rename would leave them pointing at the old one.
    private void closeGroupSubTabs(String machineName, String groupName) {
        String prefix = ScriptCanvasKeys.groupTransition(machineName, groupName, "");

        List<ScriptNodeGraphWidget> toClose = new ArrayList<>();

        for (Map.Entry<String, ScriptNodeGraphWidget> e : openSubGraphs.entrySet()) {
            if (e.getKey().startsWith(prefix)) toClose.add(e.getValue());
        }

        for (ScriptNodeGraphWidget graph : toClose) closeTab(graph);
    }

    /// Closes (flushing first) every open sub-tab whose key embeds `stateName` on `machineName` — the
    /// state's own pose sub-tab and every transition-condition sub-tab entering or leaving it — so a
    /// state re-sign can't leave name-keyed sub-tab bookkeeping pointing at the old signature.
    private void closeStateSubTabs(String machineName, String stateName) {
        String poseKey = machineName + "/state/" + stateName;
        String transPrefix = machineName + "/trans/";
        String groupTransPrefix = machineName + "/gtrans/";

        List<ScriptNodeGraphWidget> toClose = new ArrayList<>();

        for (Map.Entry<String, ScriptNodeGraphWidget> e : openSubGraphs.entrySet()) {
            String key = e.getKey();

            if (key.equals(poseKey)) {
                toClose.add(e.getValue());
            } else if (key.startsWith(transPrefix)) {
                String pair = key.substring(transPrefix.length()); // "from>to"
                int split = pair.indexOf('>');
                if (split < 0) continue;
                String from = pair.substring(0, split);
                String to = pair.substring(split + 1);
                if (from.equals(stateName) || to.equals(stateName)) toClose.add(e.getValue());
            } else if (key.startsWith(groupTransPrefix)) {
                // A group edge's source is a group, so only its target can name the state.
                String pair = key.substring(groupTransPrefix.length()); // "group>to"
                int split = pair.indexOf('>');
                if (split < 0) continue;
                if (pair.substring(split + 1).equals(stateName)) toClose.add(e.getValue());
            }
        }

        for (ScriptNodeGraphWidget graph : toClose) closeTab(graph);
    }

    /// Plays the animation sequence bound to `sequenceId` on the [#model3DViewer] — looping and
    /// self-advancing — so pressing a [AnimSequencePlayerNodeWidget] shows that clip on the preview
    /// model. No-op when the asset can't be resolved (e.g. a dangling id) or the previewer has no
    /// live entity model component.
    private void previewSequence(Identifier sequenceId) {
        EntityModelComponent entityModel =
            model3DViewer.getComponent(EntityModelComponent.class);
        if (entityModel == null) return;

        Holder<AnimationSequence> sequence = AkytheraProject.getCurrentProject()
            .getEntryFromProjectAndRegistry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, sequenceId);
        if (sequence == null || !sequence.isBound()) return;

        entityModel.setSimplePlayingAnimation(sequence.value().animClip(), true);
    }

    /// Renames a Cached Pose node to `rawName` (trimmed) and retargets every getter referencing the old
    /// name, so the cache and its `Use cached pose` readers stay linked. Returns false — rejecting the
    /// edit — only when the name is blank; a name another cache already defines is reported by the
    /// compile ([#collectDuplicateProperties]).
    private boolean renameCachedPose(PoseCachingNodeWidget node, String rawName) {
        String oldName = node.cacheName();
        String newName = rawName.trim();
        if (newName.equals(oldName)) return true;
        if (newName.isEmpty()) return false;

        node.setCacheName(newName);

        // Retarget every getter referencing this cache wherever it can live, so a rename never leaves a
        // dangling getter: the main Anim Graph, every open sub-graph tab (live widgets), and every
        // stored state-machine state pose sub-graph (closed tabs).
        poseGraph.renameCachedPoseGetters(oldName, newName);

        for (ScriptNodeGraphWidget open : openSubGraphs.values()) {
            open.renameCachedPoseGetters(oldName, newName);
        }

        stateMachines.replaceAll((name, data) -> data.withCachedPoseGettersRenamed(oldName, newName));

        return true;
    }

    /// Renames a State Machine node's machine to `rawName` (trimmed): re-keys its [StateMachineData]
    /// and closes any open tabs for it (so stale name-keyed bookkeeping can't resurface). Returns false
    /// — rejecting the edit — when the name is blank or already used by another machine.
    private boolean renameStateMachine(StateMachineNodeWidget node, String rawName) {
        String oldName = node.machineName();
        String newName = rawName.trim();
        if (newName.equals(oldName)) return true;
        if (newName.isEmpty()) return false;

        Set<String> used = new HashSet<>(stateMachines.keySet());
        used.addAll(poseGraph.stateMachineNames());
        used.remove(oldName);
        if (used.contains(newName)) return false;

        // Flush + close any open tabs for the old machine before re-keying its data.
        closeMachineTabs(oldName);

        StateMachineData data = stateMachines.remove(oldName);
        if (data != null) stateMachines.put(newName, data);

        node.setMachineName(newName);
        return true;
    }

    /// Closes the machine's canvas tab and every state-pose / transition-condition sub-tab beneath it
    /// (each tab's close callback flushes its edits first).
    private void closeMachineTabs(String machineName) {
        List<ScriptNodeGraphWidget> subTabs = new ArrayList<>();

        for (Map.Entry<String, ScriptNodeGraphWidget> e : openSubGraphs.entrySet()) {
            if (e.getKey().startsWith(machineName + "/")) subTabs.add(e.getValue());
        }

        for (ScriptNodeGraphWidget sub : subTabs) closeTab(sub);

        StateMachineGraphWidget machineTab = openStateMachines.get(machineName);

        if (machineTab != null) closeTab(machineTab);
    }

    private void closeTab(net.minecraft.client.gui.components.AbstractWidget content) {
        int index = blueprintEditor.indexOfContent(content);
        if (index >= 0) blueprintEditor.removeTab(index);
    }

    /// Opens (or re-focuses) the closeable `{name}` sub-tab hosting a state machine's canvas. The
    /// canvas edits its [StateMachineData] live; closing the tab (or a save) flushes it back into
    /// [#stateMachines] so reopening restores it. Double-clicking a state / transition there opens
    /// its own nested sub-tab.
    private void openStateMachineTab(StateMachineNodeWidget sm) {
        String name = sm.machineName();

        StateMachineGraphWidget existing = openStateMachines.get(name);
        if (existing != null) {
            blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(existing));
            return;
        }

        StateMachineGraphWidget widget = new StateMachineGraphWidget(
            minecraft.font,
            stateMachines.getOrDefault(name, StateMachineData.EMPTY),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        widget.setOnStateActivated(state -> openStatePoseTab(name, widget, state.name()));
        widget.setOnTransitionActivated((from, to) -> openTransitionConditionTab(name, widget, from, to));
        widget.setOnGroupTransitionActivated((group, to) -> openGroupTransitionConditionTab(name, widget, group, to));
        // Selecting a single state shows its (re-assignable) FSMAnimationState signature in the Details
        // panel, a group its name, a transition its blend time.
        widget.setOnSelectionChanged(selection -> onMachineSelectionChanged(name, widget, selection));
        openStateMachines.put(name, widget);

        Runnable flush = () -> stateMachines.put(name, widget.exportData());
        openEditors.add(new OpenEditor(widget, flush));

        blueprintEditor.addCloseableTab(Component.literal(name), widget, () -> {
            flush.run();
            openStateMachines.remove(name);
            openEditors.removeIf(e -> e.content() == widget);
        });
        blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(widget));
    }

    /// Opens (or re-focuses) the closeable `{state}` sub-tab: a pose data-flow graph terminating at an
    /// Output Pose node, bound to that state's pose sub-graph.
    private void openStatePoseTab(String machineName, StateMachineGraphWidget machine, String stateName) {
        String key = ScriptCanvasKeys.statePose(machineName, stateName);
        ScriptNodeGraphWidget existing = openSubGraphs.get(key);
        if (existing != null) {
            blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(existing));
            return;
        }

        ScriptNodeGraphWidget graph = makeSubGraph(ScriptFunction.Usage.ANIM_GRAPH, ScriptFunction.Usage.SCRIPT);
        ScriptGraphData data = machine.getStatePoseGraph(stateName);
        if (data.isEmpty()) {
            graph.addNode(new OutputPoseNodeWidget(
                minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_OUTPUT_POSE),
                OUTPUT_POSE_X, OUTPUT_POSE_Y));
        } else {
            graph.importGraph(data, subGraphEntryFactory);
        }

        registerSubGraphTab(key, Component.literal(stateName), graph,
            () -> machine.setStatePoseGraph(stateName, graph.exportGraph()), machineName, machine);
    }

    /// Opens (or re-focuses) the closeable `{from} to {to}` sub-tab: a boolean data-flow graph
    /// terminating at a "Can Enter Transition" result, bound to that transition's condition sub-graph.
    private void openTransitionConditionTab(String machineName, StateMachineGraphWidget machine, String from, String to) {
        String key = ScriptCanvasKeys.transition(machineName, from, to);
        ScriptNodeGraphWidget existing = openSubGraphs.get(key);
        if (existing != null) {
            blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(existing));
            return;
        }

        ScriptNodeGraphWidget graph = makeSubGraph(ScriptFunction.Usage.SCRIPT);
        fillConditionCanvas(graph, machine.getTransitionCondition(from, to));

        String title = (from.isEmpty() ? "Entry" : from) + " to " + to;
        registerSubGraphTab(key, Component.literal(title), graph,
            () -> machine.setTransitionCondition(from, to, graph.exportGraph()), machineName, machine);
    }

    /// Opens (or re-focuses) the closeable `{group} to {to}` sub-tab: the boolean condition every member
    /// of the group leaves on. One canvas, copied onto each member at compile.
    private void openGroupTransitionConditionTab(
        String machineName, StateMachineGraphWidget machine, String group, String to
    ) {
        String key = ScriptCanvasKeys.groupTransition(machineName, group, to);
        ScriptNodeGraphWidget existing = openSubGraphs.get(key);
        if (existing != null) {
            blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(existing));
            return;
        }

        ScriptNodeGraphWidget graph = makeSubGraph(ScriptFunction.Usage.SCRIPT);
        fillConditionCanvas(graph, machine.getGroupTransitionCondition(group, to));

        registerSubGraphTab(key, Component.literal(group + " to " + to), graph,
            () -> machine.setGroupTransitionCondition(group, to, graph.exportGraph()), machineName, machine);
    }

    /// Fills a freshly-made transition-condition canvas from `data` — a "Can Enter Transition" result
    /// root on an empty one, the persisted rows otherwise — and marks it a condition canvas, which is
    /// what puts the Source State Driver node in its palette.
    private void fillConditionCanvas(ScriptNodeGraphWidget graph, ScriptGraphData data) {
        conditionCanvases.add(graph);

        if (data.isEmpty()) {
            graph.addNode(new TransitionResultNodeWidget(
                minecraft.font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_TRANSITION_RESULT),
                OUTPUT_POSE_X, OUTPUT_POSE_Y));
            return;
        }

        graph.importGraph(data, subGraphEntryFactory);
    }

    /// Shared open-bookkeeping for a sub-graph (state pose / transition condition) tab: registers its
    /// flush (write the edited sub-graph back into its machine, then re-export the machine into
    /// [#stateMachines]), adds the closeable tab, and focuses it.
    private void registerSubGraphTab(
        String key, Component title, ScriptNodeGraphWidget graph,
        Runnable writeBack, String machineName, StateMachineGraphWidget machine
    ) {
        openSubGraphs.put(key, graph);

        Runnable flush = () -> {
            writeBack.run();
            stateMachines.put(machineName, machine.exportData());
        };
        openEditors.add(new OpenEditor(graph, flush));

        blueprintEditor.addCloseableTab(title, graph, () -> {
            flush.run();
            openSubGraphs.remove(key);
            conditionCanvases.remove(graph);
            openEditors.removeIf(e -> e.content() == graph);
        });
        blueprintEditor.setActiveIndex(blueprintEditor.indexOfContent(graph));

        // Mark the canvas on sight, but only once a compile has given a verdict to show: a compile
        // validates what is on screen at the time, so without this a page the toast already named
        // opens with no banner on the node that failed it.
        if (compiled) graph.validate();
    }

    /// A pose / condition sub-graph canvas with the standard editor hookups for the given usage(s).
    private ScriptNodeGraphWidget makeSubGraph(ScriptFunction.Usage... usages) {
        ScriptNodeGraphWidget graph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        graph.setSuggestionProvider(this::buildNodeSuggestions);
        graph.setScriptOwnerType(Animator.class);
        graph.setUsage(usages);
        graph.setDataFlowOnly(true);
        graph.setNodeActivationHandler(this::onNodeActivated);
        graph.setSelectionHandler(this::onNodeSelected);
        return graph;
    }

    /// Flushes every open editor (state-machine tabs + sub-graph tabs) back into [#stateMachines]
    /// before a save / compile reads it (closed tabs already flushed via their close callback).
    private void flushOpenEditors() {
        for (OpenEditor e : openEditors) e.onFlush().run();
    }

    /// Drops [#stateMachines] entries no live State Machine node references — a machine whose node was
    /// deleted. Run after [#flushOpenEditors] so the graphs are current. References are gathered
    /// transitively: the pose graph's State Machine nodes, then each referenced machine's state pose
    /// sub-graphs (which may themselves hold State Machine nodes).
    private void pruneOrphanedStateMachines() {
        Set<String> referenced = new HashSet<>();
        Deque<ScriptGraphData> queue = new ArrayDeque<>();
        queue.add(poseGraph.exportGraph());

        while (!queue.isEmpty()) {
            for (GraphNode row : queue.poll().nodes()) {
                if (!(row.data() instanceof StateMachineNodeData machine)) continue;
                String name = machine.machineName();
                if (referenced.add(name)) {
                    StateMachineData data = stateMachines.get(name);
                    if (data != null) {
                        for (StateMachineData.StateData state : data.states()) queue.add(state.poseGraph());
                    }
                }
            }
        }

        stateMachines.keySet().retainAll(referenced);
    }

    /// Flow nodes: the structural nodes (Sequence / Branch), a "Set" node per script
    /// variable, and a flow-function node per **non-pure** [ScriptFunction] passing `filter`
    /// (pure functions are value providers, not flow steps, so they are excluded here).
    private void addFlowSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter,
        Set<ScriptFunction.Usage> usage,
        boolean dataFlowOnly
    ) {
        // Sequence / Branch and variable setters are execution-flow constructs — they have no place
        // on a data-flow canvas (the Anim Graph, a state's pose graph, a transition's condition
        // graph), only on the Event Graph.
        if (!dataFlowOnly) {
            if (providedType == null) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE),
                    (font, x, y) -> new SequenceNodeWidget(
                        font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE), x, y)
                ));
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH),
                    (font, x, y) -> new BranchingNodeWidget(
                        font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH), x, y)
                ));
            }

            for (VariableDraft draft : variableDrafts) {
                String name = draft.getName();
                Class<?> type = draft.getType().type();

                if (!AbstractNodeWidget.valueAssignable(providedType, type)) continue;

                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
                    (font, x, y) -> new ScriptVariableSetterNodeWidget(
                        font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                    MEMBER_VARIABLES_CATEGORY
                ));
            }
        }

        // Flow-function nodes are execution constructs, so they belong only on a canvas that has an
        // execution flow — never on a data-flow-only canvas (the Anim Graph, a state's pose graph, a
        // transition's condition graph), even though those now accept SCRIPT pure functions.
        if (dataFlowOnly) return;

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (function.isPure()) continue;
            if (!usage.contains(function.metadata().usage())) continue;
            if (!filter.test(function)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> new FlowFunctionNodeWidget(font, function, x, y),
                function.metadata().category()
            ));
        }
    }

    /// Value providers ([PureNode]s) other than functions, offered to fill a **data input**: the Self node,
    /// an inline Literal node, and a getter per script variable. When the dragged pin carries a concrete
    /// `expectedType`, getters whose output cannot satisfy it are dropped and a single Literal of exactly
    /// that type is offered; an untyped slot offers the common literal types instead.
    private void addValueProviderSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        Set<ScriptFunction.Usage> usage
    ) {
        // Self — the script owner. Offered only where the owner can actually satisfy the slot: an
        // untyped pin, or one whose type the owner is assignable to. Self's output is typed Object
        // (a wildcard accepted by any object slot in the wire check), so without this gate it would
        // wrongly appear on a concrete slot like the Output Pose's Pose input.
        if (expectedType == null || expectedType.isAssignableFrom(Animator.class)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF),
                (font, x, y) -> new SelfNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF), x, y)
            ));
        }

        // Literal(s) — a typed literal for a concrete slot, or the defaults for an untyped one.
        if (LiteralValueSupport.isSupported(expectedType)) {
            suggestions.add(literalSuggestion(expectedType));
        } else if (expectedType == null) {
            for (Class<?> type : DEFAULT_LITERAL_TYPES) {
                suggestions.add(literalSuggestion(type));
            }
        }

        // Member variables are a script concept, offered on any canvas that accepts SCRIPT (now the
        // pose graphs too — a member like `float speed` can drive a BlendSpace input).
        if (usage.contains(ScriptFunction.Usage.SCRIPT)) {
            addMemberVariableGetters(suggestions, expectedType);
        }
    }

    /// The category folder member-variable getters are filed under in the suggestion tree.
    private static final String MEMBER_VARIABLES_CATEGORY = "Member Variables";

    /// A getter node per script member variable whose type can satisfy `expectedType` (all of
    /// them when it is null), filed under [#MEMBER_VARIABLES_CATEGORY]. Shared by the data-input
    /// drag (value providers) and the empty-canvas "All Actions" menu.
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
                MEMBER_VARIABLES_CATEGORY
            ));
        }
    }

    /// A node per **pure** value-returning [ScriptFunction] passing `filter`. When the gesture
    /// carries a concrete `expectedType` (a data-input drag), functions whose return value
    /// cannot satisfy it are dropped; for a parameter-output drag or a right-click `expectedType`
    /// is null and the `filter` alone decides membership (e.g. "accepts the dragged value").
    private void addPureFunctionSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter,
        Set<ScriptFunction.Usage> usage
    ) {
        // The generic pure-function list (and the Math nodes below) is offered on any canvas that
        // accepts SCRIPT — the Event Graph, transition conditions, AND the Anim Graph / pose graphs
        // (e.g. float math feeding a BlendSpace's X/Y). ANIM_GRAPH functions stay curated (players via
        // Asset-Browser drop, pose sources via addAnimGraphSuggestions; the compiler's plumbing such as
        // store_anim_graph_layer / anim_graph_pose is never offered), so only SCRIPT ones list here.
        if (!usage.contains(ScriptFunction.Usage.SCRIPT)) return;

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (!function.isPure()) continue;
            if (function.metadata().usage() != ScriptFunction.Usage.SCRIPT) continue;
            // Comparison functions are surfaced as dedicated type-detecting comparator nodes
            // (appended below) instead of plain pure-function nodes.
            if (isComparatorFunction(function)) continue;
            if (!filter.test(function)) continue;
            Class<?> returnType = function.functionSignature().returnType();
            if (returnType == void.class || returnType == Void.class) continue;
            if (!AbstractNodeWidget.valueAssignable(returnType, expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> new PureFunctionNodeWidget(font, function, x, y),
                function.metadata().category()
            ));
        }

        // The "Math" nodes (comparators / arithmetic operators / NOT / logic) follow, on the same
        // SCRIPT-accepting canvases (guarded at the top of this method).

        // Comparator nodes compare two numeric operands and return boolean, so offer them only
        // when their boolean output fits the target input slot (`expectedType`) AND the dragged
        // value, if any, is a valid numeric operand (`providedType`). The operand check is what
        // keeps them out of a non-numeric output drag such as a Vec3 (Vec3 is not a Number).
        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (ComparatorNodeWidget.Operator op : ComparatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new ComparatorNodeWidget(font, op, x, y),
                    "Math"
                ));
            }
        }

        // Arithmetic operator nodes consume and return a numeric operand type, so offer them only
        // when their numeric output fits the target input slot (`expectedType`) AND the dragged
        // value, if any, is a valid numeric operand (`providedType`). The operand check is what
        // suppresses e.g. `Subtract (-)` when dragging a Vec3 output, since Vec3 is not a Number.
        if (AbstractNodeWidget.valueAssignable(double.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (OperatorNodeWidget.Operator op : OperatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new OperatorNodeWidget(font, op, x, y),
                    "Math"
                ));
            }
        }

        // The boolean NOT node: offered when a boolean output fits the target slot and the dragged
        // value, if any, is a boolean (e.g. inverting a predicate in a transition condition).
        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, boolean.class)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("NOT"),
                (font, x, y) -> new NotNodeWidget(font, x, y),
                "Logic"
            ));

            // Binary boolean logic nodes (AND / OR / NAND / NOR / XOR): same gate as NOT — a boolean
            // output that fits the target slot and, for a value drag, a boolean operand.
            for (LogicalOperationNodeWidget.Operator op : LogicalOperationNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new LogicalOperationNodeWidget(font, op, x, y),
                    "Logic"
                ));
            }
        }
    }

    /// Whether `function` is a comparison function (takes a [Comparator] argument) — those are
    /// presented as [ComparatorNodeWidget]s rather than generic pure-function nodes.
    private static boolean isComparatorFunction(ScriptFunction function) {
        return function.functionSignature().parameters().stream().anyMatch(Comparator.class::isAssignableFrom);
    }

    private NodeSuggestModal.Suggestion<NodeWidgetFactory> literalSuggestion(Class<?> type) {
        return new NodeSuggestModal.Suggestion<>(
            Component.literal("Literal: " + type.getSimpleName()),
            (font, x, y) -> new LiteralNodeWidget(font, type, x, y)
        );
    }

    /// Drops the dragged Asset Browser asset onto the Anim Graph as a pose-player node bound to it: a
    /// blendspace → [BlendSpacePlayerNodeWidget], a sequence → [AnimSequencePlayerNodeWidget]. No-op
    /// unless the Anim Graph is the active tab and the cursor is inside it.
    private void dropAssetAsNode(AssetDrag drag, double screenX, double screenY) {
        // Accept onto whichever pose canvas is active — the main Anim Graph or a state's pose sub-tab.
        if (!(blueprintEditor.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return;
        if (!graph.usage().contains(ScriptFunction.Usage.ANIM_GRAPH)) return;
        if (!graph.isPointInside(screenX, screenY)) return;

        NodeWidgetFactory factory = drag.blendSpace()
            ? (font, x, y) -> new BlendSpacePlayerNodeWidget(
                font, EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, drag.id()), x, y)
            : (font, x, y) -> new AnimSequencePlayerNodeWidget(
                font, EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, drag.id()), x, y);

        graph.spawnNodeAt(factory, screenX, screenY);
    }

    // ***************************************************************
    // Copy / cut / paste
    // ***************************************************************

    /// The last copy, shared across every canvas and every open animator-script editor (a `static`
    /// clipboard, so a selection copied here pastes into another script too). Null until the first copy.
    ///
    /// A node-canvas copy fills [#graph] — the copied nodes and the wires among them — and a
    /// state-machine-canvas copy fills [#states] — the copied states, each with the driver authored
    /// under it and the transitions to the other copied states. The other stays empty, which is what
    /// lets one paste serve both: an empty graph lands nothing on a node canvas and an empty state list
    /// lands nothing on a machine canvas, so a clipboard pasted onto the wrong canvas is a no-op with
    /// no check of its own.
    ///
    /// [#machines] is the nested content either kind carries: the topology of every State Machine the
    /// copy names, gathered transitively, so a paste recreates it in full even on a script that never
    /// had that machine.
    private record NodeClipboard(
        ScriptGraphData graph,
        List<StateMachineData.StateData> states,
        Map<String, StateMachineData> machines
    ) {}

    @Nullable
    private static NodeClipboard clipboard;

    /// COPY: snapshots the active canvas's selection into the [#clipboard] — nodes on a node canvas,
    /// states on a state-machine canvas. Each canvas skips what it will not hand out (see
    /// [AbstractNodeWidget#isCopyable] and [StateMachineGraphWidget#copySelection]). Returns whether
    /// anything was copied; a copy with nothing copyable selected leaves the clipboard untouched.
    private boolean copySelection() {
        switch (blueprintEditor.getActiveContent()) {
            case ScriptNodeGraphWidget graph -> {
                ScriptGraphData copied = graph.copySelection();
                if (copied == null) return false;

                clipboard = new NodeClipboard(copied, List.of(), collectMachineData(machineNamesIn(copied)));
            }
            case StateMachineGraphWidget machine -> {
                List<StateMachineData.StateData> copied = machine.copySelection();
                if (copied == null) return false;

                clipboard = new NodeClipboard(ScriptGraphData.EMPTY, copied,
                    collectMachineData(copied.stream().flatMap(s -> machineNamesIn(s.poseGraph()))));
            }
            default -> {
                return false;
            }
        }

        return true;
    }

    /// CUT: copies the active canvas's selection, then deletes it. A selection with nothing copyable in
    /// it is left alone rather than deleted unrecorded.
    private void cutSelection() {
        if (!copySelection()) return;

        switch (blueprintEditor.getActiveContent()) {
            case ScriptNodeGraphWidget graph -> graph.deleteSelection();
            case StateMachineGraphWidget machine -> machine.deleteSelection();
            default -> {}
        }
    }

    /// The [StateMachineData] of every State Machine reachable from `seeds`, gathered transitively (a
    /// state's driver may itself host a nested State Machine). Open editors are flushed first so the
    /// snapshot reflects any unsaved tab edits. Empty when nothing is reachable.
    private Map<String, StateMachineData> collectMachineData(Stream<String> seeds) {
        Deque<String> queue = seeds.collect(Collectors.toCollection(ArrayDeque::new));
        if (queue.isEmpty()) return Map.of();

        flushOpenEditors(); // make `stateMachines` reflect any open tab's live edits

        Map<String, StateMachineData> out = new HashMap<>();

        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (out.containsKey(name)) continue;

            StateMachineData data = stateMachines.get(name);
            if (data == null) continue;
            out.put(name, data);

            for (StateMachineData.StateData state : data.states()) {
                machineNamesIn(state.poseGraph()).forEach(queue::add);
            }
        }

        return out;
    }

    /// PASTE: drops the [#clipboard] onto the active canvas, offset by [#PASTE_OFFSET], and registers
    /// the machines it carries once anything lands, so opening a pasted State Machine's tab restores
    /// the copied states, transitions and drivers. Open editors are flushed first so that registration
    /// sees any unsaved tab edits.
    ///
    /// Nothing here keeps a name or key unique. A paste may land a State Machine named like one the
    /// script already has — a cut and re-paste is exactly that, and re-issuing the name would be the
    /// wrong answer to it — and the compile reports what actually collided
    /// ([#collectDuplicateProperties]). A carried machine whose name is already registered keeps the
    /// registered topology: the two nodes then name one machine.
    private void pasteClipboard() {
        if (clipboard == null) return;

        flushOpenEditors();

        boolean landed = switch (blueprintEditor.getActiveContent()) {
            case ScriptNodeGraphWidget graph -> !graph.pasteGraph(
                clipboard.graph(), PASTE_OFFSET, PASTE_OFFSET, node -> canPasteNodeOnto(node, graph)).isEmpty();
            case StateMachineGraphWidget machine ->
                !machine.pasteStates(clipboard.states(), PASTE_OFFSET, PASTE_OFFSET).isEmpty();
            default -> false;
        };

        // A machine whose node did not land is dropped by the next save or compile
        // (see [#pruneOrphanedStateMachines]).
        if (landed) clipboard.machines().forEach(stateMachines::putIfAbsent);
    }

    /// Whether a copied `node` may be pasted onto `target`, encoding the same per-canvas palette rules
    /// the suggestion menu uses:
    ///   - structural singletons (entry / Output Pose / transition Result) — never (also un-copied);
    ///   - execution-flow constructs (Sequence / Branch / flow function / variable Set) — only on a
    ///     canvas with an execution flow (the Event Graph), never a data-flow-only pose canvas;
    ///   - pose sources / readers (sequence + blendspace players, cached-pose getter), pose mixers and
    ///     a State Machine — only on a pose (ANIM_GRAPH) canvas;
    ///   - everything else (Self / Literal / member getters / math / logic / reroute / convert) — any
    ///     canvas (all accept SCRIPT).
    ///
    /// Nodes named by a registered signature — an Anim Layer, an Animation Interface, a Cached Pose
    /// definition — never reach here: they are not [AbstractNodeWidget#isCopyable].
    private boolean canPasteNodeOnto(AbstractNodeWidget node, ScriptNodeGraphWidget target) {
        boolean animGraph = target.usage().contains(ScriptFunction.Usage.ANIM_GRAPH);
        boolean dataFlowOnly = target.isDataFlowOnly();

        return switch (node) {
            case EntryPointNodeWidget ignored -> false;
            case OutputPoseNodeWidget ignored -> false;
            case TransitionResultNodeWidget ignored -> false;

            case SequenceNodeWidget ignored -> !dataFlowOnly;
            case BranchingNodeWidget ignored -> !dataFlowOnly;
            case FlowFunctionNodeWidget ignored -> !dataFlowOnly;
            case ScriptVariableSetterNodeWidget ignored -> !dataFlowOnly;

            case AnimSequencePlayerNodeWidget ignored -> animGraph;
            case BlendSpacePlayerNodeWidget ignored -> animGraph;
            case CachedPoseGetterNodeWidget ignored -> animGraph;
            case AbstractBlendNodeWidget ignored -> animGraph;
            case StateMachineNodeWidget ignored -> animGraph;

            default -> true;
        };
    }

    @Override
    protected void init() {
        super.init();

        layoutWidgets();

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(model3DViewer);
    }

    private void layoutWidgets() {
        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
    }

    /// Flattens the live canvases onto the holder (synchronously — it reads widget state), then writes
    /// the file asynchronously behind an await modal. The returned future completes on the render thread
    /// once the file is written, the modal is closed, and the save point is re-snapshotted (clearing the
    /// title's `*`).
    private CompletableFuture<Void> save() {
        syncVariablesToHolder();
        // Flatten the live canvases back onto the holder so saveEntry (EDITOR_CODEC) and the runtime
        // compile (makeObject) both see the latest wiring.
        holder.setUpdateGraph(updateGraph.exportGraph());
        holder.setPoseGraph(poseGraph.exportGraph());
        flushOpenEditors();
        pruneOrphanedStateMachines();
        holder.setStateMachines(stateMachines);

        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(font);
        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((result, error) -> onSaveComplete(awaitModal, error), minecraft);
    }

    /// Render-thread completion for [#save]: closes the await modal (only if it is still the top one)
    /// and, on success, re-snapshots the save point so the title's `*` clears.
    private void onSaveComplete(WidgetContainerModal awaitModal, @Nullable Throwable error) {
        if (AkytheraEditorLayoutManager.getInstance().getModal() == awaitModal) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
        }

        if (error != null) {
            AkytheraLabs.LOGGER.error("Failed to save animator script '{}'", resourceKey.identifier(), error);
            return;
        }

        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Compiles the live canvas into the runtime blueprint *in memory only* — it evaluates the node
    /// connections and builds the actual [DataDrivenAnimatorScript.AnimatorScriptBlueprint] tree (via
    /// [AnimatorScriptHolder#makeObject] / [ScriptGraphCompiler])
    /// without touching the file system, so the author can sanity-check that the graph lowers
    /// cleanly. Mirrors [#save]'s flatten-to-holder step but skips `saveEntry`.
    private void compile() {
        syncVariablesToHolder();

        // Flatten the live canvases / open sub-editors onto the holder first, so the validation below
        // (which inspects the state pose sub-graphs as stored data) and makeObject see the same wiring.
        holder.setUpdateGraph(updateGraph.exportGraph());
        holder.setPoseGraph(poseGraph.exportGraph());
        flushOpenEditors();
        pruneOrphanedStateMachines();
        holder.setStateMachines(stateMachines);

        // Validate each page in turn: an unconnected required input or an incompatible wire marks the
        // offending nodes (UE-style red "ERROR!" banner) and aborts the build, like UE's failed
        // compile. The first failing page names the toast (e.g. "Event Graph" / "akythera:idle").
        compiled = true;
        Component failedPage = firstFailedPage();

        if (failedPage != null) {
            AkytheraLabs.LOGGER.warn("Compile of '{}' failed on page '{}'", resourceKey.identifier(), failedPage.getString());
            showCompileToast(false, failedPage);
            return;
        }

        // Any unresolved (failed-decode) node is a hard compile error — the compilers throw on it
        // (ScriptGraphData#requireNoFailedNodes). Report each row here first so the user sees
        // which nodes, not just the throw.
        List<String> unresolved = collectUnresolvedNodes();

        if (!unresolved.isEmpty()) {
            AkytheraLabs.LOGGER.warn("Compile of '{}' aborted — unresolved nodes: {}", resourceKey.identifier(), unresolved);
            showCompileToast(false, Component.literal(String.join("; ", unresolved)));
            return;
        }

        // A name or key the editor let collide while the graph was authored. Nothing on a canvas keeps
        // one unique, so this is where a collision is caught — before makeObject builds a script whose
        // parts silently overwrite each other.
        List<String> duplicates = collectDuplicateProperties();

        if (!duplicates.isEmpty()) {
            AkytheraLabs.LOGGER.warn("Compile of '{}' aborted — duplicated properties: {}", resourceKey.identifier(), duplicates);
            showCompileToast(false, Component.literal(String.join("; ", duplicates)));
            return;
        }

        // A data block referenced by a state / transition / anim layer, or an asset a pose player is
        // bound to, may have been deleted in the content browser while this script was open: the
        // reference no longer resolves. Report it and abort before makeObject() resolves any holder's
        // value() (which would throw) or lowers a dangling asset id.
        List<Identifier> missing = AnimGraphCompiler.findMissingReferences(holder.getPoseGraph(), stateMachines);

        if (!missing.isEmpty()) {
            AkytheraLabs.LOGGER.warn("Compile of '{}' aborted — missing references: {}", resourceKey.identifier(), missing);
            showCompileToast(false, Component.literal(
                "Missing references: " + String.join(", ", missing.stream().map(Identifier::toString).toList())));
            return;
        }

        try {
            DataDrivenAnimatorScript.AnimatorScriptBlueprint blueprint = holder.makeObject();
            AkytheraLabs.LOGGER.info("Compiled animator script '{}': {}", resourceKey.identifier(), blueprint);
            showCompileToast(true, null);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to compile animator script '{}'", resourceKey.identifier(), e);
            showCompileToast(false, Component.literal(resourceKey.identifier().toString()));
        }
    }

    /// Every unresolved (failed-decode) node across the holder's graphs — the Event / Anim Graph
    /// plus every state machine's pose and condition sub-graphs — as report lines for the compile
    /// toast. Non-empty aborts the compile: the compilers hard-fail on these rows.
    private List<String> collectUnresolvedNodes() {
        List<String> out = new ArrayList<>();
        appendUnresolved(holder.getUpdateGraph(), out);
        appendUnresolved(holder.getPoseGraph(), out);

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData s : machine.states()) {
                appendUnresolved(s.poseGraph(), out);
                for (StateMachineData.TransitionData t : s.transitions()) appendUnresolved(t.conditionGraph(), out);
            }
            for (StateMachineData.TransitionData t : machine.entryTransitions()) appendUnresolved(t.conditionGraph(), out);
        }

        return out;
    }

    private static void appendUnresolved(ScriptGraphData graph, List<String> out) {
        for (ScriptGraphData.FailedNode f : graph.failedNodes()) {
            out.add("Unresolved node (id " + f.id() + "): " + f.message());
        }
    }

    /// A name or key its node must be the only holder of: the label naming the kind, plus the value
    /// itself, read off the persisted payload.
    private record UniqueProperty(String label, String value) {}

    /// The uniqueness `data` carries, or null when it carries none. Each of these is a name or key the
    /// animator resolves *by*, so a second holder makes the script ambiguous: an **Anim Layer**
    /// signature and an **Animation Interface** key each register once per animator
    /// (`BlendingAnimator#addAnimInterface` throws on a duplicate), a **State Machine** name keys the
    /// topology store, and a **Cached Pose** name is what every `Use cached pose` getter reads back.
    private static @Nullable UniqueProperty uniquePropertyOf(NodeData data) {
        return switch (data) {
            case StateMachineNodeData machine -> new UniqueProperty("State Machine", machine.machineName());
            case CachedPoseNodeData cache -> new UniqueProperty("Cached Pose", cache.cacheName());
            case AnimLayerNodeData layer -> new UniqueProperty("Anim Layer", layer.signatureId().toString());
            case AnimInterfaceNodeData key -> new UniqueProperty("Animation Interface key", key.interfaceKeyId().toString());
            default -> null;
        };
    }

    /// Every [UniqueProperty] carried by more than one node, as the report lines for the compile toast
    /// — and, on the way, the red "ERROR!" banner on each live node carrying one, so a duplicate reads
    /// on the canvas the way a wiring error does ([#markDuplicateNodes]).
    ///
    /// Counted over every pose canvas — the root Anim Graph and each state's driver — from the holder,
    /// which [#compile] has already flushed the live canvases onto, so a closed tab counts too. State
    /// signatures are counted separately, per machine: `addState` registers them in that scope, and
    /// they belong to the machine canvas rather than to a node.
    private List<String> collectDuplicateProperties() {
        List<ScriptGraphData> poseGraphs = new ArrayList<>();
        poseGraphs.add(holder.getPoseGraph());

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData state : machine.states()) poseGraphs.add(state.poseGraph());
        }

        Map<UniqueProperty, Integer> counts = new LinkedHashMap<>();

        for (ScriptGraphData graph : poseGraphs) {
            for (GraphNode row : graph.nodes()) {
                UniqueProperty property = uniquePropertyOf(row.data());
                if (property != null) counts.merge(property, 1, Integer::sum);
            }
        }

        Map<UniqueProperty, Integer> duplicated = new LinkedHashMap<>();
        counts.forEach((property, held) -> {
            if (held > 1) duplicated.put(property, held);
        });

        markDuplicateNodes(duplicated);

        List<String> out = new ArrayList<>();
        duplicated.forEach((property, held) ->
            out.add(property.label() + " '" + property.value() + "' carried by " + held + " nodes"));

        for (Map.Entry<String, StateMachineData> entry : stateMachines.entrySet()) {
            Map<String, Integer> signatures = new LinkedHashMap<>();

            for (StateMachineData.StateData state : entry.getValue().states()) {
                signatures.merge(ScriptCanvasKeys.stateName(state.state()), 1, Integer::sum);
            }

            signatures.forEach((name, held) -> {
                if (held > 1) out.add("State '" + name + "' on '" + entry.getKey() + "' carried by " + held + " states");
            });
        }

        return out;
    }

    /// Flags every live node carrying a duplicated property with the red outline + "ERROR!" banner and
    /// the count as its tooltip. Runs over the canvases with widgets on screen — the root Anim Graph
    /// and every open sub-graph tab; a node in a closed tab is named by the toast line alone.
    ///
    /// Additive over [ScriptNodeGraphWidget#validate], which [#compile] ran first (via
    /// [#firstFailedPage]): that pass rewrites every node's error state, so resolving the duplicate and
    /// compiling again is what clears the flag.
    private void markDuplicateNodes(Map<UniqueProperty, Integer> duplicated) {
        if (duplicated.isEmpty()) return;

        List<ScriptNodeGraphWidget> live = new ArrayList<>(openSubGraphs.values());
        live.add(poseGraph);

        for (ScriptNodeGraphWidget canvas : live) {
            canvas.addPayloadErrors(data -> {
                UniqueProperty property = uniquePropertyOf(data);
                Integer held = property == null ? null : duplicated.get(property);

                return held == null ? null : Component.translatable(
                    LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_DUPLICATE,
                    property.label(), property.value(), held);
            });
        }
    }

    /// The first page that fails validation, as the Component naming it for the compile toast, or null
    /// when every page is valid. Checks the live Event Graph and Anim Graph (which also marks their
    /// nodes' error banners), then per state machine: each state's pose sub-graph (named by its state
    /// id, e.g. `akythera:idle`), each transition's condition sub-graph (named like its tab,
    /// e.g. `akythera:idle to akythera:walk`), and each group edge's condition.
    /// Pages are keyed through [ScriptCanvasKeys], the same spelling the sub-tabs and the compiler
    /// use, so an open tab is found and its nodes marked rather than a throwaway probe's. A transition's condition is **required**: it fails
    /// whether it shows as an unwired `Can Enter Transition` result (page visited) or an empty /
    /// never-authored condition graph (page never visited) — both mean an undefined condition, so the
    /// outcome is the same regardless of whether the page was ever opened. Wire a `true` literal into
    /// the result for an unconditional (always-true) transition.
    private @Nullable Component firstFailedPage() {
        // Validate every live canvas up front so any currently-open tab shows its red ERROR banners
        // (not just the first failing page): the Event Graph and Anim Graph are always live, and any
        // open state-pose / transition-condition sub-tab is live too.
        boolean eventFailed = !updateGraph.validate().isEmpty();
        boolean poseFailed = !poseGraph.validate().isEmpty();
        for (ScriptNodeGraphWidget open : openSubGraphs.values()) open.validate();

        if (eventFailed) {
            return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH);
        }

        if (poseFailed) {
            return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_ANIM_GRAPH);
        }

        for (Map.Entry<String, StateMachineData> machineEntry : stateMachines.entrySet()) {
            String machineName = machineEntry.getKey();
            StateMachineData machine = machineEntry.getValue();

            for (StateMachineData.TransitionData entry : machine.entryTransitions()) {
                String to = ScriptCanvasKeys.stateName(entry.to());

                if (transitionConditionHasErrors(
                    ScriptCanvasKeys.transition(machineName, ScriptCanvasKeys.ENTRY, to), entry.conditionGraph())) {
                    return transitionPage(ScriptCanvasKeys.ENTRY, to);
                }
            }

            for (StateMachineData.StateData state : machine.states()) {
                String from = ScriptCanvasKeys.stateName(state.state());

                if (subGraphHasErrors(ScriptCanvasKeys.statePose(machineName, from), state.poseGraph(),
                    ScriptFunction.Usage.ANIM_GRAPH, ScriptFunction.Usage.SCRIPT)) {
                    return Component.literal(from);
                }

                for (StateMachineData.TransitionData transition : state.transitions()) {
                    String to = ScriptCanvasKeys.stateName(transition.to());

                    if (transitionConditionHasErrors(
                        ScriptCanvasKeys.transition(machineName, from, to), transition.conditionGraph())) {
                        return transitionPage(from, to);
                    }
                }
            }

            // A group edge carries one condition canvas, shared by every member the compile expands it
            // onto, so it is checked here rather than per member.
            for (StateMachineData.GroupData group : machine.groups()) {
                for (StateMachineData.TransitionData transition : group.transitions()) {
                    String to = ScriptCanvasKeys.stateName(transition.to());

                    if (transitionConditionHasErrors(
                        ScriptCanvasKeys.groupTransition(machineName, group.name(), to), transition.conditionGraph())) {
                        return transitionPage(group.name(), to);
                    }
                }
            }
        }

        return null;
    }

    /// The compile-toast page name for a transition's condition graph, matching its sub-tab title
    /// (`<from> to <to>`, Entry's outgoing edges shown as `Entry to <to>`).
    private static Component transitionPage(String from, String to) {
        return Component.literal((from.isEmpty() ? "Entry" : from) + " to " + to);
    }

    /// Whether a transition's condition sub-graph fails validation. A transition condition is
    /// **required** (unlike a state's optional pose graph): an empty / never-authored condition is an
    /// undefined transition and is flagged exactly as an open page's unwired `Can Enter Transition`
    /// result is — so the result is identical whether or not the page was ever visited (opening a page
    /// seeds the result node, which the closed-tab path would otherwise never see). To make a
    /// transition fire unconditionally, wire a `true` literal into the result.
    private boolean transitionConditionHasErrors(String openKey, ScriptGraphData data) {
        ScriptNodeGraphWidget open = openSubGraphs.get(openKey);
        if (open != null) return !open.validate().isEmpty();
        if (data.isEmpty()) return true; // undefined condition — same as an unwired result node
        return graphDataHasErrors(data, ScriptFunction.Usage.SCRIPT);
    }

    /// Whether the sub-graph stored under `openKey` (its [#openSubGraphs] key) fails validation. When
    /// its tab is **open**, the live widget is validated so its offending node gets the red "ERROR!"
    /// banner the user sees; when closed, a throwaway probe is used (nothing is on screen to mark).
    private boolean subGraphHasErrors(String openKey, ScriptGraphData data, ScriptFunction.Usage... usage) {
        ScriptNodeGraphWidget open = openSubGraphs.get(openKey);
        if (open != null) return !open.validate().isEmpty();
        return graphDataHasErrors(data, usage);
    }

    /// Whether a stored (closed-tab) sub-graph fails validation, checked by importing it into a
    /// throwaway canvas of the given usage(s) and running the same [ScriptNodeGraphWidget#validate].
    /// An empty sub-graph (never authored) is not an error.
    private boolean graphDataHasErrors(ScriptGraphData data, ScriptFunction.Usage... usage) {
        if (data.isEmpty()) return false;
        ScriptNodeGraphWidget probe = makeSubGraph(usage);
        probe.importGraph(data, (_, _, _) -> null);
        return !probe.validate().isEmpty();
    }

    /// Pops a toast reporting the compile outcome — a success / failure icon plus, on failure, the
    /// `page` the error came from — via [Gui#toastManager()].
    private void showCompileToast(boolean success, @Nullable Component page) {
        Identifier icon = AkytheraLabs.identifier(success ? "widget/compile_success" : "widget/compile_fail");
        Component title = Component.literal(success ? "Compile Succeeded" : "Compile Failed");
        minecraft.gui.toastManager().addToast(new CompileResultToast(icon, title, success ? null : page));
    }

    /// Flushes the edited variable drafts back onto the holder. The graphs are flushed separately
    /// in [#save]; `makeObject` recombines variables + graphs into the runtime blueprint.
    private void syncVariablesToHolder() {
        holder.setVariables(variableDrafts.stream()
            .map(VariableDraft::toInfo)
            .toList());
    }

    /// Node-graph refactor hook: when a variable is renamed, retarget the blueprint's
    /// Get / Set nodes for it so their title / pin labels track the new name. The controller's
    /// rename history re-invokes this (names swapped) on undo / redo, so no extra step is needed.
    private void onVariableRenamed(String oldName, String newName) {
        updateGraph.renameVariableNodes(oldName, newName);
    }

    /// Variables drag-and-drop: a variable released over **any** active node canvas (the Event Graph,
    /// the Anim Graph, or a state-machine state-pose / transition-condition sub-graph) opens a
    /// [VariableDropModal] at the cursor; picking a row spawns the matching node there. A data-flow-only
    /// canvas (no execution flow) offers Get only — a Set node would have no flow to sit on.
    private boolean onVariableDropped(VariableDraft draft, double screenX, double screenY) {
        if (!(blueprintEditor.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        String name = draft.getName();
        Class<?> type = draft.getType().type();

        Runnable spawnGet = () -> graph.spawnNodeAt(
            (font, x, y) -> new ScriptVariableGetterNodeWidget(
                font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name), name, type, x, y),
            screenX, screenY);

        VariableDropModal modal;
        if (graph.isDataFlowOnly()) {
            modal = new VariableDropModal(
                minecraft.font, name,
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
                spawnGet);
        } else {
            modal = new VariableDropModal(
                minecraft.font, name,
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
                spawnGet,
                () -> graph.spawnNodeAt(
                    (font, x, y) -> new ScriptVariableSetterNodeWidget(
                        font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                    screenX, screenY));
        }

        AkytheraEditorLayoutManager.getInstance().openModalWidget(modal, (int) screenX, (int) screenY);
        return true;
    }

    /// Node-graph retype hook: when a variable's type changes, re-type the blueprint's
    /// Get / Set nodes for it and drop the wires the new type can no longer satisfy.
    /// Returns the reversible node-side edit so the controller folds it into one undo step.
    private @Nullable WorkHistory onVariableRetyped(String name, Class<?> newType) {
        return updateGraph.retypeVariableNodes(name, newType);
    }

    /// True when the live history top has diverged from the last save point — i.e. there are edits
    /// to persist. Drives both the title's `*` marker and the quit-prompt skip.
    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // [DebuggableScreen]
    // ***************************************************************

    /// Entities whose animator runs *this* script — mirroring any other entity would highlight rows
    /// against a run that never touched them.
    @Override
    public Predicate<Entity> debugTargetFilter() {
        return entity -> {
            EntityPatch<?> patch = ((EntityAccessor)entity).akythera$getEntityPatch();

            if (patch == null) {
                return false;
            }

            DataDrivenAnimatorScript script = AnimatorProbe.dataDrivenScript(patch);

            return script != null && script.getBlueprint().key().equals(resourceKey);
        };
    }

    /// Mirrors the possessed entity onto the open canvases every tick: the state outline from live
    /// driver state, and each canvas' highlighting and recorded values from the run captured for it.
    ///
    /// The Event Graph reads the **update** record, the entry point its rows run under. A pose canvas
    /// reads the record of the sub-trees the Anim Graph deferred past `construct` — a blendspace's
    /// coordinate inputs, a transition's condition — because the rest of it runs once while the
    /// animator is built, before there is a session to record it.
    ///
    /// The construct record is used by neither: it covers the Event Graph's Constructor chain *and*
    /// the Anim Graph's layer stores, whose row ids come from two different numberings.
    @Override
    public void onDebugTick(final EntityPatch<?> entityPatch) {
        Set<String> activeStates = AnimatorProbe.activeStateIds(entityPatch);

        for (StateMachineGraphWidget machine : openStateMachines.values()) {
            machine.setActiveStateIds(activeStates);
        }

        DataDrivenAnimatorScript script = AnimatorProbe.dataDrivenScript(entityPatch);

        if (script == null) {
            clearDebugRecords();
            debugSnapshotStale = false;
            return;
        }

        ScriptExecutionRecords records = (ScriptExecutionRecords)(Object)script;
        ExecutionRecord update = records.akytheralabs$getUpdateRecord();

        if (update == null) {
            updateGraph.clearDebugRecord();
            debugSnapshotStale = false;
        } else {
            debugSnapshotStale = !updateGraph.setDebugRecord(update);
        }

        Map<String, ExecutionRecord> live = records.akytheralabs$getLiveRecords();

        showCanvasRecord(poseGraph, live.get(ScriptCanvasKeys.ANIM_GRAPH));

        for (Map.Entry<String, ScriptNodeGraphWidget> open : openSubGraphs.entrySet()) {
            showCanvasRecord(open.getValue(), live.get(open.getKey()));
        }
    }

    /// Shows what a pose canvas produced the last time its deferred sub-trees were sampled. No record
    /// means nothing on it was sampled — an inactive state, or a canvas that defers nothing — so it
    /// shows no values at all.
    private static void showCanvasRecord(final ScriptNodeGraphWidget graph, final @Nullable ExecutionRecord record) {
        if (record == null) {
            graph.clearDebugRecord();
            return;
        }

        graph.setDebugRecord(record);
    }

    /// Drops every debug highlight once nothing is possessed, so a finished session does not leave the
    /// canvas frozen on its last capture.
    private void clearDebugStateWhenIdle() {
        if (EntityDebugSession.getInstance().isActive()) {
            return;
        }

        clearDebugRecords();
        debugSnapshotStale = false;

        for (StateMachineGraphWidget machine : openStateMachines.values()) {
            machine.setActiveStateIds(Set.of());
        }
    }

    /// Drops the captured run on every canvas this screen holds.
    private void clearDebugRecords() {
        updateGraph.clearDebugRecord();
        poseGraph.clearDebugRecord();

        for (ScriptNodeGraphWidget open : openSubGraphs.values()) open.clearDebugRecord();
    }

    /// Says the captured run cannot be mapped onto this canvas, in place of the highlights it replaces.
    private void drawStaleDebugBanner(GuiGraphicsExtractor graphics) {
        Component message = Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DEBUG_STALE);
        int bannerWidth = font.width(message) + 16;
        int bannerHeight = font.lineHeight + 6;
        int x = blueprintEditor.getX() + (blueprintEditor.getWidth() - bannerWidth) / 2;
        int y = blueprintEditor.getY() + 4;

        graphics.fill(x, y, x + bannerWidth, y + bannerHeight, AbstractNodeWidget.COLOR_ERROR_BANNER);
        graphics.outline(x, y, bannerWidth, bannerHeight, AbstractNodeWidget.COLOR_ERROR_OUTLINE);
        graphics.text(font, message, x + 8, y + 3, 0xFFFFFFFF, false);
    }

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                // Dismiss the prompt, then save asynchronously and close only once the write finishes.
                AkytheraEditorLayoutManager.getInstance().closeModal();
                save().thenRun(this::onClose);
            },
            () -> {
                // Quit without saving: roll the holder back to its on-disk state so abandoned edits
                // don't survive in memory into the next open.
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
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        clearDebugStateWhenIdle();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (debugSnapshotStale) drawStaleDebugBanner(graphics);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        // While dragging an asset out of the Asset Browser, swallow drags so the lists don't scroll.
        if (draggingAsset != null) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingAsset != null) {
            AssetDrag drag = draggingAsset;
            draggingAsset = null;
            dropAssetAsNode(drag, event.x(), event.y());
            return true;
        }

        return super.mouseReleased(event);
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

        // A modal, a focused edit box, or a node hosting an inline editor owns the keyboard while it is
        // open, so the clipboard keys stay with it rather than acting on the canvas' selection.
        boolean keyboardTaken = getFocusOwningModal() != null
            || AdvancedEditBox.isEditingText(this)
            || (blueprintEditor.getActiveContent() instanceof ScriptNodeGraphWidget graph && graph.isInlineEditing());

        if (!keyboardTaken) {
            if (EditorKeyMappings.COPY.matches(event)) {
                copySelection();
                return true;
            }

            if (EditorKeyMappings.CUT.matches(event)) {
                cutSelection();
                return true;
            }

            if (EditorKeyMappings.PASTE.matches(event)) {
                pasteClipboard();
                return true;
            }
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is
    /// released when it closes.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
