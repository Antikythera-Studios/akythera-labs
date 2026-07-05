package com.yesman.akytheralabs.neoforged.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractGui(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            // If mouse pointer is outside world viewport, modify the parameters of current mouse
            // pointers to avoid highlighting widgets in world viewport
            ClientHooks.drawScreen(screen, guiGraphics, -1, -1, a);
        } else {
            ClientHooks.drawScreen(screen, guiGraphics, mouseX, mouseY, a);
        }
    }
}