package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptCaller;
import com.yesman.akythera.api.data.modularize.node.ScriptFailure;
import com.yesman.akythera.api.data.modularize.node.ScriptRecorder;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.ClothModelComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.SourceCodeEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenEntityPatchScript;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintParentRef;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.akythera.entitypatch.component.EquipmentComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.MeshComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformSocketHolder;
import com.yesman.akytheralabs.akythera.physics.ClothPhysicsDataHolder;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
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
import com.yesman.akytheralabs.gui.widget.script.*;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ColliderPreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ItemModelComponent;
import com.yesman.akytheralabs.gui.widget.viewer.StaticMeshPreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.TransformComponentPreviewComponent;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Editor screen for an [EntityPatchBlueprintHolder] (an [EntityPatchBlueprint]): a three-column
/// layout under the top menu bar — Components, Functions and Variables on the left, a Preview / Event
/// Graph tab pair in the center, a Details panel on the right.
///
/// The [BlueprintEditorSession] lays the Event Graph, the Functions panel and the Details rows out of the
/// [EntityPatchScript] layout the parent maps to. A focused component's canvas properties open beside it
/// as their own tabs ([#openResultGraphTab]), as does each function's canvas.
///
/// The title beside the close button names what the engine builds this patch with, and
/// `File > Change Parent` picks another: an [EntityPatch] subclass, which is the script owner type of
/// every canvas on the screen and re-opens it, or a source code [EntityPatchScript], which locks
/// everything below the top menu.
public final class EntityPatchEditorScreen extends Screen
    implements ModalAccessibleScreen, SafeCloseScreen, NodeGraphTabHost, ComponentPathHost, DebuggableScreen
{
    private static final int TOP_BAR_H = 22;

    /// Width of the parent picker, in screen pixels.
    private static final int PARENT_PICKER_W = 240;

    private static final int LOCK_DIM_COLOR = 0xC0101010;

    /// Seed positions for a fresh result canvas: params entry left, result root right.
    private static final int RESULT_ENTRY_X = -180;
    private static final int RESULT_ENTRY_Y = 0;
    private static final int RESULT_ROOT_X = 120;
    private static final int RESULT_ROOT_Y = 0;

    /// Open result-graph tabs by property key, plus their flush-back hooks (run on tab close and on
    /// save).
    private final Map<String, ScriptNodeGraphWidget> openGraphs = new HashMap<>();
    private final List<Runnable> graphFlushes = new ArrayList<>();

    /// The skinned stand-in of every mesh component that carries no cloth. Held because the body is an
    /// [EntityModelComponent] too, so these cannot be dropped by type.
    private final List<EntityModelComponent> meshPreviews = new ArrayList<>();

    /// The item model drawn for each equipment component, which a focus on that component drags.
    private final Map<EquipmentComponentHolder, ItemModelComponent> equipmentPreviews = new IdentityHashMap<>();

    /// The marker raised for the focused armature or mesh, neither of which has a stand-in of its own. Null while the
    /// focus is elsewhere.
    private @Nullable TransformComponentPreviewComponent<?> focusMarker;

    /// The armature component the Components panel is focused on, whose skeleton the preview draws.
    /// Null while the focus is elsewhere.
    private @Nullable ArmatureComponentHolder focusedArmature;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;

    /// The spaces of this patch's holders in the preview, read off the body's bones.
    private final TransformComponentHolder.Space space;

    private final TabPanel centerTabs;

    /// The Event Graph, the function canvases and the Functions panel, flushed to the holder on save and
    /// compile.
    private final BlueprintEditorSession session;

    private final ComponentEditorPanel componentPanel;
    private final VariablePanel variablePanel;
    private final ComponentDetailsPanel detailsPanel;

    /// Working copies of the blueprint's variables; flushed to the holder by [#session] on save.
    private final List<VariableDraft> variableDrafts = new ArrayList<>();

    /// The node palette of the Event Graph.
    private final ScriptNodeSuggestions nodeSuggestions = new ScriptNodeSuggestions(this::ownerType, variableDrafts);

    /// The node palette of the data-flow canvases, whose graphs run without the member variables.
    private final ScriptNodeSuggestions dataFlowNodeSuggestions = new ScriptNodeSuggestions(this::ownerType, List.of());
    private final VariableEditController variableController;

    private final EntityPatchBlueprintHolder holder;
    private final ResourceKey<EntityPatchBlueprint> resourceKey;

    /// The key the exported blueprint is registered under in game, the patched entity type's id
    /// (`minecraft:player`). The runs and failures [ScriptRecorder] keeps are keyed by it.
    private final ResourceKey<EntityPatchBlueprint> runtimeKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top
    /// differs from it.
    private @Nullable WorkHistory savePoint;

    /// Whether the canvases carry [ScriptRecorder] failure marks, so they are cleared once the failures
    /// are gone.
    private boolean failuresMarked;

    public EntityPatchEditorScreen(ResourceKey<EntityPatchBlueprint> resourceKey, EntityPatchBlueprintHolder holder) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;
        this.holder = holder;
        this.runtimeKey = ResourceKey.create(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, patchedEntityTypeId());

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
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, holder.getPreviewEntityType(), true, true);

        space = new TransformComponentHolder.Space(
            this::hostOf,
            bone -> {
                EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);

                return model == null ? Transform.IDENTITY : model.getArmatureComponent().getBoneModelTransform(bone);
            },
            () -> Transform.IDENTITY
        );

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
                // Parent picker: the entity patch classes the patched entity can be built as, and the
                // source code scripts, built on the class taking the narrowest entity type
                .on(TopMenuOption.CHANGE_PARENT, () -> {
                    Class<? extends Entity> entityClass = RenderUtils.entityClassOf(patchedEntityTypeId());

                    List<ScriptFunction> ctors = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.stream()
                        .filter(function -> function.metadata().usage() == ScriptFunction.Usage.ENTITY_PATCH_CTOR)
                        .filter(function -> {
                            List<Class<?>> parameters = function.functionSignature().parameters();
                            return parameters.size() >= 2 && parameters.get(1).isAssignableFrom(entityClass);
                        })
                        .toList();

                    ScriptFunction narrowestCtor = ctors.stream()
                        .reduce((a, b) -> a.functionSignature().parameters().get(1)
                            .isAssignableFrom(b.functionSignature().parameters().get(1)) ? b : a)
                        .orElseGet(() -> holder.getParent().ctor());

                    List<CategorizedSelectModal.Choice<BlueprintParentRef>> sourceCodeScripts = AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
                        .filter(entry -> SourceCodeEntityPatchBlueprint.runnable(entry.getValue().type()))
                        .map(entry -> new CategorizedSelectModal.Choice<BlueprintParentRef>(
                            entry.getKey().identifier().toString(),
                            new BlueprintParentRef.SourceCodeScript(narrowestCtor, entry.getValue())))
                        .toList();

                    List<CategorizedSelectModal.Choice<BlueprintParentRef>> patchClasses = ctors.stream()
                        .map(ctor -> new CategorizedSelectModal.Choice<BlueprintParentRef>(
                            ctor.metadata().label(), new BlueprintParentRef.ParentClass(ctor)))
                        .toList();

                    CategorizedSelectModal<BlueprintParentRef> modal = new CategorizedSelectModal<>(
                        minecraft.font,
                        PARENT_PICKER_W,
                        Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PARENT),
                        List.of(
                            new CategorizedSelectModal.Category<>(Component.translatable(
                                LangKeys.GUI_ENTITY_PATCH_PARENT_SELECT_MODAL_CATEGORY_SOURCE_CODE_SCRIPT), sourceCodeScripts),
                            new CategorizedSelectModal.Category<>(Component.translatable(
                                LangKeys.GUI_ENTITY_PATCH_PARENT_SELECT_MODAL_CATEGORY_ENTITY_PATCH_CLASS), patchClasses)
                        ),
                        picked -> {
                            if (picked.equals(holder.getParent())) return;

                            // A patch class re-opens the screen, whose layout it may change, and a source
                            // code script re-locks it. Undo reaches the screen open at the time.
                            Consumer<BlueprintParentRef> writer = value -> {
                                EntityPatchEditorScreen current =
                                    AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof EntityPatchEditorScreen open
                                        && open.resourceKey.equals(resourceKey) ? open : null;

                                if (current != null) current.flushToHolder();

                                holder.setParent(value);

                                if (current == null) return;

                                if (value instanceof BlueprintParentRef.SourceCodeScript) {
                                    current.rebuildWidgets();
                                    return;
                                }

                                EntityPatchEditorScreen reopened = new EntityPatchEditorScreen(resourceKey, holder);

                                reopened.savePoint = current.savePoint;
                                current.onClose();
                                AkytheraEditorLayoutManager.getInstance().openNewScreen(reopened);
                            };

                            HistoryManager.getInstance().pushHistory(new DataChange<>(holder.getParent(), picked, writer));

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
                .on(TopMenuOption.DEBUG, () -> DebuggableScreen.openDebugSelector(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        centerTabs
            .addTab(Component.literal("Preview"), model3DViewer)
            .addTab(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_TAB_EVENT_GRAPH), session.eventGraph());

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
            minecraft.font, this, Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_PANEL_COMPONENTS),
            EntityPatchComponentHolders.REGISTRY, holder,
            RenderUtils.renderStateOf(patchedEntityTypeId()),
            Map::of, true, this::rebuildSocketPreviews, this::onComponentFocused);
        componentPanel.setDropTarget(this::onComponentDropped);
        componentPanel.setRenameListener(this::onComponentRenamed);

        // Variables (bottom-left): a working draft list edited inline (name + type combo per row),
        // under the parent's fields. A picked variable shows its default value in Details
        variableController = new VariableEditController(minecraft.font, variableDrafts, this::onVariableRenamed, this::onVariableRetyped);
        variablePanel = new VariablePanel(minecraft.font, this, variableController);
        variablePanel.setDropTarget(this::onVariableDropped);
        variablePanel.setBlueprintFields(session.layout().fields(), session::dropField);

        // Details (right): the focused component, the picked function's signature, or else the parent's
        // properties
        detailsPanel = new ComponentDetailsPanel(minecraft.font, this, session::onSignaturesEdited);
        detailsPanel.showSlots(session.propertySlots());
        session.setFunctionSelectionHandler(function -> {
            if (function != null) {
                detailsPanel.showComponent(function);
            } else {
                componentPanel.refocusDetails();
            }
        });

        // With no variable picked, Details goes back to the focused component
        Runnable showVariable = () -> {
            VariableDraft selected = variableController.getSelected();

            if (selected == null) {
                componentPanel.refocusDetails();
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

        rebuildSocketPreviews();

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: three columns [left | center | right]; the left column splits top to bottom
        // (Components / Functions / Variables). Divider order (== serialize order): col-left(0),
        // col-right(1), left-split(2, 3).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 22, 76, 40, 65);

        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        var leftSplit = columns.get(0).subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(2), r.get(3));

        leftSplit.get(0).area(componentPanel);
        leftSplit.get(1).area(session.functionPanel());
        leftSplit.get(2).area(variablePanel);
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

    /// The entry id followed by the parent's class, e.g. `epicfight:minecraft/player (PlayerPatch)`.
    private Component menuBarTitle() {
        return Component.literal(resourceKey.identifier() + " (" + holder.getParent().displayLabel() + ")");
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
    // [ComponentPathHost]
    // ***************************************************************

    @Override
    public SequencedMap<String, Class<? extends PatchComponent>> componentPaths() {
        return ComponentPaths.known(holder);
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

        ScriptNodeGraphWidget graph = new ScriptNodeGraphWidget(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        graph.setSuggestionProvider(dataFlowNodeSuggestions::build);
        graph.setScriptOwnerType(ownerType());
        graph.setUsage(ScriptFunction.Usage.SCRIPT);
        graph.setDataFlowOnly(true);
        graph.setSelectionHandler(this::onNodeSelected);

        if (data.isEmpty()) {
            // A canvas whose only context is the patch seeds no entry row: an unwired owner slot reads
            // the patch on its own.
            if (!params.isEmpty()) {
                graph.addNode(new ContextEntryNodeWidget(
                    minecraft.font, Component.literal("Params"), params, RESULT_ENTRY_X, RESULT_ENTRY_Y));
            }

            graph.addNode(new TransitionResultNodeWidget(
                minecraft.font, title, RESULT_ROOT_X, RESULT_ROOT_Y));
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

    /// Selection handler for every canvas on this screen: a single node carrying its own editable
    /// properties takes over the Details panel, any other selection shows the rows
    /// [BlueprintEditorSession#detailsOf] gives it.
    private void onNodeSelected(List<AbstractNodeWidget> selection) {
        if (selection.size() == 1 && selection.getFirst() instanceof MeshLiteralNodeWidget meshLiteral) {
            detailsPanel.showComponent(meshLiteral);
            return;
        }

        if (selection.size() == 1 && selection.getFirst() instanceof ComponentGetterNodeWidget componentGetter) {
            detailsPanel.showComponent(componentGetter);
            return;
        }

        detailsPanel.showSlots(session.detailsOf(selection));
    }

    // ***************************************************************
    // Variable node hooks + drag-drop
    // ***************************************************************
    private void onVariableRenamed(String oldName, String newName) {
        session.renameVariable(oldName, newName);
    }

    private @Nullable WorkHistory onVariableRetyped(String name, ScriptType newType) {
        return session.retypeVariable(name, newType);
    }

    /// A variable released over the Event Graph opens a [VariableDropModal] at the cursor; picking a
    /// row spawns the matching Get / Set node there.
    private boolean onVariableDropped(VariableDraft draft, double screenX, double screenY) {
        if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        String name = draft.getName();
        ScriptType type = draft.getType();

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

        ModalManager.getInstance().openModalWidget(modal, (int) screenX, (int) screenY);
        return true;
    }

    /// [ComponentEditorPanel] rename callback: every node reading the renamed component follows it —
    /// the canvases on screen through their widgets, the rest through the holders storing them.
    private void onComponentRenamed(String fromPath, String toPath) {
        session.canvases().forEach(canvas -> canvas.renameComponentGetters(fromPath, toPath));
        session.mapClosedCanvases(canvas -> ComponentPaths.moved(canvas, fromPath, toPath));

        for (ScriptNodeGraphWidget open : openGraphs.values()) {
            open.renameComponentGetters(fromPath, toPath);
        }

        ComponentPaths.rename(holder, holder.getScript(), fromPath, toPath);
    }

    /// [ComponentEditorPanel] drop callback: a component released over a canvas drops a
    /// [ComponentGetterNodeWidget] reading it, at the cursor.
    private boolean onComponentDropped(String path, double screenX, double screenY) {
        if (!(centerTabs.getActiveContent() instanceof ScriptNodeGraphWidget graph)) return false;
        if (!graph.isPointInside(screenX, screenY)) return false;

        graph.spawnNodeAt((font, x, y) -> new ComponentGetterNodeWidget(font, path, x, y), screenX, screenY);
        return true;
    }

    // ***************************************************************
    // Widget registration / layout
    // ***************************************************************
    @Override
    protected void init() {
        super.init();

        layoutWidgets();

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
        if (ModalManager.getInstance().getModal() == awaitModal) {
            ModalManager.getInstance().closeModal();
        }
        if (error != null) {
            AkytheraLabs.LOGGER.error("Failed to save entity patch '{}'", resourceKey.identifier(), error);
            return;
        }
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Compiles the live canvas into the runtime blueprint in memory only. Marks any invalid node with
    /// a red ERROR banner, aborts on the first failure, and pops a toast with the outcome.
    private void compile() {
        ScriptRecorder.removeFailures(runtimeKey);
        flushToHolder();

        // A source code script lowers nothing from the canvases
        if (holder.getParent() instanceof BlueprintParentRef.SourceCodeScript) {
            CompileResultToast.show(true, null);
            return;
        }

        Component failedPage = validateLiveCanvases();

        if (failedPage != null) {
            AkytheraLabs.LOGGER.warn("Compile of entity patch '{}' failed on page '{}'", resourceKey.identifier(), failedPage.getString());
            CompileResultToast.show(false, failedPage);
            return;
        }

        List<String> unknownPaths = ComponentPaths.unknown(holder, holder.getScript());

        if (!unknownPaths.isEmpty()) {
            AkytheraLabs.LOGGER.warn("Compile of entity patch '{}' aborted — no component at {}", resourceKey.identifier(), unknownPaths);
            CompileResultToast.show(false, Component.literal("No component at " + String.join(", ", unknownPaths)));
            return;
        }

        // Unresolved (failed-decode) nodes are a hard compile error
        List<ScriptGraphData.FailedNode> failedNodes = holder.getScript().failedNodes();

        if (!failedNodes.isEmpty()) {
            for (ScriptGraphData.FailedNode f : failedNodes) {
                AkytheraLabs.LOGGER.warn("Compile of entity patch '{}' aborted — unresolved node (id {}): {}",
                    resourceKey.identifier(), f.id(), f.message());
            }

            CompileResultToast.show(false, Component.literal("Unresolved nodes: " + failedNodes.size()));
            return;
        }

        try {
            EntityPatchBlueprint blueprint = holder.makeObject();
            AkytheraLabs.LOGGER.info("Compiled entity patch '{}': {}", resourceKey.identifier(), blueprint);
            CompileResultToast.show(true, null);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to compile entity patch '{}'", resourceKey.identifier(), e);
            CompileResultToast.show(false, Component.literal(resourceKey.identifier().toString()));
        }
    }

    /// Validates every canvas with widgets on screen — the session's canvases plus each open result /
    /// condition sub-tab — and names the first that failed, or null when all passed.
    /// [ScriptNodeGraphWidget#validate] paints a node's red "ERROR!" banner, so no canvas is skipped
    /// once one has failed.
    private @Nullable Component validateLiveCanvases() {
        Component failedPage = session.validate();

        for (Map.Entry<String, ScriptNodeGraphWidget> open : openGraphs.entrySet()) {
            if (!open.getValue().validate().isEmpty() && failedPage == null) {
                failedPage = Component.literal(open.getKey());
            }
        }

        return failedPage;
    }

    /// Writes the session's canvases and variables and any open result tab back onto the holder.
    /// Components are edited on the holder's own tree and need no flush.
    private void flushToHolder() {
        session.flush();
        graphFlushes.forEach(Runnable::run);
    }

    /// [ComponentEditorPanel] focus callback: shows the component in the Details panel, with `host` as
    /// its rows' context, or the parent's properties when none is focused. A mesh's stand-in is built
    /// from the mesh it names, so a write into one rebuilds the stand-ins once the write lands.
    private void onComponentFocused(
        @Nullable ComponentDataHolder<?> component, @Nullable ComponentAttachableHolder host
    ) {
        Runnable onWrite = component instanceof MeshComponentHolder
            ? () -> minecraft.execute(this::rebuildSocketPreviews)
            : null;

        if (component == null) {
            detailsPanel.showSlots(session.propertySlots());
        } else {
            detailsPanel.showComponent(component, host, null, onWrite);
        }

        focusedArmature = component instanceof ArmatureComponentHolder armature ? armature : null;
        showArmatureView();
        focusPreviewOf(component);
    }

    /// Focuses the preview's stand-in for `component`. An armature or a mesh has none of its own, so a
    /// marker is raised for it while it holds the focus.
    private void focusPreviewOf(@Nullable ComponentDataHolder<?> component) {
        TransformComponentPreviewComponent<?> previousMarker = focusMarker;

        focusMarker = null;
        model3DViewer.focus(switch (component) {
            case TransformSocketHolder socket -> previewOf(socket);
            case ColliderComponentHolder collider -> colliderPreviewOf(collider);
            case EquipmentComponentHolder equipment -> equipmentPreviews.get(equipment);
            case ArmatureComponentHolder armature -> addFocusMarker(armature);
            case MeshComponentHolder mesh -> addFocusMarker(mesh);
            case null, default -> null;
        });

        if (previousMarker != null) model3DViewer.removeComponent(previousMarker);
    }

    /// A gizmo marker for `holder`, at its place in the preview.
    private <H extends ComponentDataHolder<?> & TransformComponentHolder> TransformComponentPreviewComponent<H> addFocusMarker(H holder) {
        TransformComponentPreviewComponent<H> marker = new TransformComponentPreviewComponent<>(
            holder,
            () -> space.parentTransform(holder),
            () -> detailsPanel.showComponent(holder, hostOf(holder)),
            _ -> {},
            null
        );

        model3DViewer.addComponent(marker);
        focusMarker = marker;

        return marker;
    }

    /// Draws [#focusedArmature]'s skeleton over the preview, in the pose the preview model stands in.
    /// Nothing focused, a switched-off armature, or one that resolves no bones leaves the view empty.
    private void showArmatureView() {
        model3DViewer.removeAllComponents(ArmatureViewComponent.class);

        if (focusedArmature == null || !focusedArmature.isEnabled() || focusedArmature.boneNames().isEmpty()) return;

        ArmatureComponentHolder armature = focusedArmature;
        ArmatureViewComponent view = new ArmatureViewComponent(armature.getArmature());

        view.setTransformSource(() -> space.modelTransform(armature));

        view.setPoseSource(() -> {
            EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);
            Animator animator = model == null ? null : model.getAnimator();

            return animator == null ? null : animator.getPose();
        });

        model3DViewer.addComponent(view);
    }

    /// Rebuilds the preview's component stand-ins from the switched-on part of the component tree.
    private void rebuildSocketPreviews() {
        // The stand-ins are replaced wholesale, so the focus is carried across by the holder each one
        // stands for
        ComponentDataHolder<?> refocus = switch (model3DViewer.getFocused()) {
            case TransformComponentPreviewComponent<?> preview -> preview.getHolder();
            case ColliderPreviewComponent preview -> preview.getCollider();
            case ItemModelComponent preview -> equipmentPreviews.entrySet().stream()
                .filter(entry -> entry.getValue() == preview)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
            case null, default -> null;
        };

        model3DViewer.focus(null);
        model3DViewer.removeAllComponents(TransformComponentPreviewComponent.class);
        model3DViewer.removeAllComponents(ItemModelComponent.class);
        model3DViewer.removeAllComponents(ColliderPreviewComponent.class);
        model3DViewer.removeAllComponents(ClothModelComponent.class);
        model3DViewer.removeAllComponents(StaticMeshPreviewComponent.class);

        meshPreviews.forEach(model3DViewer::removeComponent);
        meshPreviews.clear();
        equipmentPreviews.clear();
        focusMarker = null;

        EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);

        if (model == null) return;

        ArmatureComponentHolder armature = ComponentAttachableHolder.flattenEnabled(holder)
            .filter(ArmatureComponentHolder.class::isInstance)
            .map(ArmatureComponentHolder.class::cast)
            .findFirst()
            .orElse(null);

        model.setTransformSource(armature == null ? null : () -> space.modelTransform(armature));

        // The preview reads the authored tree alone: an enabled mesh under an armature is the patch's
        // body, and the vanilla model gives way to it. No script runs here, so nothing the Event Graph
        // does reaches the preview. The mapping's model still carries the armature, the animator and the
        // pose every stand-in reads.
        model.setModelVisible(ComponentAttachableHolder.flattenEnabled(holder)
            .noneMatch(component -> component instanceof MeshComponentHolder mesh
                && hostOf(mesh) instanceof ArmatureComponentHolder));

        ComponentAttachableHolder.flattenEnabled(holder)
            .filter(TransformSocketHolder.class::isInstance)
            .map(TransformSocketHolder.class::cast)
            .forEach(this::addSocketPreview);

        ComponentAttachableHolder.flattenEnabled(holder)
            .filter(ColliderComponentHolder.class::isInstance)
            .map(ColliderComponentHolder.class::cast)
            .forEach(collider -> model3DViewer.addComponent(ColliderPreviewComponent.editable(
                collider,
                () -> space.parentTransform(collider),
                () -> detailsPanel.showComponent(collider, hostOf(collider)),
                componentPanel::focusListedComponent)));

        ComponentAttachableHolder.flattenEnabled(holder)
            .filter(EquipmentComponentHolder.class::isInstance)
            .map(EquipmentComponentHolder.class::cast)
            .forEach(equipment -> {
                ItemModelComponent itemModel = new ItemModelComponent(equipment::getPreviewItem);

                itemModel.setDisplayContextSource(equipment::getItemDisplayContext);
                itemModel.placeUnder(
                    () -> space.parentTransform(equipment),
                    equipment.getLocalTransform(),
                    equipment::getTransformInheritPolicy,
                    () -> detailsPanel.showComponent(equipment, hostOf(equipment)));

                equipmentPreviews.put(equipment, itemModel);
                model3DViewer.addComponent(itemModel);
            });

        ComponentAttachableHolder.flattenEnabled(holder)
            .filter(MeshComponentHolder.class::isInstance)
            .map(MeshComponentHolder.class::cast)
            .forEach(mesh -> addMeshPreview(mesh, model));

        focusPreviewOf(refocus);
        showArmatureView();
    }

    /// The wireframe standing for `collider`, or null when the preview holds none.
    private @Nullable ColliderPreviewComponent colliderPreviewOf(ColliderComponentHolder collider) {
        for (int i = 0; ; i++) {
            ColliderPreviewComponent preview = model3DViewer.getComponent(ColliderPreviewComponent.class, i);

            if (preview == null) return null;
            if (preview.getCollider() == collider) return preview;
        }
    }

    /// One socket's marker.
    private void addSocketPreview(TransformSocketHolder socket) {
        model3DViewer.addComponent(new TransformComponentPreviewComponent<>(
            socket,
            () -> space.parentTransform(socket),
            () -> detailsPanel.showComponent(socket, hostOf(socket)),
            componentPanel::focusComponent,
            this::openSocketOptions
        ));
    }

    /// The marker standing for `socket`, or null when the preview has no model to hang it off.
    private @Nullable TransformComponentPreviewComponent<?> previewOf(TransformSocketHolder socket) {
        for (int i = 0; ; i++) {
            TransformComponentPreviewComponent<?> preview = model3DViewer.getComponent(TransformComponentPreviewComponent.class, i);

            if (preview == null) return null;
            if (preview.getHolder() == socket) return preview;
        }
    }

    /// One mesh component's mesh: simulated where it carries cloth, skinned where it does not, and drawn
    /// under its socket where it is static. A canvas naming no mesh raises nothing, and neither does a
    /// skeletal one with no armature to pose it.
    @SuppressWarnings("unchecked")
    private void addMeshPreview(MeshComponentHolder meshComponent, EntityModelComponent model) {
        AssetAccessor<?> named = meshComponent.getMesh();

        if (named == null) return;

        if (named.checkType(AssetAccessor.Type.STATIC_MESH)) {
            addStaticMeshPreview(meshComponent, (AssetAccessor<? extends StaticMesh>) named);
            return;
        }

        AssetAccessor<? extends Armature> armature = model.getArmature();
        AssetAccessor<? extends SkeletalMesh> mesh = (AssetAccessor<? extends SkeletalMesh>) named;

        if (armature == null) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();
        // The body's animator is advanced by its own component; a preview only reads the pose out of it
        Supplier<@Nullable Pose> poseSource = () -> {
            Animator animator = model.getAnimator();

            return animator == null ? null : animator.getPose();
        };

        if (!project.hasClothFile(mesh)) {
            EntityModelComponent preview = new EntityModelComponent(mesh, armature);

            preview.setPoseSource(poseSource);
            preview.setTransformSource(() -> space.modelTransform(meshComponent));
            preview.setPreviewTexture(() -> textureOf(mesh));

            meshPreviews.add(preview);
            model3DViewer.addComponent(preview);

            return;
        }

        ClothPhysicsDataHolder cloth = project.getClothPhysicsHolder(mesh);
        ClothModelComponent preview = new ClothModelComponent(
            mesh, armature, cloth::getWeights, cloth::getOffsetPairs, cloth.getParameters()::makeObject
        );

        preview.setArmaturePhysics(() -> {
            AssetAccessor<? extends ArmaturePhysicsData> bodies = cloth.getArmaturePhysics();

            return bodies.identifier().equals(DataHolder.EMPTY_REFERENCE)
                ? null
                : bodies.getOrHandleException(AkytheraLabs.LOGGER::error);
        });
        preview.setPoseSource(poseSource);

        model3DViewer.addComponent(preview);
    }

    /// A static mesh, drawn where `MeshComponent` draws one at runtime: at its own transform inside its host's space.
    private void addStaticMeshPreview(MeshComponentHolder meshComponent, AssetAccessor<? extends StaticMesh> mesh) {
        StaticMeshPreviewComponent preview = new StaticMeshPreviewComponent(mesh, () -> space.modelTransform(meshComponent));

        preview.setTexture(() -> textureOf(mesh));

        model3DViewer.addComponent(preview);
    }

    /// The texture a previewed mesh draws with: its own sidecar's, or none, which draws the missing
    /// texture. The render type getter names one too, but it is a script over a live patch.
    private static @Nullable Identifier textureOf(AssetAccessor<? extends Mesh<?>> mesh) {
        Mesh<?> resolved = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);

        return resolved == null ? null : resolved.getMetadata().texture();
    }


    /// The component `component` is attached under.
    private @Nullable ComponentAttachableHolder hostOf(ComponentDataHolder<?> component) {
        return ComponentAttachableHolder.hostOf(holder, component);
    }

    /// Right-press on a focused socket in the preview.
    private void openSocketOptions(TransformSocketHolder socket) {
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

        ModalManager.getInstance().openModalWidget(
            menu,
            (int) Math.round(minecraft.mouseHandler.xpos() / scale),
            (int) Math.round(minecraft.mouseHandler.ypos() / scale));
    }

    private void resetSocketTransform(TransformSocketHolder socket) {
        Consumer<Transform> writer = transformWriterFor(socket);

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            socket.getLocalTransform().makeObject(), Transform.IDENTITY, writer));

        writer.accept(Transform.IDENTITY);
    }

    /// Writes a transform into `socket` and refreshes the Details rows showing it.
    private Consumer<Transform> transformWriterFor(TransformSocketHolder socket) {
        return transform -> {
            socket.getLocalTransform().readFrom(transform);
            detailsPanel.showComponent(socket, hostOf(socket));
        };
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // [DebuggableScreen]
    // ***************************************************************

    /// Entities whose patch runs *this* blueprint.
    @Override
    public Predicate<Entity> debugTargetFilter() {
        return entity -> {
            EntityPatch<?> patch = ((EntityAccessor)entity).akythera$getEntityPatch();

            return patch != null
                && patch.getScript() instanceof DataDrivenEntityPatchScript script
                && script.getBlueprint().key().equals(runtimeKey);
        };
    }

    /// A patch script runs on both sides.
    @Override
    public boolean mirrorsServer() {
        return true;
    }

    /// Mirrors the possessed entity's recorded runs, from the side the debug modal mirrors, onto the
    /// canvases every tick. The events share the Event Graph, so their last records are shown together; an
    /// open function or component tab shows its own last run.
    @Override
    public void onDebugTick(final EntityPatch<?> entityPatch) {
        Map<String, ExecutionRecord> records = EntityDebugSession.getInstance().records(runtimeKey);

        session.showDebugRecords(records);

        Map<ScriptNodeGraphWidget, ExecutionRecord> slotRecords = new HashMap<>();

        records.forEach((site, record) -> {
            ScriptNodeGraphWidget canvas = slotCanvas(site);

            if (canvas != null) slotRecords.put(canvas, record);
        });

        for (ScriptNodeGraphWidget open : openGraphs.values()) {
            ExecutionRecord record = slotRecords.get(open);

            if (record == null) {
                open.clearDebugRecord();
            } else {
                open.setDebugRecord(record);
            }
        }
    }

    /// The component the slot `site` names (`<path>#<slot>`) sits on, or null when `site` is no slot or
    /// no component sits at its path.
    private @Nullable ComponentDataHolder<?> slotComponent(String site) {
        int split = site.lastIndexOf(ScriptCaller.SLOT_SEPARATOR);

        return split < 0 ? null : ComponentPaths.at(holder, site.substring(0, split));
    }

    /// The open tab drawing the component slot `site` names, or null when `site` is no slot or its tab
    /// is closed.
    private @Nullable ScriptNodeGraphWidget slotCanvas(String site) {
        ComponentDataHolder<?> component = slotComponent(site);

        if (component == null) return null;

        String slot = site.substring(site.lastIndexOf(ScriptCaller.SLOT_SEPARATOR) + ScriptCaller.SLOT_SEPARATOR.length());

        return openGraphs.get(EditorWidgetFactory.nodeGraphTabKey(component, slot));
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
        // A finished session must not leave the canvases frozen on their last capture
        if (!EntityDebugSession.getInstance().isActive()) {
            session.canvases().forEach(ScriptNodeGraphWidget::clearDebugRecord);
            openGraphs.values().forEach(ScriptNodeGraphWidget::clearDebugRecord);
        }

        Map<String, ScriptFailure> failures = ScriptRecorder.failures(runtimeKey);

        // Re-marked every frame while failures stand, so the marks follow edits that renumber the rows
        // and reach component tabs opened after the failure
        if (!failures.isEmpty() || failuresMarked) {
            failuresMarked = !failures.isEmpty();

            Map<ComponentDataHolder<?>, Throwable> componentErrors = new IdentityHashMap<>();

            session.canvases().forEach(ScriptNodeGraphWidget::clearRuntimeErrors);
            openGraphs.values().forEach(ScriptNodeGraphWidget::clearRuntimeErrors);

            failures.forEach((site, failure) -> {
                if (!site.contains(ScriptCaller.SLOT_SEPARATOR)) {
                    session.markRuntimeError(site, failure);
                    return;
                }

                // The component row is flagged too, so a failure in a closed tab can be found
                ComponentDataHolder<?> component = slotComponent(site);

                if (component != null) componentErrors.putIfAbsent(component, failure.cause());

                ScriptNodeGraphWidget canvas = slotCanvas(site);

                if (canvas != null) canvas.markRuntimeError(failure);
            });

            componentPanel.setRuntimeErrors(componentErrors);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so a floating modal
    /// such as the debug viewport leaves the canvases their drags.
    @Override
    public @Nullable GuiEventListener getFocused() {
        var modal = getFocusOwningModal();
        if (modal != null) return modal;
        return super.getFocused();
    }
}
