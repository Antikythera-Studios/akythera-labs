package com.yesman.akytheralabs.gui.widget.viewer;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/// A [Model3DViewerComponent] that sits somewhere: focusing one raises a [GizmoComponent] bound to
/// it, and focusing anything else drops the gizmo again.
///
/// The transform is split into the space the component is placed in and the offset it is edited by
/// — a bone socket is drawn at `bone × offset` but the author edits `offset`. The gizmo draws at the
/// offset placed by [#transformInheritPolicy] and converts its drags back into [#localTransform]'s space itself.
public interface LocatableComponent extends Model3DViewerComponent {
    /// The space [#localTransform] is placed in, in viewer world space. [Transform#IDENTITY] for
    /// something placed directly in the world.
    Transform parentTransform();

    /// The edited offset, in [#parentTransform]'s space.
    Transform localTransform();

    /// Writes a gizmo drag back. `live` is true for every step of an in-progress drag, false for the
    /// committed value on release.
    void applyLocalTransform(Transform transform, boolean live);

    /// The elements of [#parentTransform] [#worldTransform] takes on.
    default TransformInheritPolicy transformInheritPolicy() {
        return TransformInheritPolicy.LOC_ROT_SCA;
    }

    /// [#localTransform] in viewer world space — where the gizmo is drawn.
    default Transform worldTransform() {
        return transformInheritPolicy().inherit(parentTransform(), localTransform());
    }

    /// The gizmo modes this component can be edited in; the gizmo offers only these. Never empty.
    default Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.allOf(GizmoComponent.Mode.class);
    }

    /// The gizmo bound to this component, or null when another component holds it.
    default @Nullable GizmoComponent boundGizmo(final Model3DViewer owner) {
        GizmoComponent gizmo = owner.getComponent(GizmoComponent.class);

        return gizmo != null && gizmo.getTarget() == this ? gizmo : null;
    }

    /// Raises the gizmo onto this component, building one if the viewer has none yet. The viewer
    /// itself knows nothing about gizmos — the focused component is what brings one up.
    @Override
    default void onFocused(final Model3DViewer owner) {
        GizmoComponent gizmo = owner.getComponent(GizmoComponent.class);

        if (gizmo == null) {
            gizmo = new GizmoComponent();
            owner.addComponent(gizmo);
        }

        gizmo.bind(this);
        gizmo.installModeButtons(owner);
    }

    /// Hands the gizmo to `newFocus` when it is locatable too, and drops it otherwise.
    @Override
    default void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        GizmoComponent gizmo = owner.getComponent(GizmoComponent.class);

        if (gizmo == null) return;

        gizmo.removeModeButtons(owner);

        if (!(newFocus instanceof LocatableComponent)) owner.removeComponent(gizmo);
    }

    /// The press reaches the gizmo through here — it is a sibling in the viewer and never focused,
    /// so the focused component is what routes input to it. Taking the press is also what keeps the
    /// camera off it: an unclaimed press is what [Model3DViewer#mouseDragged] passes to the camera.
    @Override
    default boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        GizmoComponent gizmo = boundGizmo(owner);

        return gizmo != null && gizmo.onMouseDown(owner, event);
    }

    @Override
    default void onMouseDragged(final Model3DViewer owner, final MouseButtonEvent event, final double dx, final double dy) {
        GizmoComponent gizmo = boundGizmo(owner);

        if (gizmo != null) gizmo.onMouseDragged(owner, event, dx, dy);
    }

    @Override
    default void onMouseReleased(final Model3DViewer owner, final MouseButtonEvent event) {
        GizmoComponent gizmo = boundGizmo(owner);

        if (gizmo != null) gizmo.onMouseReleased(owner, event);
    }

    @Override
    default void onDragCancelled(final Model3DViewer owner) {
        GizmoComponent gizmo = boundGizmo(owner);

        if (gizmo != null) gizmo.onDragCancelled(owner);
    }

    @Override
    default boolean onKeyPressed(final Model3DViewer owner, final KeyEvent event) {
        GizmoComponent gizmo = boundGizmo(owner);

        return gizmo != null && gizmo.onKeyPressed(owner, event);
    }

    @Override
    default boolean isCapturingMouse(final Model3DViewer owner) {
        GizmoComponent gizmo = boundGizmo(owner);

        return gizmo != null && gizmo.isCapturingMouse(owner);
    }

    @Override
    default void onCaptureConfirmed(final Model3DViewer owner) {
        GizmoComponent gizmo = boundGizmo(owner);

        if (gizmo != null) gizmo.onCaptureConfirmed(owner);
    }
}