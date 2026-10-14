package com.yesman.akytheralabs.mixinaccessor;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

public interface GuiGraphicsExtractorAccessor {
    GuiRenderState akytheralabs$guiRenderState();

    /// Sets what the alpha of every fill, text and blit drawn from here on is multiplied by. `1` draws them as given.
    void akytheralabs$setAlpha(float alpha);
}
