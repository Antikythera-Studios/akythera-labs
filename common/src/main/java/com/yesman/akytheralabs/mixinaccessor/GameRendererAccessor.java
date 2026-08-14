package com.yesman.akytheralabs.mixinaccessor;

import net.minecraft.client.gui.render.GuiRenderer;

public interface GameRendererAccessor {
    /// The renderer that turns the frame's `GuiRenderState` into draws. Private as of 26.2, and the
    /// editor drives it a second time to draw its own layout into its own render target.
    GuiRenderer akytheralabs$guiRenderer();
}
