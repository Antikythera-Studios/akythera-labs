package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.core.animation.driver.BlendSpace2DDriver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Reads the driver's live control point — the smoothed position it samples the node space at, which
/// is what the editor's preview cursor mirrors.
@Mixin(BlendSpace2DDriver.class)
public interface BlendSpace2DDriverAccessor {
    @Accessor("targetX")
    float akytheralabs$getTargetX();

    @Accessor("targetY")
    float akytheralabs$getTargetY();
}
