package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.core.renderer.collider.ColliderWireframe;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// An emitter's transform, as a small orange wireframe cube in its pivot's space.
public final class EmitterTransformComponent extends EmitterMarkerComponent {
    /// Half the cube's edge, in blocks.
    private static final double HALF_EDGE = 0.1D;

    public EmitterTransformComponent(
        final ParticleEmitterHolder emitter, final Consumer<ParticleEmitterHolder> onPicked, final Runnable onTransformWritten, final BooleanSupplier shown
    ) {
        super(emitter, onPicked, onTransformWritten, shown, 0xFFFF9922, 0xFFFFDD88);
    }

    @Override
    protected TransformHolder editedTransform() {
        return emitter.getTransform();
    }

    @Override
    public Transform parentTransform() {
        return emitter.getPivot().makeObject();
    }

    @Override
    protected void emitLines(final VertexConsumer consumer, final PoseStack.Pose pose, final Transform world, final int color) {
        Vec3[] corners = new Vec3[8];

        // Bit 0 is the sign along x, bit 1 along y, bit 2 along z, so two corners share an edge exactly when their
        // indices differ in one bit
        for (int i = 0; i < corners.length; i++) {
            corners[i] = world.mul(new Vec3(
                (i & 1) == 0 ? -HALF_EDGE : HALF_EDGE,
                (i & 2) == 0 ? -HALF_EDGE : HALF_EDGE,
                (i & 4) == 0 ? -HALF_EDGE : HALF_EDGE
            ));
        }

        for (int i = 0; i < corners.length; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) == 0) ColliderWireframe.line(consumer, pose, corners[i], corners[i | bit], color);
            }
        }
    }
}
