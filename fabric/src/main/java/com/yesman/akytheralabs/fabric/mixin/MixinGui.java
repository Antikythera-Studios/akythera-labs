package com.yesman.akytheralabs.fabric.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/// Screen extraction moved off `GameRenderer#extractGui` and onto [Gui] as of 26.2.
@Mixin(Gui.class)
public abstract class MixinGui {
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        ),
        method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"
    )
    private void extractRenderState(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        if (AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            // If mouse pointer is outside world viewport, modify the parameters of current mouse
            // pointers to avoid highlighting widgets in world viewport
            screen.extractRenderStateWithTooltipAndSubtitles(guiGraphics, -1, -1, a);
        } else {
            screen.extractRenderStateWithTooltipAndSubtitles(guiGraphics, mouseX, mouseY, a);
        }
    }
}
