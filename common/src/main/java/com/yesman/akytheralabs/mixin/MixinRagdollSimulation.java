package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akytheralabs.mixinaccessor.RagdollSimulationAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Adds the editor's hand grip to [RagdollSimulation], see [RagdollSimulationAccessor].
@Mixin(RagdollSimulation.class)
public abstract class MixinRagdollSimulation implements RagdollSimulationAccessor {
    @Shadow
    @Final
    private Vec3 origin;

    @Shadow
    private int calmTicks;

    /// The body [#akytheralabs$grab] holds, null for none.
    @Unique
    private RagdollSimulation.@Nullable Body akytheralabs$grabbed;

    /// Where [#akytheralabs$grabbed] holds the head of its bone, relative to [#origin].
    @Unique
    private final Vector3d akytheralabs$grabTarget = new Vector3d();

    @Shadow
    private RagdollSimulation.Body bodyOf(final String boneName) {
        throw new AssertionError();
    }

    @Shadow
    private void wake() {
        throw new AssertionError();
    }

    @Shadow
    private static double correctPosition(
        final RagdollSimulation.Body a, final Vector3d armA, final RagdollSimulation.@Nullable Body b, final @Nullable Vector3d armB,
        final Vector3d direction, final double error
    ) {
        throw new AssertionError();
    }

    @Override
    public void akytheralabs$grab(final String boneName, final Vec3 worldTarget) {
        akytheralabs$grabbed = bodyOf(boneName);
        akytheralabs$grabTarget.set(worldTarget.x - origin.x, worldTarget.y - origin.y, worldTarget.z - origin.z);
        wake();
    }

    @Override
    public void akytheralabs$release() {
        akytheralabs$grabbed = null;
    }

    /// Pulls the held body's bone head onto the target once per substep, before the joints, and keeps the
    /// simulation from resting while it is held.
    @Inject(
        at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", shift = At.Shift.AFTER),
        method = "substep(D)V"
    )
    private void akytheralabs$holdGrabbed(final double h, final CallbackInfo callbackInfo) {
        RagdollSimulation.Body grabbed = akytheralabs$grabbed;

        if (grabbed == null) return;

        calmTicks = 0;

        RagdollBodyAccessor body = (RagdollBodyAccessor) (Object) grabbed;
        Vector3d arm = body.akytheralabs$getRotation().transform(body.akytheralabs$getCenterOfMass(), new Vector3d()).negate();
        Vector3d gap = new Vector3d(akytheralabs$grabTarget).sub(body.akytheralabs$getPosition()).sub(arm);
        double distance = gap.length();

        if (distance > 1.0E-9D) correctPosition(grabbed, arm, null, null, gap.div(distance), distance);
    }
}
