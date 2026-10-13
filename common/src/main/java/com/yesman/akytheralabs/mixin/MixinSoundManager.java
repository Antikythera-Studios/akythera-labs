package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.ProjectSounds;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/// Lets the game play the open project's sounds, see [ProjectSounds].
@Mixin(SoundManager.class)
public abstract class MixinSoundManager {
    @Shadow @Final
    private Map<Identifier, WeighedSoundEvents> registry;

    /// The sound engine's buffers read the open project's sound files over the resource packs'.
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceProvider;fromMap(Ljava/util/Map;)Lnet/minecraft/server/packs/resources/ResourceProvider;"
        )
    )
    private static ResourceProvider akytheralabs$readProjectFiles(Map<Identifier, Resource> soundCache) {
        return ProjectSounds.withProjectFiles(ResourceProvider.fromMap(soundCache));
    }

    @Inject(method = "getSoundEvent", at = @At("HEAD"), cancellable = true)
    private void akytheralabs$resolveProjectEvent(Identifier location, CallbackInfoReturnable<WeighedSoundEvents> callbackInfo) {
        WeighedSoundEvents projectEvent = ProjectSounds.resolve(location, registry.get(location));

        if (projectEvent != null) callbackInfo.setReturnValue(projectEvent);
    }
}
