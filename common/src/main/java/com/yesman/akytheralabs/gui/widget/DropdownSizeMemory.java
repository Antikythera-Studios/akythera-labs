package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;

/// A dropdown anchor that remembers the size its picker was last dragged to. The memory lives on the
/// widget and nowhere else — nothing is written to the project — so it holds for as long as the editor
/// keeps that cell around and is gone once the screen is rebuilt.
///
/// Implemented by [PopupBox]; read and wired by [AkytheraEditorLayoutManager#openModalWidgetUnder], which
/// opens a [ResizableFrame] at the remembered size instead of the default one.
public interface DropdownSizeMemory {
    /// Remembered width, or 0 while the dropdown has never been resized.
    int dropdownWidth();

    /// Remembered height, or 0 while the dropdown has never been resized.
    int dropdownHeight();

    void rememberDropdownSize(int width, int height);
}
