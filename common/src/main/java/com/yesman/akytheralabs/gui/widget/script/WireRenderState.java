package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// An antialiased polyline drawn through the plain [RenderPipelines#GUI] pipeline: a solid core
/// `halfWidth` to each side of the path, fading to transparent over `feather` more. Joints are mitered,
/// so the strip has no gaps or overlaps for the fade to double up on.
///
/// @param points    `x, y` pairs, in the space `pose` maps to the screen
/// @param halfWidth half the width of the solid core, in GUI pixels
/// @param feather   width of the fade on each side, in GUI pixels
public record WireRenderState(
    Matrix3x2fc pose,
    float[] points,
    float halfWidth,
    float feather,
    int color,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    /// Longest a miter may grow at a sharp joint, as a multiple of the width it keeps.
    private static final float MITER_LIMIT = 2.0F;

    /// Points closer than this to the one before, in GUI pixels, are dropped.
    private static final float MIN_STEP = 0.001F;

    public WireRenderState(
        Matrix3x2fc pose, float[] points, float halfWidth, float feather, int color, @Nullable ScreenRectangle scissorArea
    ) {
        this(pose, distinctPoints(points), halfWidth, feather, color, scissorArea, null);
    }

    public WireRenderState {
        if (bounds == null) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

            for (int i = 0; i < points.length; i += 2) {
                minX = Math.min(minX, points[i]);
                maxX = Math.max(maxX, points[i]);
                minY = Math.min(minY, points[i + 1]);
                maxY = Math.max(maxY, points[i + 1]);
            }

            float reach = (halfWidth + feather) * MITER_LIMIT;
            int left = (int) Math.floor(minX - reach);
            int top = (int) Math.floor(minY - reach);
            ScreenRectangle area = new ScreenRectangle(
                left, top, (int) Math.ceil(maxX + reach) - left, (int) Math.ceil(maxY + reach) - top
            ).transformMaxBounds(pose);

            bounds = scissorArea != null ? scissorArea.intersection(area) : area;
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        int count = points.length / 2;

        if (count < 2) return;

        // Per point: the offset of the solid core's edge from the path, and of the fade's outer edge.
        float[] coreX = new float[count];
        float[] coreY = new float[count];
        float[] outerX = new float[count];
        float[] outerY = new float[count];

        for (int i = 0; i < count; i++) {
            int in = Math.max(i - 1, 0);
            int out = Math.min(i + 1, count - 1);
            float inX = points[i * 2] - points[in * 2];
            float inY = points[i * 2 + 1] - points[in * 2 + 1];
            float outX = points[out * 2] - points[i * 2];
            float outY = points[out * 2 + 1] - points[i * 2 + 1];
            float inLength = (float) Math.hypot(inX, inY);
            float outLength = (float) Math.hypot(outX, outY);

            if (i == 0) {
                inX = outX;
                inY = outY;
                inLength = outLength;
            } else if (i == count - 1) {
                outX = inX;
                outY = inY;
                outLength = inLength;
            }

            inX /= inLength;
            inY /= inLength;
            outX /= outLength;
            outY /= outLength;

            float tangentX = inX + outX;
            float tangentY = inY + outY;
            float tangentLength = (float) Math.hypot(tangentX, tangentY);

            // A path turning straight back on itself has no joint direction; fall back to the outgoing run.
            if (tangentLength < MIN_STEP) {
                tangentX = outX;
                tangentY = outY;
            } else {
                tangentX /= tangentLength;
                tangentY /= tangentLength;
            }

            float normalX = -tangentY;
            float normalY = tangentX;
            float miter = 1.0F / Math.max(1.0F / MITER_LIMIT, normalX * -outY + normalY * outX);

            coreX[i] = normalX * halfWidth * miter;
            coreY[i] = normalY * halfWidth * miter;
            outerX[i] = normalX * (halfWidth + feather) * miter;
            outerY[i] = normalY * (halfWidth + feather) * miter;
        }

        int clear = color & 0x00FFFFFF;

        for (int i = 0; i < count - 1; i++) {
            int j = i + 1;
            float ax = points[i * 2];
            float ay = points[i * 2 + 1];
            float bx = points[j * 2];
            float by = points[j * 2 + 1];

            quad(vertexConsumer,
                ax + outerX[i], ay + outerY[i], clear,
                bx + outerX[j], by + outerY[j], clear,
                bx + coreX[j], by + coreY[j], color,
                ax + coreX[i], ay + coreY[i], color);
            quad(vertexConsumer,
                ax + coreX[i], ay + coreY[i], color,
                bx + coreX[j], by + coreY[j], color,
                bx - coreX[j], by - coreY[j], color,
                ax - coreX[i], ay - coreY[i], color);
            quad(vertexConsumer,
                ax - coreX[i], ay - coreY[i], color,
                bx - coreX[j], by - coreY[j], color,
                bx - outerX[j], by - outerY[j], clear,
                ax - outerX[i], ay - outerY[i], clear);
        }
    }

    /// Emits the quad `a b c d` wound the way [RenderPipelines#GUI] draws its own rectangles, whichever way
    /// the four corners were given.
    private void quad(
        VertexConsumer consumer,
        float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc, float dx, float dy, int dc
    ) {
        float area = (ax * by - bx * ay) + (bx * cy - cx * by) + (cx * dy - dx * cy) + (dx * ay - ax * dy);

        if (area > 0.0F) {
            consumer.addVertexWith2DPose(pose, ax, ay).setColor(ac);
            consumer.addVertexWith2DPose(pose, dx, dy).setColor(dc);
            consumer.addVertexWith2DPose(pose, cx, cy).setColor(cc);
            consumer.addVertexWith2DPose(pose, bx, by).setColor(bc);
        } else {
            consumer.addVertexWith2DPose(pose, ax, ay).setColor(ac);
            consumer.addVertexWith2DPose(pose, bx, by).setColor(bc);
            consumer.addVertexWith2DPose(pose, cx, cy).setColor(cc);
            consumer.addVertexWith2DPose(pose, dx, dy).setColor(dc);
        }
    }

    /// `points` without the points lying on the one before them.
    private static float[] distinctPoints(float[] points) {
        float[] kept = new float[points.length];
        int size = 0;

        for (int i = 0; i + 1 < points.length; i += 2) {
            if (size > 0 && Math.abs(points[i] - kept[size - 2]) < MIN_STEP && Math.abs(points[i + 1] - kept[size - 1]) < MIN_STEP) {
                continue;
            }

            kept[size++] = points[i];
            kept[size++] = points[i + 1];
        }

        return Arrays.copyOf(kept, size);
    }
}
