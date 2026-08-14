package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimationManager.class)
public abstract class MixinAnimationManager {
    @Inject(
        method = "accessor(Lnet/minecraft/resources/Identifier;)Lcom/yesman/akythera/api/asset/AssetAccessor;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void akytheralabs$redirectToEditor(
        Identifier path,
        CallbackInfoReturnable<AssetAccessor<AnimationClip>> cir
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return;
        if (!path.getNamespace().equals(project.getProjectName())) return;

        Identifier fullId = Identifier.fromNamespaceAndPath(
            path.getNamespace(),
            AnimationManager.DIRECTORY + path.getPath()
        );

        cir.setReturnValue(project.getOrIssueEditorAnimationAccessor(fullId));
    }

    @Inject(
        method = "accessor(Ljava/lang/String;Ljava/lang/String;Lcom/yesman/akythera/api/asset/AssetAccessor;)Lcom/yesman/akythera/api/asset/AssetAccessor;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void akytheralabs$redirectExplicitArmatureToEditor(
        String namespace, String animPath, AssetAccessor<? extends Armature> armature,
        CallbackInfoReturnable<AssetAccessor<AnimationClip>> cir
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return;
        if (!namespace.equals(project.getProjectName())) return;

        Identifier fullId = Identifier.fromNamespaceAndPath(
            namespace,
            armature.identifier().getPath().replaceAll("armatures/", "animationsToReplace/") + "/" + animPath
        );
        cir.setReturnValue(project.getOrIssueEditorAnimationAccessor(fullId));
    }
}