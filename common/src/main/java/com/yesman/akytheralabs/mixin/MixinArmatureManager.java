package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmatureManager.class)
public abstract class MixinArmatureManager {
    @Inject(
        method = "accessor",
        at = @At("HEAD"),
        cancellable = true
    )
    private static <T extends Armature> void akytheralabs$redirectArmatureToEditor(
        String namespace, String path,
        ArmatureLoader.ArmatureConstructor<T> armatureConstructor,
        CallbackInfoReturnable<AssetAccessor<T>> cir
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return;
        if (!namespace.equals(project.getProjectName())) return;

        Identifier fullId = Identifier.fromNamespaceAndPath(namespace, ArmatureManager.DIRECTORY + path);
        cir.setReturnValue(project.getOrIssueEditorArmatureAccessor(fullId, armatureConstructor));
    }
}