package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.mixinaccessor.GameRendererAccessor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererAccessor implements GameRendererAccessor {
    @Shadow @Final
    private GuiRenderer guiRenderer;

    @Override
    public GuiRenderer akytheralabs$guiRenderer() {
        return guiRenderer;
    }
}
