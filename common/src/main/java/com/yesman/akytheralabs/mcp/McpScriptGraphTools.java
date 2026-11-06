package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.screen.GraphTabScreen;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.NodeWidgetFactory;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.UnresolvedNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/// The [McpTool]s that read and edit the node graph canvases of the top editor screen.
final class McpScriptGraphTools {
    private McpScriptGraphTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_script_graph",
            "Without 'canvas', lists every node graph of the top editor screen, including graphs whose tab is closed "
                + "(functions, state machine states and transitions, component and widget graphs). With 'canvas', opens that "
                + "graph's tab and returns the graph as JSON: every node with its id, title, pins and current errors, and its "
                + "saved row ('row', the form edit_script_graph 'add' takes), plus the wires. Node ids change when nodes are "
                + "added or deleted, so read the graph again after an edit. Errors shown on nodes come from the last Compile; "
                + "pass validate=true to check the graph now.",
            """
            {
              "type": "object",
              "properties": {
                "canvas": {"type": "integer", "minimum": 0, "description": "The graph index from the listing."},
                "validate": {"type": "boolean", "description": "Validate the graph and report its issues. Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                List<GraphTabScreen.GraphTab> graphTabs = graphTabsOfTopScreen();

                if (!arguments.has("canvas")) {
                    if (graphTabs.isEmpty()) return McpToolResult.text("The top editor screen has no node graph");

                    List<String> lines = new ArrayList<>();

                    for (int i = 0; i < graphTabs.size(); i++) lines.add(i + ": " + graphTabs.get(i).title());

                    return McpToolResult.text(String.join("\n", lines));
                }

                ScriptNodeGraphWidget canvas = openGraph(graphTabs, arguments);

                Map<AbstractNodeWidget, Integer> ids = canvas.assignIds();
                ScriptGraphData graph = canvas.exportGraph();
                Map<Integer, GraphNode> rows = new HashMap<>();
                graph.nodes().forEach(row -> rows.put(row.id(), row));

                JsonObject json = new JsonObject();
                JsonArray nodes = new JsonArray();

                json.addProperty("canvas", graphTabs.get(arguments.get("canvas").getAsInt()).title());

                for (AbstractNodeWidget node : canvas.nodes()) {
                    if (node instanceof UnresolvedNodeWidget) continue;

                    int id = ids.get(node);
                    JsonObject nodeJson = new JsonObject();
                    JsonArray inputs = new JsonArray();
                    JsonArray outputs = new JsonArray();
                    JsonArray errors = new JsonArray();

                    nodeJson.addProperty("id", id);
                    nodeJson.addProperty("title", node.getMessage().getString());
                    nodeJson.addProperty("widget", node.getClass().getSimpleName());
                    nodeJson.addProperty("x", node.getX());
                    nodeJson.addProperty("y", node.getY());

                    for (int pin = 0; pin < node.inputPinCount(); pin++) inputs.add(pinJson(pin, node.inputPin(pin)));
                    for (int pin = 0; pin < node.outputPinCount(); pin++) outputs.add(pinJson(pin, node.outputPin(pin)));

                    node.validationErrors().forEach(error -> errors.add(error.getString()));

                    nodeJson.add("inputs", inputs);
                    nodeJson.add("outputs", outputs);
                    nodeJson.add("errors", errors);
                    nodeJson.addProperty("deletable", node.isDeletable());

                    if (rows.containsKey(id)) nodeJson.add("row", GraphNode.CODEC.encodeStart(JsonOps.INSTANCE, rows.get(id)).getOrThrow());

                    nodes.add(nodeJson);
                }

                json.add("nodes", nodes);

                JsonArray wires = new JsonArray();

                for (ScriptGraphData.WireData wire : graph.wires()) {
                    wires.add(ScriptGraphData.WireData.CODEC.encodeStart(JsonOps.INSTANCE, wire).getOrThrow());
                }

                json.add("wires", wires);

                JsonArray failedNodes = new JsonArray();

                for (ScriptGraphData.FailedNode failed : graph.failedNodes()) {
                    failedNodes.add("node " + failed.id() + ": " + failed.message());
                }

                json.add("unresolvedNodes", failedNodes);

                if (arguments.has("validate") && arguments.get("validate").getAsBoolean()) {
                    JsonArray issues = new JsonArray();
                    Map<AbstractNodeWidget, Integer> validatedIds = canvas.assignIds();

                    for (ScriptNodeGraphWidget.ValidationIssue issue : canvas.validate()) {
                        issues.add("node " + validatedIds.get(issue.node()) + ": " + issue.message().getString());
                    }

                    json.add("issues", issues);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(json));
            })
        ));

        McpTools.register(McpTool.of(
            "search_script_nodes",
            "Searches the nodes a canvas offers, the same list as right-clicking its empty space plus literals, variable "
                + "getters and self. Returns one 'label [category]' per line; add one with edit_script_graph 'add_from_catalogue'.",
            """
            {
              "type": "object",
              "properties": {
                "canvas": {"type": "integer", "minimum": 0},
                "query": {"type": "string", "description": "Text the label or category contains, ignoring case. Default: every node."},
                "maxResults": {"type": "integer", "minimum": 1, "maximum": 1000, "description": "Default: 100."}
              },
              "required": ["canvas"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ScriptNodeGraphWidget canvas = openGraph(graphTabsOfTopScreen(), arguments);
                String query = arguments.has("query") ? arguments.get("query").getAsString().toLowerCase(Locale.ROOT) : "";
                int maxResults = arguments.has("maxResults") ? Math.clamp(arguments.get("maxResults").getAsInt(), 1, 1000) : 100;

                List<String> found = canvas.catalogue().stream()
                    .filter(NodeSuggestModal.Suggestion::enabled)
                    .map(suggestion -> suggestion.label().getString() + (suggestion.category() == null ? "" : " [" + suggestion.category() + "]"))
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains(query))
                    .distinct()
                    .toList();

                if (found.isEmpty()) return McpToolResult.text("No node matches '" + query + "'");

                String listing = String.join("\n", found.subList(0, Math.min(found.size(), maxResults)));

                return McpToolResult.text(found.size() > maxResults ? listing + "\n... (" + (found.size() - maxResults) + " more)" : listing);
            })
        ));

        McpTools.register(McpTool.of(
            "edit_script_graph",
            """
            Edits a node graph of the top editor screen through the editor's history, opening its tab first. Use this tool, \
            not a write to the entry's .json file: the editor keeps open entries in memory and overwrites the file on its \
            next save, and wires name pins by index, which only get_script_graph shows. Each op can be undone with \
            run_editor_action action=undo. Ops run in order; a failing op stops the rest and the ops before it stay applied. \
            A node reference is either an id from get_script_graph (the numbering when this call starts) or "new:<name>" \
            for a node an earlier op of the same call added. Ops:
            - {"op": "add", "nodes": [rows], "wires": [{"from", "from_pin", "to", "to_pin"}]}: adds nodes from saved rows \
            (the 'row' form of get_script_graph, at their own x/y). Row ids are local to the op: wires join rows of the \
            same op, and a row with id N becomes "new:N".
            - {"op": "add_from_catalogue", "label", "category"?, "x", "y", "ref"?}: adds the node search_script_nodes lists \
            under that label; "ref" names it "new:<ref>".
            - {"op": "connect", "from", "from_pin", "to", "to_pin"}: wires an output pin into an input pin, inserting a \
            converter between castable types. A data input keeps one wire, so its old wire is replaced.
            - {"op": "disconnect", "node", "pin", "side": "input" | "output"}: removes every wire on the pin.
            - {"op": "delete", "nodes": [references]}: removes nodes and their wires. Entry nodes are kept.
            Save afterwards with run_editor_action action=save.""",
            """
            {
              "type": "object",
              "properties": {
                "canvas": {"type": "integer", "minimum": 0},
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["canvas", "ops"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ScriptNodeGraphWidget canvas = openGraph(graphTabsOfTopScreen(), arguments);
                Map<Integer, AbstractNodeWidget> byId = new HashMap<>();
                Map<String, AbstractNodeWidget> created = new LinkedHashMap<>();
                JsonArray ops = arguments.getAsJsonArray("ops");

                canvas.assignIds().forEach((node, id) -> byId.put(id, node));

                for (int index = 0; index < ops.size(); index++) {
                    JsonObject op = ops.get(index).getAsJsonObject();

                    try {
                        switch (op.get("op").getAsString()) {
                            case "add" -> {
                                JsonObject graphJson = new JsonObject();
                                graphJson.add("nodes", op.get("nodes"));
                                if (op.has("wires")) graphJson.add("wires", op.get("wires"));

                                ScriptGraphData data = ScriptGraphData.CODEC.parse(JsonOps.INSTANCE, graphJson).getOrThrow();

                                if (!data.failedNodes().isEmpty()) {
                                    throw new IllegalArgumentException(data.failedNodes().stream()
                                        .map(failed -> "row " + failed.id() + ": " + failed.message())
                                        .collect(Collectors.joining("; ")));
                                }

                                List<AbstractNodeWidget> added = canvas.pasteGraph(data);

                                if (added.size() != data.nodes().size()) throw new IllegalArgumentException("Only " + added.size() + " of " + data.nodes().size() + " rows built a node; entry rows can't be added");

                                for (int i = 0; i < added.size(); i++) {
                                    GraphNode row = data.nodes().get(i);

                                    added.get(i).setX(row.x());
                                    added.get(i).setY(row.y());
                                    created.put("new:" + row.id(), added.get(i));
                                }
                            }
                            case "add_from_catalogue" -> {
                                String label = op.get("label").getAsString();
                                String category = op.has("category") ? op.get("category").getAsString() : null;
                                List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> matches = canvas.catalogue().stream()
                                    .filter(suggestion -> suggestion.enabled() && suggestion.label().getString().equalsIgnoreCase(label))
                                    .filter(suggestion -> category == null || category.equalsIgnoreCase(suggestion.category()))
                                    .toList();

                                if (matches.size() != 1) {
                                    throw new IllegalArgumentException(matches.size() + " nodes are labeled '" + label + "'"
                                        + (matches.isEmpty() ? "" : "; pass a category: " + matches.stream().map(NodeSuggestModal.Suggestion::category).toList()));
                                }

                                AbstractNodeWidget node = canvas.spawnNode(matches.getFirst().value(), op.get("x").getAsInt(), op.get("y").getAsInt());

                                if (op.has("ref")) created.put("new:" + op.get("ref").getAsString(), node);
                            }
                            case "connect" -> {
                                AbstractNodeWidget from = resolveNode(op.get("from"), byId, created);
                                AbstractNodeWidget to = resolveNode(op.get("to"), byId, created);
                                int fromPin = op.get("from_pin").getAsInt();
                                int toPin = op.get("to_pin").getAsInt();

                                if (!canvas.connect(from, fromPin, to, toPin)) {
                                    throw new IllegalArgumentException("Output pin " + fromPin + " of '" + from.getMessage().getString()
                                        + "' can't feed input pin " + toPin + " of '" + to.getMessage().getString() + "'");
                                }
                            }
                            case "disconnect" -> canvas.disconnectPin(
                                resolveNode(op.get("node"), byId, created),
                                op.get("pin").getAsInt(),
                                "output".equals(op.get("side").getAsString())
                            );
                            case "delete" -> {
                                List<AbstractNodeWidget> victims = new ArrayList<>();
                                op.getAsJsonArray("nodes").forEach(reference -> victims.add(resolveNode(reference, byId, created)));
                                canvas.deleteNodes(victims);
                            }
                            default -> throw new IllegalArgumentException("Unknown op '" + op.get("op").getAsString() + "'");
                        }
                    } catch (RuntimeException e) {
                        return McpToolResult.error("Op " + index + " (" + op.get("op") + ") failed: " + e.getMessage()
                            + (index == 0 ? "" : ". The " + index + " ops before it stay applied"));
                    }
                }

                Map<AbstractNodeWidget, Integer> ids = canvas.assignIds();
                JsonObject result = new JsonObject();
                JsonObject createdIds = new JsonObject();

                created.forEach((reference, node) -> createdIds.addProperty(reference, ids.get(node)));
                result.addProperty("applied", ops.size());
                result.add("created", createdIds);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));
    }

    /// Every node graph of the top editor screen: what a [GraphTabScreen] lists, or the canvases shown on any other
    /// screen.
    private static List<GraphTabScreen.GraphTab> graphTabsOfTopScreen() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (screen == null) throw new IllegalStateException("The editor layout is not open");
        if (screen instanceof GraphTabScreen graphTabScreen) return graphTabScreen.graphTabs();

        List<TabPanel> tabPanels = McpTools.findWidgets(screen, TabPanel.class);
        List<GraphTabScreen.GraphTab> graphTabs = new ArrayList<>();

        for (ScriptNodeGraphWidget canvas : McpTools.findWidgets(screen, ScriptNodeGraphWidget.class)) {
            TabPanel owner = tabPanels.stream().filter(tabs -> tabs.indexOfContent(canvas) >= 0).findFirst().orElse(null);
            String title = owner != null
                ? owner.getTabTitle(owner.indexOfContent(canvas)).getString()
                : canvas.enclosingFunction() != null ? canvas.enclosingFunction() : canvas.getMessage().getString();

            graphTabs.add(new GraphTabScreen.GraphTab(title, () -> {
                if (owner != null) owner.setActiveIndex(owner.indexOfContent(canvas));

                return canvas;
            }));
        }

        return graphTabs;
    }

    /// Opens the tab of the graph whose index `arguments` gives as "canvas", and returns its canvas.
    private static ScriptNodeGraphWidget openGraph(final List<GraphTabScreen.GraphTab> graphTabs, final JsonObject arguments) {
        int index = arguments.get("canvas").getAsInt();

        if (index < 0 || index >= graphTabs.size()) throw new IllegalArgumentException("No graph " + index + "; the top editor screen has " + graphTabs.size());

        ScriptNodeGraphWidget canvas = graphTabs.get(index).open().get();

        if (canvas == null) throw new IllegalStateException("'" + graphTabs.get(index).title() + "' did not open");

        return canvas;
    }

    private static AbstractNodeWidget resolveNode(final JsonElement reference, final Map<Integer, AbstractNodeWidget> byId, final Map<String, AbstractNodeWidget> created) {
        AbstractNodeWidget node = reference.getAsJsonPrimitive().isNumber() ? byId.get(reference.getAsInt()) : created.get(reference.getAsString());

        if (node == null) throw new IllegalArgumentException("No node " + reference);

        return node;
    }

    private static JsonObject pinJson(final int index, final AbstractNodeWidget.Pin pin) {
        JsonObject json = new JsonObject();

        json.addProperty("pin", index);
        json.addProperty("label", pin.label());
        json.addProperty("type", pin.exec() ? "exec" : pin.type() == null ? "any" : pin.type().getSimpleName());

        return json;
    }
}
