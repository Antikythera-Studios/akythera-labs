package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// The distance a sound playing at the origin is heard from, as a wireframe sphere, plus the camera's
/// distance to the origin and the gain [SoundEngine]'s linear attenuation gives the sound there.
public final class AttenuationSphereComponent implements Model3DViewerComponent {
    private static final int SPHERE_COLOR = 0xFF8BC24A;
    private static final int ORIGIN_COLOR = 0xFFFFCC00;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final float LINE_WIDTH = 2.0F;

    /// Segments per circle.
    private static final int SEGMENTS = 48;

    /// Latitude rings between the poles, the equator among them.
    private static final int LATITUDES = 5;

    /// Half-length of the origin marker's axes, in blocks.
    private static final float ORIGIN_MARKER = 0.25F;

    private final Font font;

    /// The sphere's radius in blocks, or null to draw the origin marker alone.
    private final Supplier<@Nullable Float> radius;

    public AttenuationSphereComponent(Font font, Supplier<@Nullable Float> radius) {
        this.font = font;
        this.radius = radius;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        addSegment(builder, pose, new Vector3f(-ORIGIN_MARKER, 0.0F, 0.0F), new Vector3f(ORIGIN_MARKER, 0.0F, 0.0F), ORIGIN_COLOR);
        addSegment(builder, pose, new Vector3f(0.0F, -ORIGIN_MARKER, 0.0F), new Vector3f(0.0F, ORIGIN_MARKER, 0.0F), ORIGIN_COLOR);
        addSegment(builder, pose, new Vector3f(0.0F, 0.0F, -ORIGIN_MARKER), new Vector3f(0.0F, 0.0F, ORIGIN_MARKER), ORIGIN_COLOR);

        Float r = radius.get();

        if (r != null && r > 0.0F) {
            addCircle(builder, pose, 0.0F, r, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F));
            addCircle(builder, pose, 0.0F, r, new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, 1.0F, 0.0F));

            for (int i = 1; i <= LATITUDES; i++) {
                float latitude = Mth.PI * i / (LATITUDES + 1) - Mth.HALF_PI;

                addCircle(
                    builder, pose,
                    r * Mth.sin(latitude), r * Mth.cos(latitude),
                    new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F)
                );
            }
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, int mouseX, int mouseY, float a) {
        Vector3f origin = model3DViewer.toViewSpace(new Vector3f());

        if (origin == null) return;

        float distance = origin.length();
        Float r = radius.get();
        int x = model3DViewer.getX() + 6;
        int y = model3DViewer.getY() + 6;

        graphics.text(font, Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_OVERLAY_DISTANCE, String.format("%.1f", distance)), x, y, TEXT_COLOR, false);

        if (r == null || r <= 0.0F) return;

        float gain = Mth.clamp(1.0F - distance / r, 0.0F, 1.0F); // AL_LINEAR_DISTANCE from the origin to `r`

        graphics.text(font, Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_OVERLAY_ATTENUATION, String.format("%.1f", r)), x, y + font.lineHeight + 2, TEXT_COLOR, false);
        graphics.text(font, Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_OVERLAY_GAIN, Math.round(gain * 100.0F)), x, y + (font.lineHeight + 2) * 2, TEXT_COLOR, false);
    }

    /// A circle of `circleRadius` around the vertical axis at `height`, spanned by `u` and `v`.
    private static void addCircle(BufferBuilder builder, PoseStack.Pose pose, float height, float circleRadius, Vector3f u, Vector3f v) {
        Vector3f previous = null;

        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = Mth.TWO_PI * i / SEGMENTS;
            Vector3f point = new Vector3f(u).mul(Mth.cos(angle) * circleRadius)
                .add(new Vector3f(v).mul(Mth.sin(angle) * circleRadius))
                .add(0.0F, height, 0.0F);

            if (previous != null) addSegment(builder, pose, previous, point, SPHERE_COLOR);

            previous = point;
        }
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    private static void addSegment(BufferBuilder builder, PoseStack.Pose pose, Vector3f from, Vector3f to, int color) {
        Vector3f direction = new Vector3f(to).sub(from).normalize();

        builder.addVertex(pose, from.x, from.y, from.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
        builder.addVertex(pose, to.x, to.y, to.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
    }

    @Override
    public int drawOrder() {
        return 40; // after grid (1)
    }
}
