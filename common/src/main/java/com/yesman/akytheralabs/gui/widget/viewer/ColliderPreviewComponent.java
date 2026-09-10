package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.gui.widget.viewer.ColliderViewerComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.gui.widget.viewer.Ray;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// The wireframe of one [ColliderComponentHolder], drawn in the space its host places it — the bone
/// socket's bound transform, which is what `ColliderDebugRenderer` draws by in game.
///
/// The shape is rebuilt from the holder every frame, so the Details rows and the wireframe never
/// disagree.
///
/// Two forms, see [#editable] and [#inherited]. Both are pickable; only an [#editable] one takes a
/// drag, because a drag writes into the holder directly and an inherited holder belongs to an
/// ancestor patch. Picking an inherited one is what claims it — see
/// [ComponentEditorPanel#focusListedComponent] — after which it is rebuilt as an [#editable] one over
/// the claim's copy.
public final class ColliderPreviewComponent implements LocatableComponent {
    private static final int COLOR = 0xFF33FF66;
    private static final int COLOR_FOCUSED = 0xFF99FFBB;
    private static final int COLOR_INHERITED = 0xFF1F7A3D;

    /// How far a picking ray is walked, in viewer world units.
    private static final float PICK_REACH = 64.0F;

    private final ColliderComponentHolder collider;
    private final Supplier<Transform> parentTransform;

    /// The engine's drawing side, fed this component's two live suppliers.
    private final ColliderViewerComponent wireframe;

    /// The colour this wireframe carries while unfocused.
    private final int restingColor;

    /// Runs once a drag is released, or null on an inherited collider, whose drags write nothing.
    private final @Nullable Runnable onCommitted;

    /// Reports the pick to the Components tree, which moves the row selection and claims an inherited
    /// component.
    private final Consumer<ColliderComponentHolder> onPicked;

    private boolean dragging;

    private ColliderPreviewComponent(
        final ColliderComponentHolder collider,
        final Supplier<Transform> parentTransform,
        final @Nullable Runnable onCommitted,
        final Consumer<ColliderComponentHolder> onPicked
    ) {
        this.collider = collider;
        this.parentTransform = parentTransform;
        this.onCommitted = onCommitted;
        this.onPicked = onPicked;
        this.restingColor = onCommitted == null ? COLOR_INHERITED : COLOR;
        this.wireframe = new ColliderViewerComponent(collider::makeObject, parentTransform);

        wireframe.setColor(restingColor);
    }

    /// A collider the patch declares: the gizmo drags its pivot transform, `onCommitted` runs once a
    /// drag is released.
    public static ColliderPreviewComponent editable(
        final ColliderComponentHolder collider,
        final Supplier<Transform> parentTransform,
        final Runnable onCommitted,
        final Consumer<ColliderComponentHolder> onPicked
    ) {
        return new ColliderPreviewComponent(collider, parentTransform, onCommitted, onPicked);
    }

    /// A collider an ancestor patch declares: drawn dim, and its drags write nothing. Picking it
    /// claims the component, which rebuilds it as an [#editable] one.
    public static ColliderPreviewComponent inherited(
        final ColliderComponentHolder collider,
        final Supplier<Transform> parentTransform,
        final Consumer<ColliderComponentHolder> onPicked
    ) {
        return new ColliderPreviewComponent(collider, parentTransform, null, onPicked);
    }

    public ColliderComponentHolder getCollider() {
        return collider;
    }

    // ***************************************************************
    // [LocatableComponent]
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return parentTransform.get();
    }

    @Override
    public Transform localTransform() {
        return collider.getPivotTransform().makeObject();
    }

    /// Ignored on an inherited collider: the holder behind it is an ancestor patch's.
    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (onCommitted == null) return;

        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(
                    new PendingDataChange<>(collider, localTransform(), this::writeTransform));
            }

            writeTransform(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(
            new DraggingDataChange<>(collider, transform, this::writeTransform));
        writeTransform(transform);
        onCommitted.run();
    }

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        collider.getPivotTransform().readFrom(transform);
    }

    // ***************************************************************
    // [Model3DViewerComponent]
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Hit test against the placed shape's outer [AABB], returning the camera distance so the nearest
    /// collider wins an overlap.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        AABB bounds = collider.makeObject().instantiate(null).placedAt(parentTransform.get()).getOuterAABB();
        Vector3f end = ray.at(PICK_REACH);
        Vec3 from = new Vec3(ray.origin().x, ray.origin().y, ray.origin().z);

        return bounds.clip(from, new Vec3(end.x, end.y, end.z))
            .map(hit -> (float) hit.distanceTo(from))
            .orElse(-1.0F);
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        LocatableComponent.super.onFocused(owner);

        wireframe.setColor(COLOR_FOCUSED);
        onPicked.accept(collider);
    }

    @Override
    public void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        LocatableComponent.super.onReleased(owner, newFocus);

        wireframe.setColor(restingColor);
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        wireframe.extractComponent(graphics, viewer, poseStack, byteBuffer, mouseX, mouseY, a);
    }

    @Override
    public int drawOrder() {
        return 35; // after grid (1), model (10), item (15), trajectory (20) and bone sockets (30)
    }
}
