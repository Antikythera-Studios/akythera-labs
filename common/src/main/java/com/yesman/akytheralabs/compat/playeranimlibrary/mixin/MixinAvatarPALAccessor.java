package com.yesman.akytheralabs.compat.playeranimlibrary.mixin;

import com.zigythebird.playeranim.animation.AvatarAnimManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/// Accessor into the members PAL's `AvatarMixin` merges into [Avatar], so
/// [MixinProjectReloader] can rebuild the avatar's animation stack after a project reload.
///
/// The targets are another mod's `@Unique` members, which only exist in the class once PAL's
/// mixin has been applied — `priority = 2000` orders this mixin after PAL's (default 1000).
/// `remap = false` everywhere: the names are mixin-added, not mappings.
@Mixin(value = Avatar.class, priority = 2000)
public interface MixinAvatarPALAccessor {
    /// PAL's factory: builds a fresh [AvatarAnimManager], runs
    /// `PlayerAnimationFactory#prepareAnimations` (which refills the mod-animation map) and
    /// re-fires `PlayerAnimationAccess.REGISTER_ANIMATION_EVENT` on it.
    @Invoker(value = "playerAnimLib$createAnimationStack", remap = false)
    AvatarAnimManager akytheralabs$createAnimationStack();

    /// PAL's per-avatar mod-animation lookup map, so a reload can drop stale entries whose
    /// factory no longer produces an animation (e.g. the entity patch was removed).
    @Accessor(value = "playerAnimLib$modAnimationData", remap = false)
    Map<Identifier, ?> akytheralabs$getModAnimationData();

    /// The manager field is final (initialized inline on PAL's mixin) — `@Mutable` strips the
    /// flag so the reload can swap in the freshly built stack.
    @Mutable
    @Accessor(value = "playerAnimLib$animationManager", remap = false)
    void akytheralabs$setAnimationManager(AvatarAnimManager manager);
}