package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A failed Compile, kept on the entry's [DataHolder] until the next Compile: the page it failed on, and the
/// validation errors of every node it flagged, by canvas key and node id.
public final class CompileFailure extends IllegalStateException {
    private final Component page;

    /// Canvas key → node id → the errors the compile flagged the node with.
    private final Map<String, Map<Integer, List<Component>>> marks;

    /// Canvas key → the canvas the marks of that key were last shown on.
    private final Map<String, ScriptNodeGraphWidget> shownCanvases = new HashMap<>();

    /// Canvas key → node id → the node the mark was put on in [#shownCanvases].
    private final Map<String, Map<Integer, AbstractNodeWidget>> shownNodes = new HashMap<>();

    /// Canvas key → the [ScriptNodeGraphWidget#importCount] of [#shownCanvases] when the marks were bound.
    private final Map<String, Integer> shownImportCounts = new HashMap<>();

    public CompileFailure(final Component page, final Map<String, Map<Integer, List<Component>>> marks) {
        super("Compile failed: " + page.getString());

        this.page = page;
        this.marks = marks;
    }

    /// The page the compile failed on, as the toast names it.
    public Component page() {
        return page;
    }

    /// Puts the errors stored under `key` back on `canvas`'s nodes. A canvas seen for the first time, or rebuilt
    /// by [ScriptNodeGraphWidget#importGraph] since, takes each mark on the node with its id: a rebuild keeps
    /// every id. Otherwise the mark follows the node it was put on, and is dropped once the user deletes that
    /// node or it is renumbered, so it never lands on another node.
    public void showOn(final String key, final ScriptNodeGraphWidget canvas) {
        Map<Integer, List<Component>> stored = marks.get(key);

        if (stored == null) return;

        Map<AbstractNodeWidget, Integer> ids = canvas.assignIds();

        if (shownCanvases.get(key) != canvas || !Objects.equals(shownImportCounts.get(key), canvas.importCount())) {
            Map<Integer, AbstractNodeWidget> nodeById = new HashMap<>();
            ids.forEach((node, id) -> nodeById.put(id, node));

            shownCanvases.put(key, canvas);
            shownImportCounts.put(key, canvas.importCount());
            shownNodes.put(key, new HashMap<>());
            stored.keySet().forEach(id -> shownNodes.get(key).put(id, nodeById.get(id)));
        }

        Map<Integer, AbstractNodeWidget> shown = shownNodes.get(key);

        stored.keySet().removeIf(id -> !Objects.equals(ids.get(shown.get(id)), id));
        stored.forEach((id, errors) -> shown.get(id).setErrors(errors));
    }
}
