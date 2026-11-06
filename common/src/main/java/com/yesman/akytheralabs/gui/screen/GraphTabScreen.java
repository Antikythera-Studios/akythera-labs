package com.yesman.akytheralabs.gui.screen;

import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;

import java.util.List;
import java.util.function.Supplier;

/// An editor screen whose node graph canvases open in tabs.
public interface GraphTabScreen {
    /// Every node graph canvas this screen can show, whether its tab is open or not.
    List<GraphTab> graphTabs();

    /// One canvas a [GraphTabScreen] can show.
    ///
    /// @param title what the canvas is, as its tab names it
    /// @param open  opens the canvas' tab, or brings it forward, and returns the canvas
    record GraphTab(String title, Supplier<ScriptNodeGraphWidget> open) {}
}
