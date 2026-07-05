package com.yesman.akytheralabs.gui.screen;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.widget.Modal;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/// Screens that handles input intercepts for modal widgets opened
/// via [AkytheraEditorLayoutManager#openModalWidget]
///
/// This interface is not a necessary for handling modals. You can
/// override [Screen#getChildAt] to return the modal and let current
/// screen widgets are ignored by inputs. This is more like a reminder
/// not to forget overriding that method.
public interface ModalAccessibleScreen {
    default @Nullable AbstractWidget getHoveringModal(final double x, final double y) {
        AbstractWidget modal = AkytheraEditorLayoutManager.getInstance().getModal();

        if (modal == null) return null;

        if (modal.isMouseOver(x, y)) {
            return modal;
        }

        // Close the modal in case the mouse pointer pressed out of it
        AkytheraEditorLayoutManager.getInstance().closeModal();

        return null;
    }

    /// Check whether the pressed widget is modal. Modal shouldn't be focused
    default boolean isModal(GuiEventListener g) {
        return g instanceof Modal;
    }
}
