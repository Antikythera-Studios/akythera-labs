package com.yesman.akytheralabs.neoforged.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Stack;

/// Screen extraction moved off `GameRenderer#extractGui` and onto [Gui] as of 26.2.
@Mixin(Gui.class)
public abstract class MixinGui {
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;extractScreen(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/Stack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractRenderState(
        Screen screen, Stack<Screen> backgroundLayers, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a
    ) {
        if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            // If mouse pointer is outside world viewport, modify the parameters of current mouse
            // pointers to avoid highlighting widgets in world viewport
            ClientHooks.extractScreen(screen, backgroundLayers, guiGraphics, -1, -1, a);
        } else {
            ClientHooks.extractScreen(screen, backgroundLayers, guiGraphics, mouseX, mouseY, a);
        }
    }
}
