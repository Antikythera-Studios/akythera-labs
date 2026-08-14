package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.CachedPoseReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/// Reaches the driver a pose cache points at. `CachedPoseReference` stands in front of that driver and
/// forwards to it, so a caller that needs the driver's own type has to step through.
@Mixin(CachedPoseReference.class)
public interface CachedPoseReferenceAccessor {
    @Invoker("ref")
    AnimationDriver akytheralabs$ref();
}
