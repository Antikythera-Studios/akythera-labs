package com.yesman.akytheralabs.compat.playeranimlibrary.mixin;

import com.yesman.akytheralabs.gui.panel.main.WorldviewPanel;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// PAL keeps one `AvatarAnimManager` per [Avatar], built once at construction — it never sees
/// data reloaded afterwards. When the Worldview reload renews every visible entity's patch,
/// rebuild that stack too, so PAL layers registered from patch data (the engine's factory in
/// `PlayerAnimationLibraryCompatibility`) pick up the renewed entries.
///
/// Targets our own [WorldviewPanel] rather than living in it because PAL is an optional
/// dependency: this class only loads when [com.yesman.akytheralabs.compat.playeranimlibrary.PALMixinPlugin]
/// confirms PAL is installed, keeping PAL types off the panel's classpath.
@Mixin(WorldviewPanel.class)
public abstract class MixinWorldviewPanel {
    /// `HEAD`, not `TAIL`: [WorldviewPanel#renewEntityPatch] returns early for entities without
    /// an engine patch, but the PAL stack must refresh even then — e.g. when the reload is what
    /// removed the avatar's patch.
    @Inject(method = "renewEntityPatch", at = @At("TAIL"))
    private static void akytheralabs$renewAnimationStack(Entity entity, CallbackInfo ci) {
        // PAL only drives client-side avatars; renewEntityPatch also runs for server entities.
        if (!(entity instanceof Avatar avatar) || !avatar.level().isClientSide()) {
            return;
        }

        MixinAvatarPALAccessor accessor = (MixinAvatarPALAccessor) avatar;
        accessor.akytheralabs$getModAnimationData().clear();
        accessor.akytheralabs$setAnimationManager(accessor.akytheralabs$createAnimationStack());
    }
}