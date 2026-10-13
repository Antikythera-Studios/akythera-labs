package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.mixinaccessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractorAccessor implements GuiGraphicsExtractorAccessor {
    @Shadow @Final
    private GuiRenderState guiRenderState;

    @Override
    public GuiRenderState akytheralabs$guiRenderState() {
        return guiRenderState;
    }
}
