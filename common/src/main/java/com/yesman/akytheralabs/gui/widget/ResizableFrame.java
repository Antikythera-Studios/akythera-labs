package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;

/// A modal whose frame the user can drag to resize, by way of a held [FrameResizer]. Exposing it lets
/// [AkytheraEditorLayoutManager#openModalWidgetUnder] point the resizer's size sink at the anchor's
/// [DropdownSizeMemory], so a dragged size survives the modal's close.
public interface ResizableFrame {
    FrameResizer frameResizer();
}
