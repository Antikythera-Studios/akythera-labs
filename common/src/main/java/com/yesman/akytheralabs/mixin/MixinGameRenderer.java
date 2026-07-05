package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    private Minecraft minecraft;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V",
            ordinal = 1 // When push "gui", before call `minecraft.gui#extractRenderState`
        ),
        locals = LocalCapture.CAPTURE_FAILSOFT,
        method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractGui(
       DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo callbackInfo,
       // Local variable captures
       ProfilerFiller profiler,
       int xMouse,
       int yMouse,
       GuiGraphicsExtractor graphics
    ) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            // A tricky resolution. If we copy the main render target to another framebuffer (AkytheraEditorLayoutManager#editorRenderTarget),
            // fragments with alpha == 0.0 will be displayed in black, not the fog color set by LevelRenderState.CameraRenderState.fogData.
            //
            // To resolve the issue, this applies a weak dimmer to the whole screen to remove alpha channels, as it was
            // turned out that the issue was gone when pause screen was being displayed. But we definitely need to find better solution
            // in the future (I just didn't want to spend more a day on this minor issue)
            graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), 0x10000000);
        }
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"
        ),
        method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractGui(GuiGraphicsExtractor guiGraphics, Window window) {
        if (!AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            // Only apply the cursor when mouse pointer in gameplay viewport
            // Otherwise cursor is applied on Akythera render pipeline
            guiGraphics.applyCursor(window);
        }
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
        ),
        method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractToasts(ToastManager toastManager, GuiGraphicsExtractor guiGraphics) {
        // While the editor is active, skip the in-game toast extraction — it would land in the world
        // viewport at the game gui scale. AkytheraEditorLayoutManager#renderEditorLayout re-extracts
        // toasts into the editor layout viewport (editor render target + editor gui scale) instead.
        if (AkytheraEditorLayoutManager.getInstance().isActive()) return;
        toastManager.extractRenderState(guiGraphics);
    }

    @Inject(
        at = @At("TAIL"),
        method = "resize(II)V"
    )
    private void onResize(int width, int height, CallbackInfo ci) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int calculatedGuiScale = minecraft.getWindow().calculateScale(
                minecraft.options.guiScale().get(),
                minecraft.isEnforceUnicode()
            );

            AkytheraEditorLayoutManager.getInstance().onScreenResized(width, height, calculatedGuiScale);
        }
    }
}
