package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V"
    )
    private Screen onKeyPressRedirectScreen(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    /// Suppress the vanilla F2 screenshot grab while the editor is snatching inputs,
    /// so F2 falls through to `screen.keyPressed` and our panels can use it for rename.
    /// `ordinal = 1` because `keyFullscreen.matches` is the first `matches` call in
    /// `keyPress`, `keyScreenshot.matches` is the second.
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z",
            ordinal = 1
        ),
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V"
    )
    private boolean onScreenshotKeyMatches(KeyMapping self, KeyEvent event) {
        if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            return false;
        }

        return self.matches(event);
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            opcode = Opcodes.GETFIELD
        ),
        method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V"
    )
    private Screen onCharTypedRedirectScreen(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() : minecraft.screen;
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"
        ),
        method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V"
    )
    private Overlay onCharTypedRedirectOverlay(Minecraft minecraft) {
        return AkytheraEditorLayoutManager.getInstance().snatchInputs() ? null : minecraft.getOverlay();
    }
}