package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.dev.ProjectReloader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(ServerPacksSource.class)
public abstract class MixinServerPacksSource {
    /// A world opens: clear the temporary packs a crash kept from being deleted, before the world finds them.
    @Inject(
        at = @At("HEAD"),
        method = "createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;"
    )
    private static void onCreatePackRepository(Path datapackDir, DirectoryValidator validator, CallbackInfoReturnable<PackRepository> callbackInfo) {
        ProjectReloader.deleteStaleTempPacks(datapackDir);
    }
}
