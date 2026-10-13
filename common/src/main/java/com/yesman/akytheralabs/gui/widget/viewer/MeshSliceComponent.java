package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akytheralabs.akythera.asset.ModelSkinHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/// Picks where a mesh part is sliced: an orange rectangle runs around the part under the cursor, across its
/// longest axis, and a left press reports it. A right press reports that the pick was given up.
public final class MeshSliceComponent implements Model3DViewerComponent {
    private static final int COLOR_SLICE = 0xFFFFA028;

    /// The slice a ray makes, null where it hits no part.
    private final Function<Ray, ModelSkinHolder.@Nullable Slice> sliceAt;

    private final Consumer<ModelSkinHolder.Slice> onPicked;
    private final Runnable onCancelled;

    public MeshSliceComponent(
        final Function<Ray, ModelSkinHolder.@Nullable Slice> sliceAt,
        final Consumer<ModelSkinHolder.Slice> onPicked,
        final Runnable onCancelled
    ) {
        this.sliceAt = sliceAt;
        this.onPicked = onPicked;
        this.onCancelled = onCancelled;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        Ray ray = viewer.buildRay(mouseX, mouseY);
        ModelSkinHolder.Slice slice = ray == null ? null : sliceAt.apply(ray);

        if (slice == null) return;

        // The two axes the rectangle spans
        int first = (slice.axis() + 1) % 3;
        int second = (slice.axis() + 2) % 3;
        Vector3f[] corners = new Vector3f[4];

        for (int corner = 0; corner < 4; corner++) {
            corners[corner] = new Vector3f()
                .setComponent(slice.axis(), slice.coordinate())
                .setComponent(first, (corner == 0 || corner == 3 ? slice.min() : slice.max()).get(first))
                .setComponent(second, (corner < 2 ? slice.min() : slice.max()).get(second));
        }

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        for (int corner = 0; corner < 4; corner++) {
            ClothMeshGeometry.emitLine(
                builder, poseStack.last(),
                ClothMeshGeometry.lifted(corners[corner], ray.origin()), COLOR_SLICE,
                ClothMeshGeometry.lifted(corners[(corner + 1) % 4], ray.origin()), COLOR_SLICE
            );
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Takes the press when it lands on a part, and reports the slice there.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        Ray ray = event.button() == 0 ? owner.buildRay(event.x(), event.y()) : null;
        ModelSkinHolder.Slice slice = ray == null ? null : sliceAt.apply(ray);

        if (slice == null) return false;

        onPicked.accept(slice);

        return true;
    }

    @Override
    public void showOptions(final Model3DViewer owner) {
        onCancelled.run();
    }

    @Override
    public int drawOrder() {
        return 30; // after the mesh (20)
    }
}
