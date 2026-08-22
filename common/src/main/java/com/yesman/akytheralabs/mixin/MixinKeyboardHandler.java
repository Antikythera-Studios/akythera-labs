package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
    /// The world viewport grabs the pointer on click, and from then on `snatchInputs` is false so
    /// every key press belongs to the game — the editor screens can't see the ungrab mapping. Catch
    /// it here, ahead of all vanilla handling, and cancel so the bound key doesn't also reach
    /// gameplay (Shift+F1 would otherwise still trip vanilla's `keyToggleGui`).
    @Inject(
        at = @At("HEAD"),
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        cancellable = true
    )
    private void onKeyPressUngrabMouse(long windowPointer, int action, KeyEvent event, CallbackInfo callbackInfo) {
        if (action == InputConstants.PRESS && AkytheraEditorLayoutManager.getInstance().tryUngrabMouse(event)) {
            callbackInfo.cancel();
        }
    }

    /// Asking a possessed entity for a script-log capture is a session-wide hotkey, not a widget's:
    /// the debug viewport floats over an editor screen that keeps its own focus, and once the viewport
    /// grabs the pointer the key belongs to the game. Catch it here, ahead of all vanilla handling,
    /// and cancel so the bound key doesn't also reach gameplay.
    @Inject(
        at = @At("HEAD"),
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        cancellable = true
    )
    private void onKeyPressCaptureScriptLog(long windowPointer, int action, KeyEvent event, CallbackInfo callbackInfo) {
        if (action == InputConstants.PRESS && EntityDebugSession.getInstance().tryCaptureKey(event)) {
            callbackInfo.cancel();
        }
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"
        ),
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V"
    )
    private Screen onKeyPressRedirectScreen(Gui gui) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : gui.screen();
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"
        ),
        method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V"
    )
    private Screen onCharTypedRedirectScreen(Gui gui) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : gui.screen();
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;"
        ),
        method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V"
    )
    private Overlay onCharTypedRedirectOverlay(Gui gui) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? null : gui.overlay();
    }
}