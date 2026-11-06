package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dev.ViewportCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /// Moves the camera to the placed [ViewportCamera] once it has followed its entity, before the view and culling
    /// matrices are built from it.
    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
            shift = At.Shift.AFTER
        ),
        method = "update(Lnet/minecraft/client/DeltaTracker;)V"
    )
    private void akytheralabs$placeViewportCamera(final DeltaTracker deltaTracker, final CallbackInfo callbackInfo) {
        ViewportCamera.Placement placement = AkytheraEditorLayoutManager.getInstance().getViewportCamera()
            .placement(deltaTracker.getGameTimeDeltaPartialTick(true));

        if (placement == null) return;

        setPosition(placement.position());
        setRotation(placement.yaw(), placement.pitch());
    }
}
