package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dev.ProjectReloader;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Final
    @Shadow
    public MouseHandler mouseHandler;

    @Final
    @Shadow
    private Window window;

    @Final
    @Shadow
    private DeltaTracker.Timer deltaTracker;

    @Final
    @Shadow
    public Options options;

    /// Frees `F2` for the editor's rename shortcut. [Minecraft#handleGlobalKeyPress] runs in
    /// `KeyboardHandler#keyPress` *before* the screen is offered the key and swallows the whole
    /// press when it returns true, so a screenshot grab would otherwise be the only thing `F2`
    /// ever does. Only the screenshot branch is answered — fullscreen and the friends key keep
    /// working while the editor is up.
    @Inject(
        at = @At("HEAD"),
        method = "handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z",
        cancellable = true
    )
    private void onHandleGlobalKeyPress(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()
            && (options.keyScreenshot.matches(key) || options.keyFriends.matches(key))
        ) {
            callbackInfo.setReturnValue(false);
        }
    }

    /// The frame's one and only present. 26.2 replaced `RenderTarget#blitToScreen` with a blit off
    /// the window surface, and the surface rejects a second blit in the same frame — so the editor
    /// layout is drawn here and its target presented *instead of* the game's, not after it.
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"
        ),
        method = "renderFrame(Z)V"
    )
    private void renderFrame(GpuSurface surface, CommandEncoder commandEncoder, GpuTextureView colorTexture) {
        AkytheraEditorLayoutManager layoutManager = AkytheraEditorLayoutManager.getInstance();
        boolean layoutShown = layoutManager.isLayoutShown();

        if (layoutShown) {
            int xMouse = (int)mouseHandler.getScaledXPos(window);
            int yMouse = (int)mouseHandler.getScaledYPos(window);

            layoutManager.renderEditorLayout(xMouse, yMouse, deltaTracker.getGameTimeDeltaTicks());

            surface.blitFromTexture(commandEncoder, layoutManager.getEditorRenderTarget().getColorTextureView());
        } else {
            surface.blitFromTexture(commandEncoder, colorTexture);
        }

        layoutManager.markPresented(layoutShown);
    }

    @Inject(
        at = @At("TAIL"),
        method = "tick()V"
    )
    private void onTick(CallbackInfo callbackInfo) {
        EntityDebugSession.getInstance().clientTick();
    }

    /// Ends the open project of a world the player has left while the game keeps running. `TAIL`: the
    /// integrated server has shut down, so the world no longer holds the data pack open.
    @Inject(
        at = @At("TAIL"),
        method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V"
    )
    private void onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo callbackInfo) {
        if (Minecraft.getInstance().isRunning() && AkytheraProject.getCurrentProjectOrNull() != null) {
            AkytheraProject.endProject();
        }
    }

    /// Lets [ProjectReloader] append its server steps to a client resource reload.
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"
        ),
        method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;"
    )
    private ReloadInstance createReload(
        ReloadableResourceManager resourceManager, Executor backgroundExecutor, Executor mainThreadExecutor, CompletableFuture<Unit> initialTask, List<PackResources> packs
    ) {
        return ProjectReloader.appendServerSteps(resourceManager.createReload(backgroundExecutor, mainThreadExecutor, initialTask, packs));
    }

    /// `TAIL`: every resource is closed, including a window closed by its X button.
    @Inject(
        at = @At("TAIL"),
        method = "close()V"
    )
    private void onClose(CallbackInfo callbackInfo) {
        ProjectReloader.onGameClosed();
    }
}
