package com.yesman.akytheralabs.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/// The grid background every editor canvas draws: a dark fill under lines whose spacing scales with the zoom while
/// their thickness stays 1px, every fourth one brighter.
public final class CanvasGrid {
    private CanvasGrid() {}

    /// Line spacing at zoom 1, in GUI pixels.
    private static final int GRID = 24;
    private static final int MAJOR_EVERY = 4;

    private static final int COLOR_BG = 0xFF161616;
    private static final int COLOR_GRID_MINOR = 0xFF222222;
    private static final int COLOR_GRID_MAJOR = 0xFF2E2E2E;

    /// Fills `left`..`right` x `top`..`bottom` and draws the grid over it, its lines passing through the canvas
    /// origin `originX`, `originY` (screen pixels).
    public static void extract(
        final GuiGraphicsExtractor graphics, final int left, final int top, final int right, final int bottom,
        final float originX, final float originY, final float zoom
    ) {
        graphics.fill(left, top, right, bottom, COLOR_BG);

        float step = GRID * zoom;

        if (step < 6.0F) return; // too dense to be useful when far zoomed out

        int kx = (int) Math.ceil((left - originX) / step);

        for (float sx = originX + kx * step; sx < right; sx += step, kx++) {
            graphics.fill((int) sx, top, (int) sx + 1, bottom, Math.floorMod(kx, MAJOR_EVERY) == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
        }

        int ky = (int) Math.ceil((top - originY) / step);

        for (float sy = originY + ky * step; sy < bottom; sy += step, ky++) {
            graphics.fill(left, (int) sy, right, (int) sy + 1, Math.floorMod(ky, MAJOR_EVERY) == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
        }
    }
}
