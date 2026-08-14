package com.yesman.akytheralabs.gui.screen;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.widget.FloatingModal;
import com.yesman.akytheralabs.gui.widget.Modal;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/// A screen that intercepts input for the modal widgets opened through
/// [AkytheraEditorLayoutManager#openModalWidget].
///
/// Implementing it is optional: overriding [Screen#getChildAt] to return the modal is enough to make
/// the screen's own widgets ignore input. The interface exists as a reminder to do so.
public interface ModalAccessibleScreen {
    default @Nullable AbstractWidget getHoveringModal(final double x, final double y) {
        AbstractWidget modal = AkytheraEditorLayoutManager.getInstance().getModal();

        if (modal == null) return null;

        if (modal.isMouseOver(x, y)) {
            return modal;
        }

        // A FloatingModal shares the screen: a press beside it belongs to whatever is under the
        // pointer, and leaves the modal up.
        if (modal instanceof FloatingModal) return null;

        // Close the modal in case the mouse pointer pressed out of it
        AkytheraEditorLayoutManager.getInstance().closeModal();

        return null;
    }

    /// The modal that stands in for the screen's own focus while it is open, or `null` when the focus
    /// chain is the screen's own.
    ///
    /// A [FloatingModal] never is one — [#isModal] lets it into the parent's focus field, so it is
    /// focused and blurred like any other widget.
    default @Nullable AbstractWidget getFocusOwningModal() {
        AbstractWidget modal = AkytheraEditorLayoutManager.getInstance().getModal();

        return modal instanceof FloatingModal ? null : modal;
    }

    /// Check whether the pressed widget is modal. Modal shouldn't be focused
    ///
    /// A [FloatingModal] is the exception: it shares the screen, so it takes focus like any other
    /// widget — but only while it is up. A modal that closes itself from its own click handler is
    /// handed the focus again on the way back out of
    /// [net.minecraft.client.gui.components.events.ContainerEventHandler#mouseClicked], and the screen
    /// would keep pointing at it.
    default boolean isModal(GuiEventListener g) {
        if (g instanceof FloatingModal) {
            return g instanceof AbstractWidget widget && !widget.isActive();
        }

        return g instanceof Modal;
    }
}