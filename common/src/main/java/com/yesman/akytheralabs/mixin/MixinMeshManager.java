package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeshManager.class)
public abstract class MixinMeshManager {
    @Inject(
        method = "staticMesh",
        at = @At("HEAD"),
        cancellable = true
    )
    private static <T extends StaticMesh> void akytheralabs$redirectStaticMeshToEditor(
        String namespace, String path,
        MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor,
        CallbackInfoReturnable<AssetAccessor<T>> cir
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return;
        if (!namespace.equals(project.getProjectName())) return;

        Identifier fullId = Identifier.fromNamespaceAndPath(namespace, MeshManager.DIRECTORY + path);
        cir.setReturnValue(project.getOrIssueEditorStaticMeshAccessor(fullId, meshConstructor));
    }

    @Inject(
        method = "skeletalMesh",
        at = @At("HEAD"),
        cancellable = true
    )
    private static <T extends SkeletalMesh> void akytheralabs$redirectSkeletalMeshToEditor(
        String namespace, String path,
        MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor,
        CallbackInfoReturnable<AssetAccessor<T>> cir
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return;
        if (!namespace.equals(project.getProjectName())) return;

        Identifier fullId = Identifier.fromNamespaceAndPath(namespace, MeshManager.DIRECTORY + path);
        cir.setReturnValue(project.getOrIssueEditorSkeletalMeshAccessor(fullId, meshConstructor));
    }
}