package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.gui.widget.viewer.Ray;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.VectorUtils;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// A transform picker in 3D space.
///
/// Bound to one [LocatableComponent] at a time and raised / dropped by [Model3DViewer#focus]. Never
/// focusable itself: its input arrives through the focused component's press.
///
/// Handles are hit-tested in widget pixels, their world geometry projected through
/// [Model3DViewer#projectToWidget] — which is also what holds the gizmo at a constant on-screen size.
public final class GizmoComponent implements Model3DViewerComponent {
    public enum Mode {
        TRANSLATE, ROTATE, SCALE
    }

    /// The basis the handles run along: [#GLOBAL] the viewer's world axes, [#LOCAL] the target's own.
    public enum Orientation {
        GLOBAL, LOCAL
    }

    /// X / Y / Z.
    private static final int[] AXIS_COLORS = { 0xFFE04A5A, 0xFF7CC64B, 0xFF4A9BE0 };
    private static final int COLOR_HOVER = 0xFFFFC845;
    private static final int COLOR_VIEW = 0xFFDDDDDD;

    /// On-screen length of an axis handle, in widget pixels.
    private static final float HANDLE_PIXELS = 60.0F;
    private static final float PICK_TOLERANCE_PIXELS = 6.0F;
    private static final float SHAFT_HALF_PIXELS = 1.5F;
    private static final float ARROW_HALF_PIXELS = 5.0F;
    private static final float PLANE_OFFSET = 0.45F;
    private static final float PLANE_HALF = 0.11F;
    private static final float VIEW_RING_SCALE = 1.25F;
    private static final int RING_SEGMENTS = 64;

    /// How far the axis guide runs either side of the origin, as a multiple of the handle length.
    private static final float GUIDE_LINE_SCALE = 40.0F;

    /// Wedge left out of each axis ring, centred on its `+a` frame axis. A closed circle is
    /// rotationally symmetric and so reads the same at every angle; the gap turns with the ring and
    /// makes the rotation legible while it is being dragged. The view ring stays closed.
    private static final double RING_GAP = Math.toRadians(60.0);

    private static final Vector3f[] UNIT_AXES = {
        new Vector3f(1.0F, 0.0F, 0.0F),
        new Vector3f(0.0F, 1.0F, 0.0F),
        new Vector3f(0.0F, 0.0F, 1.0F)
    };

    /// Mode button box and the space between two boxes, both in widget pixels.
    private static final int BUTTON = 20;
    private static final int BUTTON_GAP = 2;

    /// Space between the mode buttons and the orientation toggle, in widget pixels.
    private static final int BUTTON_GROUP_GAP = 6;

    /// The two faces of the orientation toggle, held so the split lines are cached between frames.
    private static final Tooltip GLOBAL_TOOLTIP = Tooltip.create(Component.literal("Global"));
    private static final Tooltip LOCAL_TOOLTIP = Tooltip.create(Component.literal("Local"));

    private Mode mode = Mode.TRANSLATE;

    private Orientation orientation = Orientation.LOCAL;

    @Nullable
    private LocatableComponent target;

    @Nullable
    private Handle hovered;

    @Nullable
    private Handle active;

    /// Baseline captured on press. Every drag step is applied to this, not to the running value.
    private Transform dragStartLocal = Transform.IDENTITY;

    /// Where the press landed — a point on the drag plane for the plane / ring handles, the origin
    /// for the screen-projected ones.
    private final Vector3f dragStartPoint = new Vector3f();
    private double dragStartMouseX;
    private double dragStartMouseY;

    /// Whether the transform in flight was started by a key press instead of a held button. The
    /// cursor drives it from [#extractComponent] and the next mouse button ends it.
    private boolean capturing;

    /// Axis the transform in flight is limited to, indexing [Frame#axes] — -1 while it runs free.
    /// Only ever set while [#capturing].
    private int constrainedAxis = -1;

    /// Widget-space cursor of the last extracted frame — what a key press anchors against, since it
    /// carries no cursor of its own. [Integer#MIN_VALUE] until the first frame, which reads as
    /// outside the viewer.
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    private final List<AbstractWidget> modeButtons = new ArrayList<>();

    /// The three mode buttons among [#modeButtons], keyed by the mode each one sets.
    private final Map<Mode, AbstractWidget> modeButtonsByMode = new EnumMap<>(Mode.class);

    /// The orientation toggle among [#modeButtons], held to disable it in [Mode#SCALE].
    @Nullable
    private AnchoredButton orientationButton;

    public void bind(@Nullable LocatableComponent target) {
        abortTransform();

        this.target = target;
        this.hovered = null;
    }

    public @Nullable LocatableComponent getTarget() {
        return target;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        abortTransform();

        this.mode = mode;

        if (orientationButton != null) orientationButton.active = mode != Mode.SCALE;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    /// A transform started by [#onKeyPressed] survives the flip: it runs on [Handle#CENTER] or
    /// [Handle#VIEW], both built from the camera rather than from the handle basis.
    public void setOrientation(Orientation orientation) {
        if (!capturing) abortTransform();

        this.orientation = orientation;
    }

    public void toggleOrientation() {
        setOrientation(orientation == Orientation.GLOBAL ? Orientation.LOCAL : Orientation.GLOBAL);
    }

    /// The orientation the handles are actually built along.
    ///
    /// Pinned to [Orientation#LOCAL] in [Mode#SCALE]: a [Transform] keeps its scale as three numbers
    /// along its own axes, so a non-uniform scale along any other basis is a shear it has nowhere to
    /// store. The toggle is disabled in that mode rather than lying about what it does.
    private Orientation effectiveOrientation() {
        return mode == Mode.SCALE ? Orientation.LOCAL : orientation;
    }

    // ***************************************************************
    // Rendering
    // ***************************************************************

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (target == null) return;

        // A keyed transform runs with no button held, so no drag event carries a cursor for it.
        if (capturing) applyTransform(viewer, mouseX, mouseY, true);

        Frame frame = buildFrame(viewer);

        if (frame == null) return;

        if (active == null) hovered = hitTest(viewer, frame, mouseX, mouseY);

        RenderType renderType = RenderTypes.debugQuads();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        Handle lit = active != null ? active : hovered;

        switch (mode) {
            case TRANSLATE -> {
                emitAxes(builder, pose, viewer, frame, lit, true);
                emitPlanes(builder, pose, frame, lit);
            }
            case SCALE -> {
                emitAxes(builder, pose, viewer, frame, lit, false);
                emitPlanes(builder, pose, frame, lit);
                emitViewRing(builder, pose, viewer, frame, lit);
            }
            case ROTATE -> {
                emitRings(builder, pose, viewer, frame, lit);
                emitViewRing(builder, pose, viewer, frame, lit);
            }
        }

        if (constrainedAxis >= 0) emitGuideLine(builder, pose, viewer, frame, constrainedAxis);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// The gizmo's placement this frame: world origin, the three handle axes, and the pixel-to-world
    /// scale at that depth.
    private @Nullable Frame buildFrame(Model3DViewer viewer) {
        if (target == null) return null;

        Transform world = target.worldTransform();
        Vector3f origin = VectorUtils.doubleToFloatVector(world.loc());
        Vector3f[] axes = frameAxes(world);

        Vector3f projectedOrigin = viewer.projectToWidget(origin);
        Vector3f projectedProbe = viewer.projectToWidget(new Vector3f(origin).add(viewer.getCameraRight()));

        if (projectedOrigin == null || projectedProbe == null) return null;

        // Pixels one world unit spans at the origin's depth. A right-facing probe is used because it
        // is never foreshortened.
        float pixelsPerUnit = (float) Math.hypot(projectedProbe.x - projectedOrigin.x, projectedProbe.y - projectedOrigin.y);

        if (pixelsPerUnit < 1.0e-3F) return null;

        return new Frame(origin, axes, projectedOrigin, HANDLE_PIXELS / pixelsPerUnit, pixelsPerUnit);
    }

    /// The three handle axes in viewer world space — the world axes themselves under
    /// [Orientation#GLOBAL], `world`'s own under [Orientation#LOCAL].
    private Vector3f[] frameAxes(Transform world) {
        Vector3f[] axes = new Vector3f[3];
        boolean global = effectiveOrientation() == Orientation.GLOBAL;

        for (int i = 0; i < 3; i++) {
            axes[i] = global
                ? new Vector3f(UNIT_AXES[i])
                : VectorUtils.doubleToFloatVector(
                    VectorUtils.rotateByQuaternion(world.rot(), VectorUtils.toVec3(UNIT_AXES[i]))
                ).normalize();
        }

        return axes;
    }

    private record Frame(
        Vector3f origin,
        Vector3f[] axes,
        Vector3f projectedOrigin,
        float worldPerHandle,
        float pixelsPerUnit
    ) {
        Vector3f tip(int axis) {
            return new Vector3f(axes[axis]).mul(worldPerHandle).add(origin);
        }
    }

    /// The three axis handles: a screen-facing shaft, capped with an arrow (translate) or a square
    /// (scale).
    private void emitAxes(BufferBuilder builder, PoseStack.Pose pose, Model3DViewer viewer, Frame frame, @Nullable Handle lit, boolean arrow) {
        Vector3f right = viewer.getCameraRight();
        Vector3f up = viewer.getCameraUp();

        for (int i = 0; i < 3; i++) {
            Handle handle = Handle.axis(i);
            int color = handle.equals(lit) ? COLOR_HOVER : AXIS_COLORS[i];
            Vector3f tip = frame.tip(i);
            float shaftHalf = SHAFT_HALF_PIXELS / frame.pixelsPerUnit();
            float capHalf = ARROW_HALF_PIXELS / frame.pixelsPerUnit();

            emitBillboardRibbon(builder, pose, frame.origin(), tip, right, up, shaftHalf, color);

            if (arrow) {
                emitBillboardArrow(builder, pose, tip, frame.axes()[i], right, up, capHalf, color);
            } else {
                emitBillboardQuad(builder, pose, tip, right, up, capHalf, color);
            }
        }
    }

    /// The three plane handles — a square in the plane of two axes, offset toward their corner.
    private void emitPlanes(BufferBuilder builder, PoseStack.Pose pose, Frame frame, @Nullable Handle lit) {
        for (int normal = 0; normal < 3; normal++) {
            Handle handle = Handle.plane(normal);
            int color = handle.equals(lit) ? COLOR_HOVER : (AXIS_COLORS[normal] & 0x00FFFFFF) | 0xAA000000;

            for (Vector3f corner : planeCorners(frame, normal)) {
                builder.addVertex(pose, corner.x, corner.y, corner.z).setColor(color);
            }
        }
    }

    /// The four corners of the plane handle whose normal is `normal`, in world space.
    private Vector3f[] planeCorners(Frame frame, int normal) {
        int a = (normal + 1) % 3;
        int b = (normal + 2) % 3;
        float offset = PLANE_OFFSET * frame.worldPerHandle();
        float half = PLANE_HALF * frame.worldPerHandle();

        Vector3f center = new Vector3f(frame.origin())
            .add(new Vector3f(frame.axes()[a]).mul(offset))
            .add(new Vector3f(frame.axes()[b]).mul(offset));

        Vector3f da = new Vector3f(frame.axes()[a]).mul(half);
        Vector3f db = new Vector3f(frame.axes()[b]).mul(half);

        return new Vector3f[] {
            new Vector3f(center).sub(da).sub(db),
            new Vector3f(center).add(da).sub(db),
            new Vector3f(center).add(da).add(db),
            new Vector3f(center).sub(da).add(db)
        };
    }

    /// One ring per axis, in the plane that axis is normal to.
    private void emitRings(BufferBuilder builder, PoseStack.Pose pose, Model3DViewer viewer, Frame frame, @Nullable Handle lit) {
        Vector3f right = viewer.getCameraRight();
        Vector3f up = viewer.getCameraUp();
        float half = SHAFT_HALF_PIXELS / frame.pixelsPerUnit();

        for (int normal = 0; normal < 3; normal++) {
            Handle handle = Handle.ring(normal);
            int color = handle.equals(lit) ? COLOR_HOVER : AXIS_COLORS[normal];
            List<Vector3f> points = ringPoints(frame, normal, frame.worldPerHandle());

            for (int i = 0; i < points.size() - 1; i++) {
                emitBillboardRibbon(builder, pose, points.get(i), points.get(i + 1), right, up, half, color);
            }
        }
    }

    /// The outer screen-facing ring: view-axis rotation, or uniform scale.
    private void emitViewRing(BufferBuilder builder, PoseStack.Pose pose, Model3DViewer viewer, Frame frame, @Nullable Handle lit) {
        Vector3f right = viewer.getCameraRight();
        Vector3f up = viewer.getCameraUp();
        int color = Handle.VIEW.equals(lit) ? COLOR_HOVER : COLOR_VIEW;
        float radius = frame.worldPerHandle() * VIEW_RING_SCALE;
        float half = SHAFT_HALF_PIXELS / frame.pixelsPerUnit();
        Vector3f previous = null;

        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = i * 2.0 * Math.PI / RING_SEGMENTS;
            Vector3f point = new Vector3f(frame.origin())
                .add(new Vector3f(right).mul((float) Math.cos(angle) * radius))
                .add(new Vector3f(up).mul((float) Math.sin(angle) * radius));

            if (previous != null) emitBillboardRibbon(builder, pose, previous, point, right, up, half, color);

            previous = point;
        }
    }

    /// The line a constrained transform runs along: through the origin both ways, in the axis's own
    /// colour and thinner than a handle shaft.
    private void emitGuideLine(BufferBuilder builder, PoseStack.Pose pose, Model3DViewer viewer, Frame frame, int axis) {
        Vector3f reach = new Vector3f(frame.axes()[axis]).mul(frame.worldPerHandle() * GUIDE_LINE_SCALE);

        emitBillboardRibbon(
            builder, pose,
            new Vector3f(frame.origin()).sub(reach),
            new Vector3f(frame.origin()).add(reach),
            viewer.getCameraRight(), viewer.getCameraUp(),
            SHAFT_HALF_PIXELS * 0.5F / frame.pixelsPerUnit(),
            AXIS_COLORS[axis]
        );
    }

    /// The ring's polyline, walked from one lip of [#RING_GAP] round to the other. Each ring's `a`
    /// axis is a different one, so the three gaps never stack up in the same place.
    private List<Vector3f> ringPoints(Frame frame, int normal, float radius) {
        int a = (normal + 1) % 3;
        int b = (normal + 2) % 3;
        List<Vector3f> points = new ArrayList<>(RING_SEGMENTS + 1);

        double start = RING_GAP * 0.5;
        double sweep = 2.0 * Math.PI - RING_GAP;

        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = start + sweep * i / RING_SEGMENTS;
            points.add(new Vector3f(frame.origin())
                .add(new Vector3f(frame.axes()[a]).mul((float) Math.cos(angle) * radius))
                .add(new Vector3f(frame.axes()[b]).mul((float) Math.sin(angle) * radius)));
        }

        return points;
    }

    /// A flat strip from `from` to `to`, widened perpendicular to the segment in the camera plane so
    /// it keeps its thickness from any viewing angle.
    private static void emitBillboardRibbon(
        BufferBuilder builder, PoseStack.Pose pose,
        Vector3f from, Vector3f to, Vector3f right, Vector3f up, float half, int color
    ) {
        Vector3f direction = new Vector3f(to).sub(from);
        Vector3f normal = new Vector3f(right).cross(up);
        Vector3f perpendicular = new Vector3f(direction).cross(normal);

        if (perpendicular.lengthSquared() < 1.0e-8F) return;

        perpendicular.normalize().mul(half);

        emitQuad(builder, pose, color,
            new Vector3f(from).sub(perpendicular),
            new Vector3f(from).add(perpendicular),
            new Vector3f(to).add(perpendicular),
            new Vector3f(to).sub(perpendicular));
    }

    /// A screen-facing triangle, emitted as a quad with a doubled apex, capping an axis.
    private static void emitBillboardArrow(
        BufferBuilder builder, PoseStack.Pose pose,
        Vector3f tip, Vector3f direction, Vector3f right, Vector3f up, float half, int color
    ) {
        Vector3f normal = new Vector3f(right).cross(up);
        Vector3f perpendicular = new Vector3f(direction).cross(normal);

        if (perpendicular.lengthSquared() < 1.0e-8F) return;

        perpendicular.normalize().mul(half);

        Vector3f apex = new Vector3f(tip).add(new Vector3f(direction).normalize().mul(half * 2.5F));

        emitQuad(builder, pose, color,
            new Vector3f(tip).sub(perpendicular),
            new Vector3f(tip).add(perpendicular),
            apex,
            new Vector3f(apex));
    }

    /// A screen-facing square centered on `center` — the scale handles' cube stand-in.
    private static void emitBillboardQuad(
        BufferBuilder builder, PoseStack.Pose pose,
        Vector3f center, Vector3f right, Vector3f up, float half, int color
    ) {
        Vector3f dx = new Vector3f(right).mul(half);
        Vector3f dy = new Vector3f(up).mul(half);

        emitQuad(builder, pose, color,
            new Vector3f(center).sub(dx).sub(dy),
            new Vector3f(center).add(dx).sub(dy),
            new Vector3f(center).add(dx).add(dy),
            new Vector3f(center).sub(dx).add(dy));
    }

    private static void emitQuad(BufferBuilder builder, PoseStack.Pose pose, int color, Vector3f... corners) {
        for (Vector3f corner : corners) builder.addVertex(pose, corner.x, corner.y, corner.z).setColor(color);
    }

    // ***************************************************************
    // Hit testing — everything in widget pixels
    // ***************************************************************

    private @Nullable Handle hitTest(Model3DViewer viewer, Frame frame, double mouseX, double mouseY) {
        // Plane / cap handles sit on top of the shafts they share a corner with, so they answer first.
        if (mode != Mode.ROTATE) {
            for (int normal = 0; normal < 3; normal++) {
                if (insideProjectedQuad(viewer, planeCorners(frame, normal), mouseX, mouseY)) return Handle.plane(normal);
            }
        }

        if (mode == Mode.ROTATE) {
            for (int normal = 0; normal < 3; normal++) {
                if (nearProjectedPolyline(viewer, ringPoints(frame, normal, frame.worldPerHandle()), mouseX, mouseY)) {
                    return Handle.ring(normal);
                }
            }
        }

        if (mode != Mode.TRANSLATE) {
            float radius = frame.worldPerHandle() * VIEW_RING_SCALE * frame.pixelsPerUnit();
            double distance = Math.hypot(mouseX - frame.projectedOrigin().x, mouseY - frame.projectedOrigin().y);

            if (Math.abs(distance - radius) <= PICK_TOLERANCE_PIXELS) return Handle.VIEW;
        }

        if (mode != Mode.ROTATE) {
            for (int i = 0; i < 3; i++) {
                Vector3f tip = viewer.projectToWidget(frame.tip(i));

                if (tip == null) continue;

                if (distanceToSegment(mouseX, mouseY, frame.projectedOrigin().x, frame.projectedOrigin().y, tip.x, tip.y)
                    <= PICK_TOLERANCE_PIXELS) {
                    return Handle.axis(i);
                }
            }
        }

        if (mode == Mode.TRANSLATE
            && Math.hypot(mouseX - frame.projectedOrigin().x, mouseY - frame.projectedOrigin().y) <= PICK_TOLERANCE_PIXELS * 1.4) {
            return Handle.CENTER;
        }

        return null;
    }

    private static boolean insideProjectedQuad(Model3DViewer viewer, Vector3f[] corners, double x, double y) {
        float[] px = new float[corners.length];
        float[] py = new float[corners.length];

        for (int i = 0; i < corners.length; i++) {
            Vector3f projected = viewer.projectToWidget(corners[i]);

            if (projected == null) return false;

            px[i] = projected.x;
            py[i] = projected.y;
        }

        boolean inside = false;

        for (int i = 0, j = corners.length - 1; i < corners.length; j = i++) {
            if ((py[i] > y) != (py[j] > y)
                && x < (px[j] - px[i]) * (y - py[i]) / (py[j] - py[i]) + px[i]) {
                inside = !inside;
            }
        }

        return inside;
    }

    private static boolean nearProjectedPolyline(Model3DViewer viewer, List<Vector3f> points, double x, double y) {
        Vector3f previous = null;

        for (Vector3f point : points) {
            Vector3f projected = viewer.projectToWidget(point);

            if (projected != null && previous != null
                && distanceToSegment(x, y, previous.x, previous.y, projected.x, projected.y) <= PICK_TOLERANCE_PIXELS) {
                return true;
            }

            previous = projected;
        }

        return false;
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared < 1.0e-8 ? 0.0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / lengthSquared, 0.0, 1.0);

        return Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
    }

    // ***************************************************************
    // Dragging
    // ***************************************************************

    @Override
    public boolean onMouseDown(final Model3DViewer viewer, final MouseButtonEvent event) {
        if (target == null) return false;

        Frame frame = buildFrame(viewer);

        if (frame == null) return false;

        Handle handle = hitTest(viewer, frame, event.x(), event.y());

        return handle != null && beginDrag(viewer, frame, handle, event.x(), event.y());
    }

    /// Anchors `handle` at the cursor — the baseline and start point every following step is applied
    /// to. False when the anchor cannot be solved, leaving [#active] untouched.
    private boolean beginDrag(Model3DViewer viewer, Frame frame, Handle handle, double mouseX, double mouseY) {
        if (target == null) return false;

        Vector3f anchor = dragAnchor(viewer, frame, handle, mouseX, mouseY);

        if (anchor == null) return false;

        active = handle;
        dragStartLocal = target.localTransform();
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartPoint.set(anchor);

        return true;
    }

    @Override
    public void onMouseDragged(final Model3DViewer viewer, final MouseButtonEvent event, final double dx, final double dy) {
        applyTransform(viewer, event.x(), event.y(), true);
    }

    @Override
    public void onMouseReleased(final Model3DViewer viewer, final MouseButtonEvent event) {
        applyTransform(viewer, event.x(), event.y(), false);
        active = null;
    }

    @Override
    public void onDragCancelled(final Model3DViewer viewer) {
        abortTransform();
    }

    /// Puts the target back to the baseline the transform in flight started from, and ends it.
    ///
    /// Committing that baseline is a real commit, not a skipped one: it is what closes the live
    /// drag's pending history entry, and committing a value equal to the baseline drops the pair
    /// instead of recording a no-op.
    private void abortTransform() {
        if (target != null && active != null) target.applyLocalTransform(dragStartLocal, false);

        capturing = false;
        constrainedAxis = -1;
        active = null;
    }

    private void applyTransform(Model3DViewer viewer, double mouseX, double mouseY, boolean live) {
        if (target == null || active == null) return;

        Frame frame = buildFrame(viewer);

        if (frame == null) return;

        Transform result = switch (mode) {
            case TRANSLATE -> dragTranslate(viewer, frame, mouseX, mouseY);
            case ROTATE -> dragRotate(viewer, frame, mouseX, mouseY);
            case SCALE -> dragScale(viewer, frame, mouseX, mouseY);
        };

        if (result != null) target.applyLocalTransform(result, live);
    }

    /// Where a drag started: a point on the drag plane for the plane / ring handles, the origin for
    /// the screen-projected ones.
    private @Nullable Vector3f dragAnchor(Model3DViewer viewer, Frame frame, Handle handle, double mouseX, double mouseY) {
        Vector3f normal = dragPlaneNormal(viewer, frame, handle);

        if (normal == null) return new Vector3f(frame.origin());

        Ray ray = viewer.buildRay(mouseX, mouseY);

        if (ray == null) return null;

        float t = ray.intersectPlane(frame.origin(), normal);

        return t < 0.0F ? null : ray.at(t);
    }

    /// The plane a handle's drag is solved on, null for handles solved in screen space.
    private @Nullable Vector3f dragPlaneNormal(Model3DViewer viewer, Frame frame, Handle handle) {
        return switch (handle.kind()) {
            case PLANE, RING -> frame.axes()[handle.index()];
            case VIEW, CENTER -> new Vector3f(viewer.getCameraRight()).cross(viewer.getCameraUp());
            case AXIS -> null;
        };
    }

    private @Nullable Transform dragTranslate(Model3DViewer viewer, Frame frame, double mouseX, double mouseY) {
        if (target == null || active == null) return null;

        Vector3f worldDelta;

        if (active.kind() == Handle.Kind.AXIS) {
            worldDelta = axisScreenDelta(viewer, frame, active.index(), mouseX, mouseY);

            if (worldDelta == null) return null;
        } else {
            Vector3f normal = dragPlaneNormal(viewer, frame, active);
            Ray ray = viewer.buildRay(mouseX, mouseY);

            if (normal == null || ray == null) return null;

            float t = ray.intersectPlane(frame.origin(), normal);

            if (t < 0.0F) return null;

            worldDelta = ray.at(t).sub(dragStartPoint);
        }

        // `world.loc = parent.loc + rotate(parent.rot, local.loc * parent.sca)` — so a world delta
        // lands in local space un-rotated by the parent and un-scaled by it.
        Transform parent = target.parentTransform();
        Vec3 localDelta = VectorUtils.rotateByQuaternion(
            parent.rot().conjugate(new Quaterniond()), VectorUtils.toVec3(worldDelta)
        );

        return new Transform(
            dragStartLocal.loc().add(divideSafely(localDelta, parent.sca())),
            dragStartLocal.rot(),
            dragStartLocal.sca()
        );
    }

    private @Nullable Transform dragRotate(Model3DViewer viewer, Frame frame, double mouseX, double mouseY) {
        if (target == null || active == null) return null;

        Vector3f normal = dragPlaneNormal(viewer, frame, active);
        Ray ray = viewer.buildRay(mouseX, mouseY);

        if (normal == null || ray == null) return null;

        float t = ray.intersectPlane(frame.origin(), normal);

        if (t < 0.0F) return null;

        Vector3f from = new Vector3f(dragStartPoint).sub(frame.origin());
        Vector3f to = ray.at(t).sub(frame.origin());

        if (from.lengthSquared() < 1.0e-8F || to.lengthSquared() < 1.0e-8F) return null;

        from.normalize();
        to.normalize();

        double angle = Math.atan2(new Vector3f(from).cross(to).dot(normal), from.dot(to));
        Quaterniond worldRotation = new Quaterniond().rotateAxis(angle, normal.x, normal.y, normal.z);

        // `world.rot = parent.rot × local.rot`, so pre-multiplying a world rotation lands in local
        // space conjugated by the parent.
        Quaterniond parentRotation = target.parentTransform().rot();
        Quaterniond localRotation = parentRotation.conjugate(new Quaterniond())
            .mul(worldRotation, new Quaterniond())
            .mul(parentRotation, new Quaterniond())
            .mul(dragStartLocal.rot(), new Quaterniond())
            .normalize();

        return new Transform(dragStartLocal.loc(), localRotation, dragStartLocal.sca());
    }

    private @Nullable Transform dragScale(Model3DViewer viewer, Frame frame, double mouseX, double mouseY) {
        if (target == null || active == null) return null;

        if (Handle.VIEW.equals(active)) {
            double startRadius = Math.hypot(dragStartMouseX - frame.projectedOrigin().x, dragStartMouseY - frame.projectedOrigin().y);
            double radius = Math.hypot(mouseX - frame.projectedOrigin().x, mouseY - frame.projectedOrigin().y);

            if (startRadius < 1.0) return null;

            double factor = Math.max(0.01, radius / startRadius);

            return new Transform(dragStartLocal.loc(), dragStartLocal.rot(), dragStartLocal.sca().scale(factor));
        }

        // Scale handles run along the local axes, so the drag multiplies the local scale directly.
        double[] factors = { 1.0, 1.0, 1.0 };

        for (int i = 0; i < 3; i++) {
            if (active.kind() == Handle.Kind.AXIS && active.index() != i) continue;
            if (active.kind() == Handle.Kind.PLANE && active.index() == i) continue;

            Vector3f delta = axisScreenDelta(viewer, frame, i, mouseX, mouseY);

            if (delta == null) continue;

            factors[i] = Math.max(0.01, 1.0 + delta.dot(frame.axes()[i]) / frame.worldPerHandle());
        }

        return new Transform(
            dragStartLocal.loc(),
            dragStartLocal.rot(),
            new Vec3(
                dragStartLocal.sca().x * factors[0],
                dragStartLocal.sca().y * factors[1],
                dragStartLocal.sca().z * factors[2]
            )
        );
    }

    /// How far the cursor has slid along axis `i`, in world units — the cursor delta projected on
    /// the axis's screen direction, over that axis's on-screen length. Falls to zero as the axis
    /// turns toward the camera.
    private @Nullable Vector3f axisScreenDelta(Model3DViewer viewer, Frame frame, int i, double mouseX, double mouseY) {
        Vector3f tip = viewer.projectToWidget(frame.tip(i));

        if (tip == null) return null;

        double screenX = tip.x - frame.projectedOrigin().x;
        double screenY = tip.y - frame.projectedOrigin().y;
        double screenLengthSquared = screenX * screenX + screenY * screenY;

        if (screenLengthSquared < 1.0) return new Vector3f();

        double amount = ((mouseX - dragStartMouseX) * screenX + (mouseY - dragStartMouseY) * screenY) / screenLengthSquared;

        return new Vector3f(frame.axes()[i]).mul((float) (amount * frame.worldPerHandle()));
    }

    /// Component-wise divide guarding a zero-scaled parent axis.
    private static Vec3 divideSafely(Vec3 value, Vec3 divisor) {
        return new Vec3(
            Math.abs(divisor.x) < 1.0e-6 ? 0.0 : value.x / divisor.x,
            Math.abs(divisor.y) < 1.0e-6 ? 0.0 : value.y / divisor.y,
            Math.abs(divisor.z) < 1.0e-6 ? 0.0 : value.z / divisor.z
        );
    }

    // ***************************************************************
    // Keyed transforms
    // ***************************************************************

    /// Starts a transform the bare cursor drives, or flips the basis. The mode's screen-space handle
    /// is anchored where the cursor last sat and the next mouse button ends it: [Handle#CENTER] for
    /// [Mode#TRANSLATE] — the camera-facing plane — and [Handle#VIEW] for the other two, view-axis
    /// rotation and uniform scale.
    ///
    /// Only taken while the cursor is over the viewer: an editor key reaches every panel, these bindings are
    /// unmodified, and `Tab` is what [Screen] navigates focus with.
    @Override
    public boolean onKeyPressed(final Model3DViewer viewer, final KeyEvent event) {
        if (target == null || !viewer.isMouseOver(lastMouseX, lastMouseY)) return false;

        if (capturing) {
            int axis = keyedAxis(event);

            // The axis already in force frees the transform again.
            if (axis >= 0) {
                reanchorCapture(viewer, constrainedAxis == axis ? -1 : axis);
                return true;
            }
        }

        if (EditorKeyMappings.GIZMO_ORIENTATION.matches(event)) {
            // Mirrors [#orientationButton]'s enabled state — the basis is pinned in [Mode#SCALE].
            if (mode != Mode.SCALE) {
                toggleOrientation();
                focusButton(orientationButton);

                // The handle basis moved out from under a constrained transform.
                if (capturing && constrainedAxis >= 0) reanchorCapture(viewer, constrainedAxis);
            }

            return true;
        }

        Mode keyed = keyedMode(event);

        if (keyed == null) return false;

        setMode(keyed);
        focusButton(modeButtonsByMode.get(keyed));

        Frame frame = buildFrame(viewer);

        capturing = frame != null && beginDrag(viewer, frame, capturedHandle(-1), lastMouseX, lastMouseY);

        return true;
    }

    /// Re-solves the transform in flight along `axis`, or free again for -1.
    ///
    /// The target goes back to its baseline first, so the new handle anchors against the frame the
    /// gesture started on, and re-reads the cursor from where the gesture was pressed — the whole
    /// motion so far is re-interpreted under the new limit rather than restarting from nothing.
    private void reanchorCapture(Model3DViewer viewer, int axis) {
        if (target == null || !capturing) return;

        target.applyLocalTransform(dragStartLocal, true);

        Frame frame = buildFrame(viewer);

        if (frame == null) return;

        if (beginDrag(viewer, frame, capturedHandle(axis), dragStartMouseX, dragStartMouseY)) {
            constrainedAxis = axis;
        }
    }

    /// The handle a keyed transform runs on. Unconstrained (`axis` < 0) it is the screen-space one:
    /// [Handle#CENTER] for [Mode#TRANSLATE] — the camera-facing plane — and [Handle#VIEW] for the
    /// other two, view-axis rotation and uniform scale.
    private Handle capturedHandle(int axis) {
        if (axis < 0) return mode == Mode.TRANSLATE ? Handle.CENTER : Handle.VIEW;

        return mode == Mode.ROTATE ? Handle.ring(axis) : Handle.axis(axis);
    }

    /// The mode a key press asks for, null when it is none of the three bindings.
    private static @Nullable Mode keyedMode(KeyEvent event) {
        if (EditorKeyMappings.GIZMO_TRANSLATE.matches(event)) return Mode.TRANSLATE;
        if (EditorKeyMappings.GIZMO_ROTATE.matches(event)) return Mode.ROTATE;
        if (EditorKeyMappings.GIZMO_SCALE.matches(event)) return Mode.SCALE;

        return null;
    }

    /// The axis a key press limits to, -1 when it is none of the three bindings.
    private static int keyedAxis(KeyEvent event) {
        if (EditorKeyMappings.GIZMO_AXIS_X.matches(event)) return 0;
        if (EditorKeyMappings.GIZMO_AXIS_Y.matches(event)) return 1;
        if (EditorKeyMappings.GIZMO_AXIS_Z.matches(event)) return 2;

        return -1;
    }

    /// Moves the overlay's focus onto `button` and off the rest, so a shortcut leaves the button it
    /// drove in the state hovering that button puts it in.
    private void focusButton(@Nullable AbstractWidget button) {
        for (AbstractWidget widget : modeButtons) widget.setFocused(widget == button);
    }

    @Override
    public boolean isCapturingMouse(final Model3DViewer viewer) {
        return capturing;
    }

    @Override
    public void onCaptureConfirmed(final Model3DViewer viewer) {
        applyTransform(viewer, lastMouseX, lastMouseY, false);

        capturing = false;
        constrainedAxis = -1;
        active = null;
    }

    // ***************************************************************
    // Mode buttons
    // ***************************************************************

    /// Installs the Translate / Rotate / Scale buttons and the orientation toggle along the viewer's
    /// top edge.
    public void installModeButtons(Model3DViewer viewer) {
        if (!modeButtons.isEmpty()) return;

        addModeButton(viewer, Mode.TRANSLATE, AkytheraLabsButtonTheme.GIZMO_TRANSLATE_BUTTON, "Move", 4);
        addModeButton(viewer, Mode.ROTATE, AkytheraLabsButtonTheme.GIZMO_ROTATE_BUTTON, "Rotate", 4 + BUTTON + BUTTON_GAP);
        addModeButton(viewer, Mode.SCALE, AkytheraLabsButtonTheme.GIZMO_SCALE_BUTTON, "Scale", 4 + (BUTTON + BUTTON_GAP) * 2);
        addOrientationButton(viewer, 4 + (BUTTON + BUTTON_GAP) * 3 + BUTTON_GROUP_GAP);
    }

    public void removeModeButtons(Model3DViewer viewer) {
        modeButtons.forEach(viewer::removeOverlayWidget);
        modeButtons.clear();
        modeButtonsByMode.clear();
        orientationButton = null;
    }

    private void addModeButton(Model3DViewer viewer, Mode buttonMode, AkytheraLabsButtonTheme theme, String tooltip, int offsetX) {
        Tooltip label = Tooltip.create(Component.literal(tooltip));

        AnchoredButton button = AnchoredButton
            .buttonBuilder(Minecraft.getInstance().font, Component.empty(), _ -> setMode(buttonMode))
            .xUnits(AnchoredWidget.pixel(offsetX), AnchoredWidget.pixel(BUTTON))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(theme)
            .tooltip(_ -> label)
            .isHighlighted(_ -> mode == buttonMode)
            .build();

        modeButtons.add(button);
        modeButtonsByMode.put(buttonMode, button);
        viewer.addOverlayWidget(button);
    }

    /// One button holding both bases: pressing it flips the icon between the world and the local one.
    /// Highlighted stands for [Orientation#GLOBAL], and both it and the tooltip read
    /// [#effectiveOrientation] so they name the basis the handles are really on.
    private void addOrientationButton(Model3DViewer viewer, int offsetX) {
        AnchoredButton button = AnchoredButton
            .buttonBuilder(Minecraft.getInstance().font, Component.empty(), _ -> toggleOrientation())
            .xUnits(AnchoredWidget.pixel(offsetX), AnchoredWidget.pixel(BUTTON))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.GIZMO_ORIENTATION_BUTTON)
            .tooltip(_ -> effectiveOrientation() == Orientation.GLOBAL ? GLOBAL_TOOLTIP : LOCAL_TOOLTIP)
            .isHighlighted(_ -> effectiveOrientation() == Orientation.GLOBAL)
            .build();

        button.active = mode != Mode.SCALE;
        orientationButton = button;
        modeButtons.add(button);
        viewer.addOverlayWidget(button);
    }

    @Override
    public void destroy() {
        abortTransform();

        target = null;
        modeButtons.clear();
        modeButtonsByMode.clear();
        orientationButton = null;
    }

    @Override
    public int drawOrder() {
        return OVERLAY_DRAW_ORDER;
    }

    /// One clickable part of the gizmo. `index` names the axis for [Kind#AXIS] and [Kind#RING], and
    /// the normal axis for [Kind#PLANE].
    private record Handle(Kind kind, int index) {
        enum Kind { AXIS, PLANE, RING, VIEW, CENTER }

        static final Handle VIEW = new Handle(Kind.VIEW, -1);
        static final Handle CENTER = new Handle(Kind.CENTER, -1);

        static Handle axis(int index) { return new Handle(Kind.AXIS, index); }
        static Handle plane(int index) { return new Handle(Kind.PLANE, index); }
        static Handle ring(int index) { return new Handle(Kind.RING, index); }
    }
}