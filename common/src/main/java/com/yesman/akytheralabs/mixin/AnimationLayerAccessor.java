package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Reads a layer's base driver, whose [AnimationDriver#getPlayingAnimation] and
/// [AnimationDriver#getTime] carry the entity's live playback.
@Mixin(AnimationLayer.class)
public interface AnimationLayerAccessor {
    @Accessor("animDriver")
    AnimationDriver akytheralabs$getAnimDriver();
}
