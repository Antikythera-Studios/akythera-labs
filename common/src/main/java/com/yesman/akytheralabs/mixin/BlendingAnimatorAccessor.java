package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// Reads the animator's layers and the script driving it, for the editor screens that mirror a
/// possessed entity.
@Mixin(BlendingAnimator.class)
public interface BlendingAnimatorAccessor {
    @Accessor("animLayers")
    Map<AnimationLayerSignature, AnimationLayer> akytheralabs$getAnimLayers();

    @Accessor("animScript")
    AnimatorScript akytheralabs$getAnimScript();
}
