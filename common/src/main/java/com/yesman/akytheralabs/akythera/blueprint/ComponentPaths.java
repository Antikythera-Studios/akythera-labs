package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.ComponentGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Consumer;

/// The component paths of a blueprint's component tree, and what its canvases do with them: the paths they may name
/// ([#known]), the ones a [ComponentGetterNodeData] names without ([#unknown], a compile error), and
/// following a renamed component ([#rename]).
public final class ComponentPaths {
    private ComponentPaths() {}

    /// Every path the component tree under `root` holds, a component's own path prefixing its children's,
    /// mapped to the runtime component class that path builds.
    public static SequencedMap<String, Class<? extends PatchComponent>> known(final ComponentAttachableHolder root) {
        SequencedMap<String, Class<? extends PatchComponent>> paths = new LinkedHashMap<>();
        collectPaths(root, "", paths);

        return paths;
    }

    /// The component at `path` under `host`, or null when none sits there.
    public static @Nullable ComponentDataHolder<?> at(final ComponentAttachableHolder host, final String path) {
        ComponentAttachableHolder current = host;
        ComponentDataHolder<?> component = null;

        for (String name : path.split(ComponentAttachable.PATH_SEPARATOR)) {
            if (current == null) return null;

            component = current.getComponents().get(name);

            if (component == null) return null;

            current = component instanceof ComponentAttachableHolder child ? child : null;
        }

        return component;
    }

    /// Every path a `Get Component` node names that the tree under `root` does not hold, in the order the
    /// canvases list them. Reads `script`'s canvases and every canvas a component owns.
    public static List<String> unknown(final ComponentAttachableHolder root, final BlueprintScriptData script) {
        SequencedMap<String, Class<? extends PatchComponent>> known = known(root);

        List<ScriptGraphData> canvases = new ArrayList<>(script.canvases().toList());
        collectCanvases(root, canvases);

        List<String> unknown = new ArrayList<>();

        for (ScriptGraphData canvas : canvases) {
            for (GraphNode node : canvas.nodes()) {
                if (node.data() instanceof ComponentGetterNodeData getter && !known.containsKey(getter.path())) {
                    unknown.add(getter.path());
                }
            }
        }

        return unknown;
    }

    /// Re-points `script`'s canvases and every canvas a component under `root` owns at a renamed component.
    /// The canvases open as tabs carry their own widgets and are re-pointed with them.
    public static void rename(final ComponentAttachableHolder root, final BlueprintScriptData script, final String from, final String to) {
        script.mapCanvases(canvas -> moved(canvas, from, to));

        forEachComponent(root, component -> component.mapCanvases(canvas -> moved(canvas, from, to)));
    }

    /// `canvas` with every [ComponentGetterNodeData] row following the rename, or `canvas` itself when
    /// none names the renamed component.
    public static ScriptGraphData moved(final ScriptGraphData canvas, final String from, final String to) {
        List<GraphNode> nodes = new ArrayList<>(canvas.nodes().size());
        boolean moved = false;

        for (GraphNode node : canvas.nodes()) {
            String path = node.data() instanceof ComponentGetterNodeData getter ? getter.path() : null;
            String movedPath = path == null ? null : moved(path, from, to);

            if (movedPath != null && !movedPath.equals(path)) {
                nodes.add(node.withData(new ComponentGetterNodeData(movedPath)));
                moved = true;
            } else {
                nodes.add(node);
            }
        }

        return moved ? new ScriptGraphData(nodes, canvas.wires(), canvas.failedNodes()) : canvas;
    }

    /// `path` after a rename: the renamed component itself, or anything attached under it, moves to
    /// `to`. Any other path is its own answer.
    public static String moved(final String path, final String from, final String to) {
        if (path.equals(from)) return to;

        return path.startsWith(from + ComponentAttachable.PATH_SEPARATOR) ? to + path.substring(from.length()) : path;
    }

    /// Every path under `host` into `out`, each child's name behind `prefix`.
    private static void collectPaths(
        final ComponentAttachableHolder host,
        final String prefix,
        final SequencedMap<String, Class<? extends PatchComponent>> out
    ) {
        for (Map.Entry<String, ComponentDataHolder<?>> entry : host.getComponents().entrySet()) {
            String path = prefix + entry.getKey();
            out.put(path, entry.getValue().generateEventually());

            if (entry.getValue() instanceof ComponentAttachableHolder child) {
                collectPaths(child, path + ComponentAttachable.PATH_SEPARATOR, out);
            }
        }
    }

    /// Every canvas a component under `host` owns, into `out`.
    private static void collectCanvases(final ComponentAttachableHolder host, final List<ScriptGraphData> out) {
        forEachComponent(host, component -> component.canvases().forEach(out::add));
    }

    /// Runs `action` on every component under `host`, hosts before their children.
    private static void forEachComponent(final ComponentAttachableHolder host, final Consumer<ComponentDataHolder<?>> action) {
        for (ComponentDataHolder<?> component : host.getComponents().values()) {
            action.accept(component);

            if (component instanceof ComponentAttachableHolder child) forEachComponent(child, action);
        }
    }
}
