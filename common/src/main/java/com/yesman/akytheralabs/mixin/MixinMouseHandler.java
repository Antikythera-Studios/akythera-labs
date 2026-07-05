package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"
    )
    private Screen onMouseButtonRedirectScreen(Minecraft minecraft) {
        // Returns AkytheraEditorLayoutManager editor screen instead of in-game viewport screen when editor layout activated (Applied to below snatch methods)
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"
        ),
        method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"
    )
    private Overlay onMouseButtonRedirectOverlay(Minecraft minecraft) {
        // Returns no overlay info when AkytheraEditorLayoutManager editor activated to allow inputs while reloading resources (Applied to below snatch methods)
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? null : minecraft.getOverlay();
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "onScroll(JDD)V"
    )
    private Screen onMouseScrollRedirectScreen(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"
        ),
        method = "onScroll(JDD)V"
    )
    private Overlay onMouseScrollRedirectOverlay(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? null : minecraft.getOverlay();
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "handleAccumulatedMovement()V"
    )
    private Screen onHandleAccumulatedMovementRedirectScreen(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"
        ),
        method = "handleAccumulatedMovement()V"
    )
    private Overlay onHandleAccumulatedMovementRedirectOverlay(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? null : minecraft.getOverlay();
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "onDrop(JLjava/util/List;I)V"
    )
    private Screen onFileDropRedirectScreen(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    /// Vanilla [net.minecraft.client.gui.components.events.ContainerEventHandler] only latches
    /// dragging / forwards drag + release for the left button (button 0), so a right-press never
    /// starts a drag. These three redirects keep the stock left-button behaviour and, when the
    /// editor is active, extend it to the right button (button 1) — gated on [MouseButtonEvent]
    /// so vanilla and non-editor screens are untouched. This lets editor canvases (e.g. the node
    /// graph) pan on a right-drag.

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"
        ),
        method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"
    )
    private boolean onMousePressForwardClick(Screen screen, MouseButtonEvent event, boolean doubleClick) {
        boolean handled = screen.mouseClicked(event, doubleClick);

        // Vanilla skipped setDragging for the right button; latch it so the following
        // right-drag is forwarded through the focused chain.
        if (handled && AkytheraEditorLayoutManager.getInstance().isActive() && event.button() == 1) {
            screen.setDragging(true);
        }

        return handled;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"
        ),
        method = "handleAccumulatedMovement()V"
    )
    private boolean onMouseMoveForwardDrag(Screen screen, MouseButtonEvent event, double dx, double dy) {
        if (screen.mouseDragged(event, dx, dy)) {
            return true;
        }

        // Right-button pan: vanilla's container dispatch dropped this drag (button != 0).
        if (AkytheraEditorLayoutManager.acceptsDragButton(event) && screen.isDragging()) {
            GuiEventListener focused = screen.getFocused();
            return focused != null && focused.mouseDragged(event, dx, dy);
        }

        return false;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"
        ),
        method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"
    )
    private boolean onMouseReleaseForwardRelease(Screen screen, MouseButtonEvent event) {
        if (screen.mouseReleased(event)) {
            return true;
        }

        // Mirror the drag extension for release so the right-button drag/click is finished
        // (the focused chain gets its mouseReleased and dragging is cleared).
        if (AkytheraEditorLayoutManager.acceptsDragButton(event) && screen.isDragging()) {
            screen.setDragging(false);
            GuiEventListener focused = screen.getFocused();
            return focused != null && focused.mouseReleased(event);
        }

        return false;
    }

    @Inject(
        at = @At(
            value = "HEAD"
        ),
        method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D",
        cancellable = true
    )
    private static void getScaledXPos(Window window, double x, CallbackInfoReturnable<Double> callbackInfo) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int editorScale = AkytheraEditorLayoutManager.getInstance().getLayoutGuiScale();

            if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
                // Editor UI: coordinates in editor GUI units
                callbackInfo.setReturnValue(x * (1.0D / editorScale));
                callbackInfo.cancel();
            } else {
                // Game viewport: subtract viewport screen-pixel origin, then divide by the
                // game's own GUI scale (recalculated by resizeDisplay after viewport resize)
                MainEditorScreen mainScreen = AkytheraEditorLayoutManager.getInstance().getMainScreen();
                callbackInfo.setReturnValue((x - mainScreen.getWorldViewportX() * editorScale) * (1.0D / window.getGuiScale()));
                callbackInfo.cancel();
            }
        }
    }

    @Inject(
        at = @At(
            value = "HEAD"
        ),
        method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D",
        cancellable = true
    )
    private static void getScaledYPos(Window window, double y, CallbackInfoReturnable<Double> callbackInfo) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int editorScale = AkytheraEditorLayoutManager.getInstance().getLayoutGuiScale();

            if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
                // Editor UI: coordinates in editor GUI units
                callbackInfo.setReturnValue(y * (1.0D / editorScale));
                callbackInfo.cancel();
            } else {
                // Game viewport: subtract viewport screen-pixel origin, then divide by the
                // game's own GUI scale (recalculated by resizeDisplay after viewport resize)
                MainEditorScreen mainScreen = AkytheraEditorLayoutManager.getInstance().getMainScreen();
                callbackInfo.setReturnValue((y - mainScreen.getWorldViewportY() * editorScale) * (1.0D / window.getGuiScale()));
                callbackInfo.cancel();
            }
        }
    }
}