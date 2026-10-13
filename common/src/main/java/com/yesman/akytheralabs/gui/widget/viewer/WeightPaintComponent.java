package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.physics.BrushSettings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import java.util.function.Supplier;

/// Paints one weight per vertex onto a skeletal mesh's bind pose with a [BrushSettings] brush.
///
/// A weight draws white at 1, black near 0 and green at an absolute 0. With [#setHeatColors] on it draws
/// from blue at 0 through green and yellow to red at 1 instead. The offset vertex of a pair draws green and
/// takes no paint. [ViewMode#WIREFRAME] draws them on the edges alone,
/// [ViewMode#WEIGHT_PAINT] fills each triangle with them under white edges, and [ViewMode#TEXTURED] draws
/// the textured mesh with them on its edges.
///
/// [BrushSettings.Mode#GRADATION] paints along a line dragged from any press in the viewer, drawn over
/// the scene while it is dragged. The camera does not rotate in that mode.
///
/// With a vertex listener set ([#setOnVertexPicked]), the brush paints in [ViewMode#WEIGHT_PAINT] only. In
/// the other views a left press picks the vertex under the cursor, and the next one of a stack of vertices
/// sharing that place when one of them is already picked. An orange dot marks it.
///
/// With [#setPoser] set, the mesh is drawn, picked and painted where the poser puts its vertices.
public final class WeightPaintComponent implements Model3DViewerComponent {
    /// How the mesh and its weights are drawn.
    public enum ViewMode {
        WIREFRAME(AkytheraLabsButtonTheme.CLOTH_WIREFRAME_BUTTON, LangKeys.GUI_CLOTH_PHYSICS_SCREEN_VIEW_WIREFRAME),
        WEIGHT_PAINT(AkytheraLabsButtonTheme.WEIGHT_PAINT_BUTTON, LangKeys.GUI_CLOTH_PHYSICS_SCREEN_VIEW_WEIGHT_PAINT),
        TEXTURED(AkytheraLabsButtonTheme.CLOTH_TEXTURED_BUTTON, LangKeys.GUI_CLOTH_PHYSICS_SCREEN_VIEW_TEXTURED);

        private final AkytheraLabsButtonTheme buttonTheme;
        private final Tooltip tooltip;

        ViewMode(final AkytheraLabsButtonTheme buttonTheme, final String nameKey) {
            this.buttonTheme = buttonTheme;
            this.tooltip = Tooltip.create(Component.translatable(nameKey));
        }

        /// The icon a button showing this mode draws.
        public AkytheraLabsButtonTheme buttonTheme() {
            return buttonTheme;
        }

        /// This mode's name.
        public Tooltip tooltip() {
            return tooltip;
        }

        /// The mode a view button moves to from this one.
        public ViewMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int COLOR_KINEMATIC = 0xFF33CC66;
    private static final int COLOR_EDGE = 0xFFFFFFFF;
    private static final int COLOR_CURSOR = 0xFFFFA028;
    private static final int COLOR_PICKED = 0xFFFFA028;
    private static final int CURSOR_SEGMENTS = 32;

    /// How far from the cursor a press still picks a vertex, in widget pixels.
    private static final float PICK_PIXELS = 8.0F;

    /// Half the side of the dot marking the picked vertex, in widget pixels.
    private static final int PICKED_DOT_PIXELS = 2;

    /// Distance under which two vertices share one place, in viewer world units.
    private static final float SAME_PLACE = 1.0E-4F;

    /// Cursor travel between two dabs of one stroke, as a fraction of the brush size.
    private static final float DAB_SPACING = 0.25F;

    /// Half-length of the tick across each end of the gradation line, in widget pixels.
    private static final int GRADATION_TICK = 4;

    /// Share of the camera distance a triangle may sit in front of a vertex and still not hide it.
    private static final float VISIBILITY_TOLERANCE = 1.0E-3F;


    /// Vanilla's `debug_quads` pipeline with depth writes on. That one only tests depth, so the filled
    /// triangles would draw through each other and through the edges.
    private static final RenderPipeline FILL_PIPELINE = RenderPipeline.builder()
        .withLocation(AkytheraLabs.identifier("pipeline/cloth_weight_fill"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .withCull(false)
        .build();

    /// Weight-colored triangle fills of [ViewMode#WEIGHT_PAINT], each a quad with its last corner repeated.
    private static final RenderType FILL_TYPE = RenderType.create(
        AkytheraLabs.MODID + ":cloth_weight_fill", RenderSetup.builder(FILL_PIPELINE).createRenderSetup()
    );

    private final AssetAccessor<? extends SkeletalMesh> mesh;
    private final Supplier<float[]> weights;

    /// Mesh position index pairs, flattened as `root, offset`.
    private final Supplier<int[]> offsetPairs;

    private final BrushSettings brush;

    /// Receives the weights before and after a finished stroke.
    private final BiConsumer<float[], float[]> onStrokeCommitted;

    /// The mesh [#positions], [#triangles] and [#edges] were read from.
    private @Nullable SkeletalMesh builtMesh;

    /// Bind-pose position per mesh position index, in viewer world space.
    private Vector3f[] bindPositions = new Vector3f[0];

    /// Where each mesh position is drawn this frame: [#bindPositions], or what [#poser] made of them.
    private Vector3f[] positions = new Vector3f[0];

    /// Moves the bind-pose positions to where the mesh stands, read per frame. Null draws the bind pose.
    private @Nullable UnaryOperator<Vector3f[]> poser;

    /// Position-index triples.
    private int[] triangles = new int[0];

    /// Unique position-index pairs.
    private int[] edges = new int[0];

    private float @Nullable [] strokeBefore;
    private float @Nullable [] strokeWeights;
    private double lastDabX;
    private double lastDabY;

    /// Where the gradation line starts, in widget pixels. Null unless a gradation stroke is dragged.
    private @Nullable Vector2f gradationStart;

    /// Where the gradation line ends, in widget pixels.
    private final Vector2f gradationEnd = new Vector2f();

    private ViewMode viewMode = ViewMode.WEIGHT_PAINT;

    /// Whether a weight draws on the blue to red ramp, not from black to white.
    private boolean heatColors;

    /// Whether the brush may paint right now.
    private BooleanSupplier paintable = () -> true;

    /// Receives the mesh position index a press picked.
    private @Nullable IntConsumer onVertexPicked;

    /// The mesh position index marked in the view, -1 for none.
    private int pickedVertex = -1;

    public WeightPaintComponent(
        final AssetAccessor<? extends SkeletalMesh> mesh,
        final Supplier<float[]> weights,
        final Supplier<int[]> offsetPairs,
        final BrushSettings brush,
        final BiConsumer<float[], float[]> onStrokeCommitted
    ) {
        this.mesh = mesh;
        this.weights = weights;
        this.offsetPairs = offsetPairs;
        this.brush = brush;
        this.onStrokeCommitted = onStrokeCommitted;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        SkeletalMesh skeletalMesh = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (skeletalMesh == null) return;

        if (skeletalMesh != builtMesh) {
            ClothMeshGeometry geometry = ClothMeshGeometry.of(skeletalMesh);

            bindPositions = geometry.positions();
            triangles = geometry.triangles();
            edges = geometry.edges();
            builtMesh = skeletalMesh;
            pickedVertex = -1;
        }

        positions = poser == null ? bindPositions : poser.apply(bindPositions);

        Ray probe = viewer.buildRay(viewer.getX(), viewer.getY());

        if (probe == null) return;

        float[] shown = strokeWeights != null ? strokeWeights : weights.get();
        boolean[] offsets = offsetMask();
        Vector3f eye = probe.origin();
        PoseStack.Pose pose = poseStack.last();

        if (viewMode == ViewMode.TEXTURED) {
            Identifier texture = skeletalMesh.getMetadata().texture();
            BufferBuilder textured = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);

            Vector2f uv = new Vector2f();

            for (SkeletalMesh.SkeletalMeshPart part : skeletalMesh.getParts().values()) {
                for (int v = 0; v + 2 < part.vertexCount(); v += 3) {
                    Vector3f first = positions[part.getPositionIndex(v)];
                    Vector3f normal = positions[part.getPositionIndex(v + 1)].sub(first, new Vector3f())
                        .cross(positions[part.getPositionIndex(v + 2)].sub(first, new Vector3f()));

                    // A triangle with no area has no face to draw
                    if (normal.lengthSquared() < 1.0E-12F) continue;

                    pose.transformNormal(normal.normalize(), normal);

                    for (int corner = 0; corner < 3; corner++) {
                        Vector3f position = pose.pose().transformPosition(positions[part.getPositionIndex(v + corner)], new Vector3f());

                        skeletalMesh.getUv(part.getUvIndex(v + corner), uv);
                        Mesh.DrawingFunction.NEW_ENTITY.draw(
                            textured,
                            position.x, position.y, position.z,
                            normal.x, normal.y, normal.z,
                            Model3DViewer.FULL_BRIGHT,
                            1.0F, 1.0F, 1.0F, 1.0F,
                            uv.x, uv.y,
                            OverlayTexture.NO_OVERLAY
                        );
                    }
                }
            }

            MeshData texturedData = textured.build();

            if (texturedData != null) {
                RenderUtils.drawImmediate(
                    RenderTypes.entityTranslucent(texture == null ? MissingTextureAtlasSprite.getLocation() : texture), texturedData
                );
            }
        } else if (viewMode == ViewMode.WEIGHT_PAINT) {
            BufferBuilder fills = new BufferBuilder(byteBuffer, FILL_TYPE.primitiveTopology(), FILL_TYPE.format());

            for (int t = 0; t + 2 < triangles.length; t += 3) {
                int first = triangles[t];
                int second = triangles[t + 1];
                int third = triangles[t + 2];

                fills.addVertex(pose, positions[first].x, positions[first].y, positions[first].z).setColor(colorOf(shown, offsets, first));
                fills.addVertex(pose, positions[second].x, positions[second].y, positions[second].z).setColor(colorOf(shown, offsets, second));
                fills.addVertex(pose, positions[third].x, positions[third].y, positions[third].z).setColor(colorOf(shown, offsets, third));
                fills.addVertex(pose, positions[third].x, positions[third].y, positions[third].z).setColor(colorOf(shown, offsets, third));
            }

            MeshData fillData = fills.build();

            if (fillData != null) RenderUtils.drawImmediate(FILL_TYPE, fillData);
        }

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        for (int e = 0; e + 1 < edges.length; e += 2) {
            int from = edges[e];
            int to = edges[e + 1];
            int fromColor = viewMode == ViewMode.WEIGHT_PAINT ? COLOR_EDGE : colorOf(shown, offsets, from);
            int toColor = viewMode == ViewMode.WEIGHT_PAINT ? COLOR_EDGE : colorOf(shown, offsets, to);

            ClothMeshGeometry.emitLine(
                builder, pose, ClothMeshGeometry.lifted(positions[from], eye), fromColor, ClothMeshGeometry.lifted(positions[to], eye), toColor
            );
        }

        Ray cursorRay = viewer.buildRay(mouseX, mouseY);
        Vector3f surfaceNormal = new Vector3f();
        float hit = cursorRay == null ? -1.0F : nearestHit(cursorRay, surfaceNormal);

        if (cursorRay != null
            && hit >= 0.0F
            && brush.getMode() != BrushSettings.Mode.GRADATION
            && !isPickingVertices()
            && paintable.getAsBoolean()
        ) {
            Vector3f center = cursorRay.at(hit);
            float radius = worldPerPixel(viewer, center) * brush.getSize();

            if (brush.getMode() == BrushSettings.Mode.VOLUME) {
                emitCircle(builder, pose, eye, center, new Vector3f(radius, 0.0F, 0.0F), new Vector3f(0.0F, radius, 0.0F));
                emitCircle(builder, pose, eye, center, new Vector3f(0.0F, radius, 0.0F), new Vector3f(0.0F, 0.0F, radius));
                emitCircle(builder, pose, eye, center, new Vector3f(0.0F, 0.0F, radius), new Vector3f(radius, 0.0F, 0.0F));
            } else {
                Vector3f helper = Math.abs(surfaceNormal.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
                Vector3f tangent = surfaceNormal.cross(helper, new Vector3f()).normalize(radius);
                Vector3f bitangent = surfaceNormal.cross(tangent, new Vector3f()).normalize(radius);

                emitCircle(builder, pose, eye, center, tangent, bitangent);
            }
        }


        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// The dot on the picked vertex, and the gradation line being dragged, with a tick across each end.
    @Override
    public void extractOverlay(final GuiGraphicsExtractor graphics, final Model3DViewer viewer, final int mouseX, final int mouseY, final float a) {
        Vector3f picked = pickedVertex >= 0 && pickedVertex < positions.length ? viewer.projectToWidget(positions[pickedVertex]) : null;

        if (picked != null) {
            graphics.enableScissor(viewer.getX(), viewer.getY(), viewer.getRight(), viewer.getBottom());
            graphics.fill(
                Math.round(picked.x) - PICKED_DOT_PIXELS, Math.round(picked.y) - PICKED_DOT_PIXELS,
                Math.round(picked.x) + PICKED_DOT_PIXELS + 1, Math.round(picked.y) + PICKED_DOT_PIXELS + 1,
                COLOR_PICKED
            );
            graphics.disableScissor();
        }

        if (gradationStart == null) return;

        float dx = gradationEnd.x - gradationStart.x;
        float dy = gradationEnd.y - gradationStart.y;
        int length = Math.round((float) Math.hypot(dx, dy));

        graphics.enableScissor(viewer.getX(), viewer.getY(), viewer.getRight(), viewer.getBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(gradationStart.x, gradationStart.y);
        graphics.pose().rotate((float) Math.atan2(dy, dx));

        graphics.fill(0, 0, length, 1, COLOR_CURSOR);
        graphics.fill(0, -GRADATION_TICK, 1, GRADATION_TICK + 1, COLOR_CURSOR);
        graphics.fill(length - 1, -GRADATION_TICK, length, GRADATION_TICK + 1, COLOR_CURSOR);

        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        return nearestHit(ray, null);
    }

    /// Starts a stroke when the press lands on the mesh or has vertices under the brush. A
    /// [BrushSettings.Mode#GRADATION] stroke starts on any press.
    ///
    /// While vertices are picked instead, picks the one [#vertexAt] the press.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        if (event.button() != 0) return false;

        if (isPickingVertices()) {
            int nearest = vertexAt(owner, event.x(), event.y());

            if (nearest < 0) return false;

            if (pickedVertex >= 0 && pickedVertex < positions.length && positions[pickedVertex].distance(positions[nearest]) <= SAME_PLACE) {
                for (int step = 1; step <= positions.length; step++) {
                    int candidate = (pickedVertex + step) % positions.length;

                    if (positions[candidate].distance(positions[nearest]) <= SAME_PLACE) {
                        nearest = candidate;
                        break;
                    }
                }
            }

            pickedVertex = nearest;
            onVertexPicked.accept(nearest);

            return true;
        }

        if (!paintable.getAsBoolean()) return false;

        float[] base = weights.get();
        float[] working = Arrays.copyOf(base, positions.length);

        if (brush.getMode() == BrushSettings.Mode.GRADATION) {
            if (owner.buildRay(event.x(), event.y()) == null) return false;

            gradationStart = new Vector2f((float) event.x(), (float) event.y());
            gradationEnd.set(gradationStart);
        } else if (!dab(owner, event.x(), event.y(), working)) {
            return false;
        }

        strokeBefore = base;
        strokeWeights = working;
        lastDabX = event.x();
        lastDabY = event.y();

        return true;
    }

    @Override
    public void onMouseDragged(final Model3DViewer owner, final MouseButtonEvent event, final double dx, final double dy) {
        if (strokeBefore == null || strokeWeights == null) return;

        if (gradationStart != null) {
            gradationEnd.set((float) event.x(), (float) event.y());

            Vector2f line = new Vector2f(gradationEnd).sub(gradationStart);
            float lengthSquared = line.lengthSquared();
            float[] working = Arrays.copyOf(strokeBefore, positions.length);

            if (lengthSquared >= 1.0F) {
                IntArrayList indices = new IntArrayList();
                FloatArrayList strengths = new FloatArrayList();
                boolean[] offsets = offsetMask();

                for (int i = 0; i < positions.length; i++) {
                    if (offsets[i]) continue;

                    Vector3f projected = owner.projectToWidget(positions[i]);

                    if (projected == null) continue;

                    float along = ((projected.x - gradationStart.x) * line.x + (projected.y - gradationStart.y) * line.y) / lengthSquared;

                    indices.add(i);
                    strengths.add(1.0F - Math.clamp(along, 0.0F, 1.0F));
                }

                brush.paint(working, indices, strengths);
            }

            strokeWeights = working;
            return;
        }

        if (Math.hypot(event.x() - lastDabX, event.y() - lastDabY) < brush.getSize() * DAB_SPACING) return;

        dab(owner, event.x(), event.y(), strokeWeights);
        lastDabX = event.x();
        lastDabY = event.y();
    }

    @Override
    public void onMouseReleased(final Model3DViewer owner, final MouseButtonEvent event) {
        if (strokeBefore != null && strokeWeights != null && !Arrays.equals(strokeBefore, strokeWeights)) {
            onStrokeCommitted.accept(strokeBefore, strokeWeights);
        }

        strokeBefore = null;
        strokeWeights = null;
        gradationStart = null;
    }

    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        strokeBefore = null;
        strokeWeights = null;
        gradationStart = null;
    }


    @Override
    public int drawOrder() {
        return 20; // after grid (1) and the preview model (10)
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    public void setViewMode(final ViewMode viewMode) {
        this.viewMode = viewMode;
    }

    public void setOnVertexPicked(final IntConsumer onVertexPicked) {
        this.onVertexPicked = onVertexPicked;
    }

    public void setHeatColors(final boolean heatColors) {
        this.heatColors = heatColors;
    }

    public void setPoser(final @Nullable UnaryOperator<Vector3f[]> poser) {
        this.poser = poser;
    }

    /// The mesh position index nearest the camera among those within [#PICK_PIXELS] of the widget position
    /// (`x`, `y`), -1 for none. Depth compares under reversed-Z: greater is nearer.
    public int vertexAt(final Model3DViewer owner, final double x, final double y) {
        int nearest = -1;
        float nearestDepth = -Float.MAX_VALUE;

        for (int i = 0; i < positions.length; i++) {
            Vector3f projected = owner.projectToWidget(positions[i]);

            if (projected == null || projected.z <= nearestDepth) continue;
            if (Math.hypot(projected.x - x, projected.y - y) > PICK_PIXELS) continue;

            nearest = i;
            nearestDepth = projected.z;
        }

        return nearest;
    }

    public void setPaintable(final BooleanSupplier paintable) {
        this.paintable = paintable;
    }

    /// Whether a left press picks a vertex instead of painting.
    public boolean isPickingVertices() {
        return onVertexPicked != null && viewMode != ViewMode.WEIGHT_PAINT;
    }

    /// Applies one dab at the widget position (`x`, `y`) to `working`. True when the press belongs to
    /// the brush: the cursor is over the mesh, or a vertex was painted.
    private boolean dab(final Model3DViewer owner, final double x, final double y, final float[] working) {
        Ray mouseRay = owner.buildRay(x, y);

        if (mouseRay == null) return false;

        float surfaceHit = nearestHit(mouseRay, null);
        IntArrayList indices = new IntArrayList();
        FloatArrayList strengths = new FloatArrayList();
        boolean[] offsets = offsetMask();

        if (brush.getMode() == BrushSettings.Mode.SCREEN) {
            float radius = brush.getSize();

            for (int i = 0; i < positions.length; i++) {
                if (offsets[i]) continue;

                Vector3f projected = owner.projectToWidget(positions[i]);

                if (projected == null) continue;

                float distance = (float) Math.hypot(projected.x - x, projected.y - y);

                if (distance > radius) continue;

                Ray vertexRay = owner.buildRay(projected.x, projected.y);

                if (vertexRay == null) continue;

                float vertexDistance = vertexRay.origin().distance(positions[i]);
                float occluder = nearestHit(vertexRay, null);

                if (occluder >= 0.0F && occluder < vertexDistance * (1.0F - VISIBILITY_TOLERANCE)) continue;

                indices.add(i);
                strengths.add(brush.strengthAt(distance / radius));
            }
        } else if (surfaceHit >= 0.0F) {
            Vector3f center = mouseRay.at(surfaceHit);
            float radius = worldPerPixel(owner, center) * brush.getSize();

            for (int i = 0; radius > 0.0F && i < positions.length; i++) {
                if (offsets[i]) continue;

                float distance = positions[i].distance(center);

                if (distance > radius) continue;

                indices.add(i);
                strengths.add(brush.strengthAt(distance / radius));
            }
        }

        brush.paint(working, indices, strengths);

        return !indices.isEmpty() || surfaceHit >= 0.0F;
    }

    /// Distance along `ray` to the nearest mesh triangle, -1 for a miss.
    /// `normalOut`, when given, takes that triangle's unit normal.
    private float nearestHit(final Ray ray, final @Nullable Vector3f normalOut) {
        float nearest = Float.MAX_VALUE;

        for (int t = 0; t + 2 < triangles.length; t += 3) {
            Vector3f a = positions[triangles[t]];
            Vector3f b = positions[triangles[t + 1]];
            Vector3f c = positions[triangles[t + 2]];
            float distance = ClothMeshGeometry.rayTriangle(ray, a, b, c);

            if (distance >= 0.0F && distance < nearest) {
                nearest = distance;

                if (normalOut != null) b.sub(a, new Vector3f()).cross(c.sub(a, new Vector3f()), normalOut).normalize();
            }
        }

        return nearest == Float.MAX_VALUE ? -1.0F : nearest;
    }

    /// World units one widget pixel spans at `at`'s depth, or 0 when the point does not project.
    private static float worldPerPixel(final Model3DViewer viewer, final Vector3f at) {
        Vector3f projected = viewer.projectToWidget(at);
        Vector3f probe = viewer.projectToWidget(new Vector3f(at).add(viewer.getCameraRight()));

        if (projected == null || probe == null) return 0.0F;

        float pixelsPerUnit = (float) Math.hypot(probe.x - projected.x, probe.y - projected.y);

        return pixelsPerUnit < 1.0e-3F ? 0.0F : 1.0F / pixelsPerUnit;
    }

    /// A cursor circle around `center`, spanned by `axisA` and `axisB` — each already the radius long.
    private static void emitCircle(
        final BufferBuilder builder, final PoseStack.Pose pose, final Vector3f eye,
        final Vector3f center, final Vector3f axisA, final Vector3f axisB
    ) {
        Vector3f previous = new Vector3f(center).add(axisA);

        for (int s = 1; s <= CURSOR_SEGMENTS; s++) {
            double angle = Math.PI * 2.0D * s / CURSOR_SEGMENTS;
            Vector3f current = new Vector3f(center)
                .add(new Vector3f(axisA).mul((float) Math.cos(angle)))
                .add(new Vector3f(axisB).mul((float) Math.sin(angle)));

            ClothMeshGeometry.emitLine(
                builder, pose, ClothMeshGeometry.lifted(previous, eye), COLOR_CURSOR, ClothMeshGeometry.lifted(current, eye), COLOR_CURSOR
            );
            previous = current;
        }
    }

    /// Whether each mesh position is the offset vertex of a pair.
    private boolean[] offsetMask() {
        boolean[] mask = new boolean[positions.length];
        int[] pairs = offsetPairs.get();

        for (int k = 1; k < pairs.length; k += 2) {
            if (pairs[k] < mask.length) mask[pairs[k]] = true;
        }

        return mask;
    }

    private int colorOf(final float[] shown, final boolean[] offsets, final int position) {
        float weight = position < shown.length ? shown[position] : 0.0F;

        if (position < offsets.length && offsets[position]) return COLOR_KINEMATIC;

        // Hue runs from blue down to red as the weight rises
        if (heatColors) return 0xFF000000 | Mth.hsvToRgb((1.0F - Math.clamp(weight, 0.0F, 1.0F)) * 2.0F / 3.0F, 1.0F, 1.0F);
        if (weight == 0.0F) return COLOR_KINEMATIC;

        int gray = Math.round(Math.clamp(weight, 0.0F, 1.0F) * 255.0F);

        return 0xFF000000 | gray << 16 | gray << 8 | gray;
    }
}
