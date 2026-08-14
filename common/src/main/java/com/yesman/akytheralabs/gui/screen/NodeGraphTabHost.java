package com.yesman.akytheralabs.gui.screen;

import com.yesman.akytheralabs.editor.EditorNodeGraph;
import com.yesman.akytheralabs.gui.widget.script.ContextEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/// An editor screen that can open one [EditorNodeGraph] field's canvas as a closeable center tab.
/// [com.yesman.akytheralabs.editor.EditorWidgetFactory]'s "Edit Node Graph" row dispatches on this,
/// so a component carrying a canvas field works on any screen that hosts one.
public interface NodeGraphTabHost {
    /// Opens (or re-focuses) the canvas for one result-graph field: a pure data-flow graph from a
    /// [ContextEntryNodeWidget] carrying `params` into a `resultType`-typed result root. `flush`
    /// receives the exported graph on tab close and on save.
    void openResultGraphTab(
        String key,
        Component title,
        ScriptGraphData data,
        List<ContextEntryNodeWidget.ContextParam> params,
        Class<?> resultType,
        Consumer<ScriptGraphData> flush
    );
}
