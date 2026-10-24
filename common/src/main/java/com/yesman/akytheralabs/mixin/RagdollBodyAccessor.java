package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Reads where a rag doll body stands, for the grab [MixinRagdollSimulation] adds.
@Mixin(RagdollSimulation.Body.class)
public interface RagdollBodyAccessor {
    @Accessor("position")
    Vector3d akytheralabs$getPosition();

    @Accessor("rotation")
    Quaterniond akytheralabs$getRotation();

    @Accessor("centerOfMass")
    Vector3d akytheralabs$getCenterOfMass();
}
