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

/// An emitter's pivot, as a small cyan wireframe octahedron with a line to the emitter's cube.
public final class EmitterPivotComponent extends EmitterMarkerComponent {
    /// From the octahedron's center to each of its tips, in blocks.
    private static final double TIP = 0.12D;

    public EmitterPivotComponent(
        final ParticleEmitterHolder emitter, final Consumer<ParticleEmitterHolder> onPicked, final Runnable onTransformWritten, final BooleanSupplier shown
    ) {
        super(emitter, onPicked, onTransformWritten, shown, 0xFF33CCFF, 0xFFAAEEFF);
    }

    @Override
    protected TransformHolder editedTransform() {
        return emitter.getPivot();
    }

    @Override
    public Transform parentTransform() {
        return Transform.IDENTITY;
    }

    @Override
    protected void emitLines(final VertexConsumer consumer, final PoseStack.Pose pose, final Transform world, final int color) {
        Vec3[] tips = {
            world.mul(new Vec3(TIP, 0.0D, 0.0D)), world.mul(new Vec3(-TIP, 0.0D, 0.0D)),
            world.mul(new Vec3(0.0D, TIP, 0.0D)), world.mul(new Vec3(0.0D, -TIP, 0.0D)),
            world.mul(new Vec3(0.0D, 0.0D, TIP)), world.mul(new Vec3(0.0D, 0.0D, -TIP))
        };

        // Tips 2i and 2i + 1 are opposite; every other pair is an edge
        for (int i = 0; i < tips.length; i++) {
            for (int j = i + 1; j < tips.length; j++) {
                if (j != (i ^ 1)) ColliderWireframe.line(consumer, pose, tips[i], tips[j], color);
            }
        }

        ColliderWireframe.line(consumer, pose, world.loc(), emitter.getPivot().makeObject().mul(emitter.getTransform().makeObject()).loc(), color);
    }
}
