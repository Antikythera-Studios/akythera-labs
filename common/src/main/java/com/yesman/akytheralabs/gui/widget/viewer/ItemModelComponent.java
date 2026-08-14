package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// An item model drawn in the previewer, placed by an armature socket ([#boundToEntityModelSocket]),
/// by an absolute transform ([#placeInWorld]), or by an offset inside a live parent space
/// ([#placeUnder]) — the last of which a gizmo can drag, since the offset is an editor holder's own
/// [TransformHolder].
public final class ItemModelComponent implements LocatableComponent {
    /// Item models never ask for an entity outline in the previewer.
    private static final int NO_OUTLINE = 0;

    /// A socket to get render location from [EntityModelComponent].
    /// In the other words, [EntityModelComponent] must be registered
    /// in [Model3DViewer].
    private String socketName;

    private int modelIndex = -1;

    /// The space [#localTransform] sits in, resolved per frame — [Transform#IDENTITY] for an item
    /// placed straight in the world.
    private Supplier<Transform> parentTransform = () -> Transform.IDENTITY;

    /// The offset inside [#parentTransform] this item is drawn at. A live editor value in the
    /// [#placeUnder] mode, which is what a gizmo drag writes into.
    private TransformHolder localTransform = TransformHolder.identity();

    /// Runs when a gizmo drag on [#localTransform] is released. Null while nothing edits this item.
    private @Nullable Runnable onTransformCommitted;

    /// An item to render, read per frame so an editor pick shows on the next one.
    private Supplier<Item> item;

    /// Decides both the model variant that gets picked (a trident is a flat sprite in
    /// [ItemDisplayContext#NONE] but a real 3D model in hand) and the built-in `display`
    /// transform folded in on top of the placement transform. Read per frame, like [#item].
    private Supplier<ItemDisplayContext> displayContext = () -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

    /// Set while a gizmo drag on [#localTransform] is in flight.
    private boolean dragging;

    /// Resolved once per frame from [#item], and reused rather than reallocated.
    private final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    /// Backing stack for [#item], rebuilt only when the item changes.
    @Nullable
    private ItemStack itemStack;

    private final SubmitNodeStorage submitNodes = new SubmitNodeStorage();

    public ItemModelComponent(final String socketName, final Item item) {
        this(() -> item);

        this.socketName = socketName;
    }

    /// An item placed by [#placeInWorld] or [#placeUnder] instead of riding an armature socket. The
    /// item is read per frame, so an editor value it comes off follows its edits.
    public ItemModelComponent(final Supplier<Item> item) {
        this.item = item;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        if (item.get() == null) return;

        Transform placement = resolvePlacement(model3DViewer);

        // Either the socket isn't resolvable yet, or nothing told us where to put the item.
        if (placement == null) return;

        ItemStackRenderState renderState = resolveRenderState();

        if (renderState.isEmpty()) return;

        poseStack.pushPose();

        // Item models are authored in the viewer's base space (+Y up, one unit per block), which
        // the socket / world transform is already in, so it goes on unmodified.
        poseStack.mulPose(placement.toMatrix());

        drawItem(renderState, poseStack);

        poseStack.popPose();
    }

    /// Where the item goes this frame: the bound armature socket if one is set and its owning
    /// [EntityModelComponent] is present, otherwise [LocatableComponent#worldTransform] — the
    /// offset in whatever space [#parentTransform] resolves to.
    private @Nullable Transform resolvePlacement(final Model3DViewer model3DViewer) {
        if (!StringUtil.isNullOrEmpty(socketName) && modelIndex > -1) {
            EntityModelComponent entityModelComponent = model3DViewer.getComponent(
                EntityModelComponent.class, modelIndex
            );

            return entityModelComponent == null
                ? null
                : entityModelComponent.getArmatureComponent().getSocketBoundTransform(socketName);
        }

        return worldTransform();
    }

    /// Re-resolves the item's model layers. Done every frame because item models are
    /// state-driven (conditional / range-select models, animated layers).
    private ItemStackRenderState resolveRenderState() {
        Item item = this.item.get();

        if (itemStack == null || !itemStack.is(item)) {
            itemStack = new ItemStack(item);
        }

        // No level and no owner: the previewer has neither, and the conditional model
        // properties treat both as optional.
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
            itemRenderState, itemStack, displayContext.get(), null, null, 0
        );

        return itemRenderState;
    }

    /// Runs a one-item version of a frame: collects the layers into our own submit storage, then
    /// hands the whole storage to the shared `FeatureRenderDispatcher`, which drains it and draws
    /// into whatever render target [Model3DViewer] redirected the output overrides to. Going through
    /// the real submit path (rather than walking [ItemStackRenderState]'s quads by hand) is what
    /// keeps tints, enchantment foil and `SpecialModelRenderer` items — shields, tridents, chests,
    /// banners — rendering correctly.
    private void drawItem(final ItemStackRenderState renderState, final PoseStack poseStack) {
        renderState.submit(poseStack, submitNodes, Model3DViewer.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, NO_OUTLINE);

        Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(submitNodes);
    }

    public void boundToEntityModelSocket(final String socketName, final int modelIndex) {
        this.socketName = socketName;
        this.modelIndex = modelIndex;

        parentTransform = () -> Transform.IDENTITY;
        localTransform = TransformHolder.identity();
        onTransformCommitted = null;
    }

    public void placeInWorld(final Transform worldTransform) {
        placeUnder(() -> Transform.IDENTITY, TransformHolder.of(worldTransform), null);
    }

    /// Places the item at `localTransform` inside `parentTransform`, both read per frame. Passing an
    /// `onTransformCommitted` makes the placement gizmo-editable: a drag writes into `localTransform`
    /// — an editor holder's own — and the callback runs when it is released.
    public void placeUnder(
        final Supplier<Transform> parentTransform,
        final TransformHolder localTransform,
        final @Nullable Runnable onTransformCommitted
    ) {
        this.parentTransform = parentTransform;
        this.localTransform = localTransform;
        this.onTransformCommitted = onTransformCommitted;

        socketName = null;
        modelIndex = -1;
    }

    public void setItem(final Item item) {
        this.item = () -> item;
    }

    public void setItemSource(final Supplier<Item> item) {
        this.item = item;
    }

    public Item getItem() {
        return item.get();
    }

    /// Defaults to [ItemDisplayContext#THIRD_PERSON_RIGHT_HAND]. Set
    /// [ItemDisplayContext#THIRD_PERSON_LEFT_HAND] for an off-hand socket, or
    /// [ItemDisplayContext#NONE] to place the raw model with no built-in `display` transform.
    public void setDisplayContext(final ItemDisplayContext displayContext) {
        this.displayContext = () -> displayContext;
    }

    public void setDisplayContextSource(final Supplier<ItemDisplayContext> displayContext) {
        this.displayContext = displayContext;
    }

    /// ***************************************************************
    /// [LocatableComponent]
    /// ***************************************************************

    @Override
    public Transform parentTransform() {
        return parentTransform.get();
    }

    @Override
    public Transform localTransform() {
        return localTransform.makeObject();
    }

    /// Ignored while the placement carries no [#onTransformCommitted] — nothing owns the transform
    /// this would write.
    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (onTransformCommitted == null) return;

        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(
                    new PendingDataChange<>(localTransform, localTransform(), this::writeTransform));
            }

            writeTransform(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(
            new DraggingDataChange<>(localTransform, transform, this::writeTransform));
        writeTransform(transform);
        onTransformCommitted.run();
    }

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        localTransform.readFrom(transform);
    }

    @Override
    public int drawOrder() {
        return 15; // after grid (1) and model (10), before trajectory (20)
    }
}