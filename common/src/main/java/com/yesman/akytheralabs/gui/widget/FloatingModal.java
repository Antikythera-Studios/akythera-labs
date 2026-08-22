package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.screen.ModalAccessibleScreen;

/// A [Modal] that shares its screen instead of taking it over: the pointer belongs to the modal only
/// while it is over the frame, and a press anywhere else goes to the widget under it rather than
/// dismissing the modal. Keyboard focus is likewise ordinary — the modal holds it while it is the last
/// thing clicked, and loses it to the next widget.
///
/// The modal stack still puts it on top, so it draws over the screen, and it still answers
/// [AkytheraEditorLayoutManager#getWorldViewportHost] when it is a [WorldViewportHost].
///
/// A modal that dims the whole viewport is the opposite kind and must not implement this — see
/// [ModalAccessibleScreen#getHoveringModal] for the two paths.
public interface FloatingModal extends Modal {
}