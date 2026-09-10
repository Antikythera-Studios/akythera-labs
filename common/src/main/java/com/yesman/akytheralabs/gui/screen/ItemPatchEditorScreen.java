package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.BoneSocketHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityModelComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EquipmentComponentHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.script.*;
import com.yesman.akytheralabs.gui.widget.viewer.ColliderPreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ItemModelComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Editor screen for an [ItemPatchBlueprintHolder] (an [ItemPatchBlueprint]): two side columns —
/// Components (left), Details (right) — around a center Preview / graph [TabPanel].
///
/// Item patches have no flows or variables, so no Event Graph is seeded; graph tabs open per component
/// property holding a canvas ([#openResultGraphTab]). With no component focused, the Details panel
/// shows the blueprint's own fields (the parent reference).
public final class ItemPatchEditorScreen extends Screen
    implements ModalAccessibleScreen, SafeCloseScreen, NodeGraphTabHost, AnimationPreviewHost
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

    /// The project entry path a player entity patch lives at.
    private static final String PLAYER_PATCH_PATH = "minecraft/player";

    /// Width of the preview warning popup.
    private static final int WARNING_POPUP_W = 300;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final TabPanel centerTabs;

    private final ComponentEditorPanel componentPanel;
    private final ComponentDetailsPanel detailsPanel;

    /// Open pure-node graph tabs by property key, plus their flush-back hooks (run on tab close and
    /// on save).
    private final Map<String, ScriptNodeGraphWidget> openGraphs = new HashMap<>();
    private final List<Runnable> graphFlushes = new ArrayList<>();

    private final ItemPatchBlueprintHolder holder;
    private final ResourceKey<ItemPatchBlueprint> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live
    /// top differs from it.
    private @Nullable WorkHistory savePoint;

    /// The space this patch's colliders sit in: the socket the preview item is drawn at, which is the
    /// transform `ColliderDebugRenderer` places them by in game. Null while the preview raised its
    /// warning card instead.
    private @Nullable Supplier<Transform> colliderPlacement;

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
                .on(TopMenuOption.SELECT_PREVIEW_ITEM, this::isMetaPatch, this::openPreviewItemSelectModal)
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        // Preview: the camera, the background and the grid floor only
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
        buildPreview();

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

        // Components (left): edits the blueprint's component tree in place, the patch holder being
        // the tree's root host
        componentPanel = new ComponentEditorPanel(
            minecraft.font, this, ItemPatchComponentHolders.REGISTRY, holder, null,
            holder::inheritedComponents,
            this::rebuildColliderPreviews, this::onComponentFocused);

        rebuildColliderPreviews();

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this, this::onDetailsChanged);
        // Nothing focused yet: the blueprint's own fields
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
    // Preview
    // ***************************************************************

    /// Installs the preview: the player model the project's `minecraft:player` entity patch describes,
    /// with this patch's item at the socket that patch equips the main hand from. Whatever that patch
    /// does not define raises the warning card instead.
    ///
    /// The mesh is the entity model component's default one: its getter is a tree over the patch's
    /// owning entity, and an editor preview has none.
    private void buildPreview() {
        EntityPatchBlueprintHolder patch = AkytheraProject.getCurrentProject()
            .<EntityPatchBlueprint, EntityPatchBlueprintHolder>getDataHolder(playerPatchKey())
            .orElse(null);

        if (patch == null) {
            showPreviewWarning(
                Component.translatable(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_MISSING_PATCH),
                Component.translatable(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_CREATE_PATCH),
                this::createPlayerPatch);
            return;
        }

        ArmatureComponentHolder armature = rootComponent(patch, ArmatureComponentHolder.class);

        if (armature == null) {
            showMissingComponent(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_COMPONENT_ARMATURE);
            return;
        }

        EntityModelComponentHolder entityModel = rootComponent(patch, EntityModelComponentHolder.class);

        if (entityModel == null) {
            showMissingComponent(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_COMPONENT_ENTITY_MODEL);
            return;
        }

        AssetAccessor<? extends Mesh<?>> mesh = entityModel.getDefaultMesh();

        if (mesh == null || !mesh.checkType(AssetAccessor.Type.SKELETAL_MESH)) {
            showMissingComponent(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_COMPONENT_DEFAULT_MESH);
            return;
        }

        BoneSocketHolder socket = null;
        EquipmentComponentHolder equipment = null;

        for (ComponentDataHolder<?> attached : armature.getComponents().values()) {
            if (!(attached instanceof BoneSocketHolder candidate)) continue;

            equipment = candidate.getComponents().values().stream()
                .filter(EquipmentComponentHolder.class::isInstance)
                .map(EquipmentComponentHolder.class::cast)
                .filter(e -> e.getEquipmentSlot() == EquipmentSlot.MAINHAND)
                .findFirst()
                .orElse(null);

            if (equipment != null) {
                socket = candidate;
                break;
            }
        }

        if (socket == null || equipment == null) {
            showMissingComponent(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_COMPONENT_EQUIPMENT_SOCKET);
            return;
        }

        @SuppressWarnings("unchecked")
        EntityModelComponent model = new EntityModelComponent(
            (AssetAccessor<SkeletalMesh>) mesh, armature.getArmature());

        // A non-null animator is what makes the component rewrite its bone transforms each frame, and
        // the headless one holds the bind pose.
        model.setSimplePlayingAnimation(null);
        model3DViewer.addComponent(model);
        model3DViewer.frameCamera(
            Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.width(),
            Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.height());

        BoneSocketHolder placement = socket;
        EquipmentComponentHolder displayContext = equipment;

        ItemModelComponent itemModel = new ItemModelComponent(this::previewItem);
        itemModel.setDisplayContextSource(displayContext::getItemDisplayContext);
        itemModel.placeUnder(
            () -> model.getArmatureComponent().getBoneModelTransform(placement.getBoneName()),
            placement.getLocalTransform(),
            null);

        model3DViewer.addComponent(itemModel);

        // bone x socket offset. The item's own `display` transform is no part of where a collider
        // sits, and `placeUnder` keeps it out of this one.
        colliderPlacement = itemModel::worldTransform;
    }

    /// Rebuilds the preview's collider wireframes from the components the panel lists, keeping the
    /// wireframe that holds the focus. One this patch declares is draggable; one it inherits is drawn
    /// dim until a pick claims it.
    private void rebuildColliderPreviews() {
        rebuildColliderPreviews(model3DViewer.getFocused() instanceof ColliderPreviewComponent preview
            ? preview.getCollider()
            : null);
    }

    /// [#rebuildColliderPreviews], ending focused on `focus`'s wireframe — on nothing when it is null.
    ///
    /// A focus change supplies the component it is moving to, and runs this because a pick on a dim
    /// wireframe claims the component: the claim's copy is what the panel lists from then on. It
    /// cannot keep the old focus instead — a wireframe taking the focus reports itself to the
    /// Components tree, which would pull the selection back off the component being focused here.
    private void rebuildColliderPreviews(@Nullable ColliderComponentHolder focus) {
        model3DViewer.focus(null);
        model3DViewer.removeAllComponents(ColliderPreviewComponent.class);

        Supplier<Transform> placement = colliderPlacement;

        if (placement == null) return;

        componentPanel.listedComponents().forEach(listed -> {
            if (!(listed.holder() instanceof ColliderComponentHolder collider)) return;

            model3DViewer.addComponent(listed.inherited()
                ? ColliderPreviewComponent.inherited(collider, placement, this::onColliderPicked)
                : ColliderPreviewComponent.editable(
                    collider, placement, this::onColliderDragCommitted, this::onColliderPicked));
        });

        if (focus != null) model3DViewer.focus(previewOf(focus));
    }

    /// Moves the Components tree onto a picked wireframe, claiming an inherited collider the way
    /// clicking its row does.
    ///
    /// Deferred a tick: the claim rebuilds the previews, and the pick is still running inside the one
    /// being replaced.
    private void onColliderPicked(ColliderComponentHolder collider) {
        minecraft.execute(() -> componentPanel.focusListedComponent(collider));
    }

    /// Takes a claimed collider into the patch and re-reads the Details rows, once a drag is
    /// released. The drag wrote into the claim's copy, which no patch held until here.
    ///
    /// Deferred a tick: attaching rebuilds the previews, and the release is still running inside the
    /// one being replaced.
    private void onColliderDragCommitted() {
        minecraft.execute(() -> {
            componentPanel.onDetailsWrite();
            componentPanel.refocusDetails();
        });
    }

    /// The wireframe standing for `collider`, or null when the preview holds none.
    private @Nullable ColliderPreviewComponent previewOf(ColliderComponentHolder collider) {
        for (int i = 0; ; i++) {
            ColliderPreviewComponent preview = model3DViewer.getComponent(ColliderPreviewComponent.class, i);

            if (preview == null) return null;
            if (preview.getCollider() == collider) return preview;
        }
    }

    /// [AnimationPreviewHost]: plays `clip` on the preview model and brings the Preview tab forward,
    /// so pressing an animation row in the Details panel shows it straight away. No-op before the
    /// preview is built — a patch whose player entity patch is missing has no model to pose.
    @Override
    public void previewAnimation(AssetAccessor<AnimationClip> clip) {
        EntityModelComponent entityModel = model3DViewer.getComponent(EntityModelComponent.class);
        if (entityModel == null) return;

        centerTabs.setActiveIndex(centerTabs.indexOfContent(model3DViewer));
        entityModel.setSimplePlayingAnimation(clip, true);
    }

    /// The first component of `type` attached directly on `patch`.
    private static <C extends ComponentDataHolder<?>> @Nullable C rootComponent(
        EntityPatchBlueprintHolder patch, Class<C> type
    ) {
        return patch.getComponents().values().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElse(null);
    }

    /// The item this patch describes: the entry id's `<itemNs>/<itemPath>` path read back as an item
    /// id.
    private Item patchedItem() {
        String path = resourceKey.identifier().getPath();
        int split = path.indexOf('/');

        return split < 0
            ? Items.AIR
            : BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(path.substring(0, split), path.substring(split + 1)));
    }

    /// Whether this patch targets no item of its own — a **meta** patch, reached only through a child
    /// patch's parent reference. Its entry id names the project instead of an item, so
    /// [#patchedItem] resolves to [Items#AIR].
    private boolean isMetaPatch() {
        return patchedItem() == Items.AIR;
    }

    /// The item the preview draws: the patched item, or the holder's stand-in for a meta patch.
    /// [Items#AIR] draws nothing.
    private Item previewItem() {
        Item patched = patchedItem();

        return patched == Items.AIR ? ParseUtils.nvl(holder.getPreviewItem(), Items.AIR) : patched;
    }

    /// `View > Select Preview Item`, offered on a meta patch only. The pick lands on the undo stack:
    /// it writes a holder field, so the title's `*` follows it like any other edit.
    private void openPreviewItemSelectModal() {
        ItemSelectModal modal = new ItemSelectModal(minecraft.font, item -> {
            AkytheraEditorLayoutManager.getInstance().closeModal();

            Item previous = holder.getPreviewItem();
            if (item == previous) return;

            holder.setPreviewItem(item);
            HistoryManager.getInstance().pushHistory(new DataChange<>(previous, item, holder::setPreviewItem));
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    private static ResourceKey<EntityPatchBlueprint> playerPatchKey() {
        return ResourceKey.create(
            AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
            Identifier.fromNamespaceAndPath(AkytheraProject.getCurrentProject().getProjectName(), PLAYER_PATCH_PATH));
    }

    /// The warning for one piece the player's entity patch does not define, pointing at that patch's
    /// own editor.
    private void showMissingComponent(String componentKey) {
        showPreviewWarning(
            Component.translatable(
                LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_MISSING_COMPONENT, Component.translatable(componentKey)),
            Component.translatable(LangKeys.GUI_ITEM_PATCH_SCREEN_PREVIEW_OPEN_PATCH),
            () -> {
                ResourceKey<EntityPatchBlueprint> key = playerPatchKey();

                openPlayerPatch(key, AkytheraProject.getCurrentProject().getDataHolderOrThrow(key));
            });
    }

    /// Leaves this screen for the player entity patch's editor, asking to save first. Both answers
    /// land on the patch editor; cancelling keeps this screen.
    ///
    /// Deferred a tick. The press is still dispatching through the viewer's overlay widget list, and
    /// [#onClose] destroys the viewer that owns it — which is the path taken with nothing to save, where
    /// the prompt is skipped and the quit runs straight away.
    private void openPlayerPatch(ResourceKey<EntityPatchBlueprint> key, EntityPatchBlueprintHolder holder) {
        minecraft.execute(() -> askToSave(() -> {
            onClose();
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new EntityPatchEditorScreen(key, holder));
        }));
    }

    /// Raises the warning: the reason over a Cancel and the one button acting on it. Cancel leaves the
    /// empty preview standing.
    ///
    /// Deferred a tick. This runs from the constructor, and
    /// [AkytheraEditorLayoutManager#openNewScreen] closes every open modal before pushing the screen it
    /// was called for.
    private void showPreviewWarning(Component reason, Component action, Runnable onAction) {
        minecraft.execute(() -> {
            int buttonWidth = WidgetContainerModal.evenButtonWidth(WARNING_POPUP_W, 2);

            WidgetContainerModal popup = WidgetContainerModal
                .builder(minecraft.font, Component.empty(), WARNING_POPUP_W)
                .textLines(reason)
                .footerButton(
                    AnchoredButton.buttonBuilder(
                            minecraft.font,
                            Component.translatable(LangKeys.GUI_CANCEL),
                            _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .footerButton(
                    AnchoredButton.buttonBuilder(
                            minecraft.font,
                            action,
                            // Closed first: the prompt the action raises refuses to open over a modal.
                            _ -> {
                                AkytheraEditorLayoutManager.getInstance().closeModal();
                                onAction.run();
                            })
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .build();

            AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
        });
    }

    /// Creates the project's entity patch for the player and opens it.
    private void createPlayerPatch() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        ResourceKey<EntityPatchBlueprint> key = project.createEntry(
            AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, PLAYER_PATCH_PATH, true);

        EntityPatchBlueprintHolder holder = project.getDataHolderOrThrow(key);
        holder.setPreviewModel(new EntityPreviewModel(
            BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.PLAYER),
            EntityModelComponent.ModelType.VANILLA_ENTITY));
        project.saveEntry(key);

        openPlayerPatch(key, holder);
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

    /// Pure-only suggestions: literals, pure SCRIPT functions and the Math / Logic nodes.
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
            // Comparison functions are surfaced as the type-detecting comparator nodes below
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

        // Math / Logic nodes: the output must fit the target slot and the dragged value, if any,
        // must be a valid operand
        Class<?> providedType = context.providedType();

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)) {
            for (ComparatorNodeWidget.Operator op : ComparatorNodeWidget.Operator.values()) {
                // Ordering runs on the numeric comparator; equality runs on any operand.
                if (op != ComparatorNodeWidget.Operator.EQUAL
                    && !AbstractNodeWidget.valueAssignable(providedType, double.class)) continue;

                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()),
                    (font, x, y) -> new ComparatorNodeWidget(font, op, x, y),
                    op == ComparatorNodeWidget.Operator.EQUAL ? "Logic" : "Math"));
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

    /// Builds the runtime blueprint in memory only, surfacing dangling references and bad modifier ids
    /// before export.
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
    /// returns whether any failed. [ScriptNodeGraphWidget#validate] paints a node's red "ERROR!"
    /// banner, so no canvas is skipped once one has failed.
    private boolean validateLiveCanvases() {
        boolean failed = false;

        for (ScriptNodeGraphWidget open : openGraphs.values()) {
            if (!open.validate().isEmpty()) failed = true;
        }

        return failed;
    }

    /// Flushes any open graph tabs. Components and the parent reference are edited on the holder in
    /// place and need no flush. An override that matches what it overrides is dropped here rather than
    /// written out.
    private void flushToHolder() {
        graphFlushes.forEach(Runnable::run);
        componentPanel.dropUnchangedOverrides();
    }

    /// [ComponentEditorPanel] focus callback: shows the component in the Details panel, with `host` as
    /// its rows' context and whatever it overrides behind it. A component the patch does not hold yet
    /// carries the hook that attaches it, see [ComponentEditorPanel#onDetailsWrite]. Nothing focused
    /// shows the blueprint's own fields.
    private void onComponentFocused(
        @Nullable ComponentDataHolder<?> component, @Nullable ComponentAttachableHolder host
    ) {
        if (component == null) {
            detailsPanel.showComponent(holder);
        } else {
            detailsPanel.showComponent(
                component, host, componentPanel.inheritedBehind(component), componentPanel::onDetailsWrite);
        }

        // A focus on an inherited row is a focus on the claim's copy, which the panel lists from here
        // on, so the wireframes are re-read before this focus lands on one of them.
        rebuildColliderPreviews(component instanceof ColliderComponentHolder collider ? collider : null);
    }

    /// The Details panel re-rendered itself: an override that matches what it overrides again is no
    /// override.
    private void onDetailsChanged() {
        componentPanel.dropUnchangedOverrides();
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // Screen lifecycle / input
    // ***************************************************************
    @Override
    public void handleQuitRequest() {
        askToSave(this::onClose);
    }

    /// The ask-to-save prompt, then `after` on either answer — saved, or reverted to what is on disk.
    /// Cancelling keeps the screen and runs nothing.
    private void askToSave(Runnable after) {
        SafeCloseScreen.openQuiteScreen(
            minecraft.font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                AkytheraEditorLayoutManager.getInstance().closeModal();
                save().thenRun(after);
            },
            () -> {
                if (hasUnsavedChanges()) AkytheraProject.getCurrentProject().reloadEntryFromDisk(resourceKey);
                after.run();
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
