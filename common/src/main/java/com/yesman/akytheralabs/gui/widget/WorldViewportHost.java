package com.yesman.akytheralabs.gui.widget;

import net.minecraft.client.gui.navigation.ScreenRectangle;

/// Owner of the rectangle the in-game frame buffer is blitted into.
///
/// [com.yesman.akytheralabs.AkytheraEditorLayoutManager#snatchInputs] reads the active host to decide
/// whether a pointer position belongs to the editor or to the game, and
/// [com.yesman.akytheralabs.mixin.MixinMouseHandler] reads it to express the cursor in the game's own
/// coordinate space.
public interface WorldViewportHost {
    /// The viewport rect, in editor gui units.
    ScreenRectangle getWorldViewportRectangle();

    /// Whether a pointer merely resting over this viewport already belongs to the game.
    ///
    /// A host that answers false keeps every input until it grabs the pointer itself, so its own
    /// widgets stay clickable while the cursor crosses the viewport.
    default boolean routesHoverToGame() {
        return true;
    }

    default boolean pointerInWorldViewport(final int mouseX, final int mouseY) {
        ScreenRectangle rectangle = getWorldViewportRectangle();

        return mouseX >= rectangle.left()
            && mouseY >= rectangle.top()
            && mouseX < rectangle.right()
            && mouseY < rectangle.bottom();
    }
}
