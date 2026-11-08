package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.behavior.BehaviorNodeCatalog;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeData;
import com.yesman.akytheralabs.gui.screen.BehaviorTreeEditorExtension;
import com.yesman.akytheralabs.gui.screen.ScriptBlueprintEditorScreen;
import com.yesman.akytheralabs.gui.widget.behavior.BehaviorTreeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineGraphWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/// The [McpTool]s that read and edit the behavior trees and state machines of the behavior tree asset open on the top
/// editor screen. The graphs inside them (transition conditions, the Event Graph) are edited with
/// `edit_script_graph`.
final class McpBehaviorTools {
    private McpBehaviorTools() {}

    private static final String TREE_TARGET = """
        "state_machine": {"type": "string", "description": "With 'state', the state machine whose state tree to use. Omit both for the root tree."},
        "state": {"type": "string", "description": "The state signature id, e.g. 'mymod:chase'."}
        """;

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_behavior_tree",
            "Returns a behavior tree of the behavior tree asset open on the top editor screen, as JSON: every node with its "
                + "id, position, 'node' (the node type JSON, 'type' included), 'children' (ids, left to right), 'conditions', "
                + "'subtasks' and 'abort_mode', plus 'roots' (the ids ROOT runs, left to right). With catalogue=true, "
                + "also lists the node types by role with the JSON a fresh node starts from.",
            """
            {
              "type": "object",
              "properties": {
                %s,
                "catalogue": {"type": "boolean", "description": "Also list the node types. Default: false."}
              }
            }
            """.formatted(TREE_TARGET),
            arguments -> McpTools.onClient(() -> {
                BehaviorTreeGraphWidget canvas = treeCanvas(arguments);
                JsonObject json = BehaviorTreeData.CODEC.encodeStart(JsonOps.INSTANCE, canvas.exportData()).getOrThrow().getAsJsonObject();

                if (arguments.has("catalogue") && arguments.get("catalogue").getAsBoolean()) {
                    JsonObject catalogue = new JsonObject();

                    BehaviorNodeCatalog.entries().forEach((role, entries) -> {
                        JsonArray types = new JsonArray();
                        entries.values().forEach(types::add);
                        catalogue.add(role.name().toLowerCase(), types);
                    });

                    json.add("catalogue", catalogue);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(json));
            })
        ));

        McpTools.register(McpTool.of(
            "edit_behavior_tree",
            "Edits a behavior tree of the behavior tree asset open on the top editor screen, as one undoable step. Ops, "
                + "applied in order: {op:'add', node:<type JSON>, x, y, parent?} (the reply lists the new id), "
                + "{op:'remove', id}, {op:'move', id, x, y}, {op:'set_parent', id, parent} (appends as the last child), "
                + "{op:'set_node', id, node}, {op:'set_abort_mode', id, mode:'none'|'self'|'lower_priority'|'both'}, "
                + "{op:'add_condition', id, node}, {op:'add_subtask', id, node, interval?}, {op:'remove_condition', id, index}, "
                + "{op:'remove_subtask', id, index}. A parent is a node id or 'root', the ROOT entry. ROOT runs its "
                + "nodes left to right by priority, like a selector. Children and ROOT's nodes run left to right; the "
                + "canvas keeps them sorted by x.",
            """
            {
              "type": "object",
              "properties": {
                %s,
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["ops"]
            }
            """.formatted(TREE_TARGET),
            arguments -> McpTools.onClient(() -> {
                BehaviorTreeGraphWidget canvas = treeCanvas(arguments);
                JsonObject tree = BehaviorTreeData.CODEC.encodeStart(JsonOps.INSTANCE, canvas.exportData()).getOrThrow().getAsJsonObject();
                JsonArray nodes = tree.has("nodes") ? tree.getAsJsonArray("nodes") : new JsonArray();
                List<String> report = new ArrayList<>();

                tree.add("nodes", nodes);

                for (JsonElement element : arguments.getAsJsonArray("ops")) {
                    JsonObject op = element.getAsJsonObject();
                    String name = op.get("op").getAsString();

                    switch (name) {
                        case "add" -> {
                            int id = 0;
                            for (JsonElement node : nodes) id = Math.max(id, node.getAsJsonObject().get("id").getAsInt() + 1);

                            JsonObject node = new JsonObject();
                            node.addProperty("id", id);
                            node.addProperty("x", op.has("x") ? op.get("x").getAsInt() : 0);
                            node.addProperty("y", op.has("y") ? op.get("y").getAsInt() : 0);
                            node.add("node", op.get("node"));
                            nodes.add(node);

                            if (op.has("parent")) attach(tree, nodes, op.get("parent"), id);

                            report.add("added node " + id);
                        }
                        case "remove" -> {
                            int id = op.get("id").getAsInt();

                            nodes.remove(nodeOf(nodes, id));
                            nodes.forEach(node -> children(node.getAsJsonObject()).remove(new JsonPrimitive(id)));
                            list(tree, "roots").remove(new JsonPrimitive(id));
                        }
                        case "move" -> {
                            JsonObject node = nodeOf(nodes, op.get("id").getAsInt());
                            node.addProperty("x", op.get("x").getAsInt());
                            node.addProperty("y", op.get("y").getAsInt());
                        }
                        case "set_parent" -> {
                            int id = op.get("id").getAsInt();

                            nodes.forEach(node -> children(node.getAsJsonObject()).remove(new JsonPrimitive(id)));
                            list(tree, "roots").remove(new JsonPrimitive(id));

                            attach(tree, nodes, op.get("parent"), id);
                        }
                        case "set_node" -> nodeOf(nodes, op.get("id").getAsInt()).add("node", op.get("node"));
                        case "set_abort_mode" -> nodeOf(nodes, op.get("id").getAsInt()).addProperty("abort_mode", op.get("mode").getAsString());
                        case "add_condition", "add_subtask" -> {
                            JsonObject attachment = new JsonObject();
                            attachment.add("node", op.get("node"));
                            attachment.addProperty("interval", op.has("interval") ? op.get("interval").getAsInt() : 10);
                            list(nodeOf(nodes, op.get("id").getAsInt()), name.equals("add_condition") ? "conditions" : "subtasks").add(attachment);
                        }
                        case "remove_condition", "remove_subtask" ->
                            list(nodeOf(nodes, op.get("id").getAsInt()), name.equals("remove_condition") ? "conditions" : "subtasks").remove(op.get("index").getAsInt());
                        default -> throw new IllegalArgumentException("Unknown op " + name);
                    }
                }

                canvas.replaceData(BehaviorTreeData.CODEC.parse(JsonOps.INSTANCE, tree).getOrThrow(IllegalArgumentException::new));

                return McpToolResult.text(report.isEmpty() ? "Done" : String.join("\n", report));
            })
        ));

        McpTools.register(McpTool.of(
            "get_state_machine",
            "Returns the state machine 'name' of the behavior tree asset open on the top editor screen, as JSON: the entry "
                + "transitions, each state with its signature, position, transitions and groups, and the group nodes. A "
                + "transition carries 'to', 'transition_priority', 'abort_task_immediately' and 'check_latency'; its condition "
                + "graph is edited with edit_script_graph. A State Machine task names the machine it runs.",
            """
            {
              "type": "object",
              "properties": {"name": {"type": "string"}},
              "required": ["name"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                StateMachineGraphWidget machine = extension().openStateMachine(arguments.get("name").getAsString());
                JsonElement json = StateMachineData.codec(StateMachineData.Kind.BEHAVIOR).encodeStart(JsonOps.INSTANCE, machine.exportData()).getOrThrow();

                stripGraphs(json);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(json));
            })
        ));

        McpTools.register(McpTool.of(
            "edit_state_machine",
            "Edits the state machine 'name' of the behavior tree asset open on the top editor screen, as one undoable step, "
                + "creating it when missing. Ops, applied in order: {op:'add_state', state:<signature id>, x, y}, "
                + "{op:'remove_state', state}, {op:'add_transition', from:<signature id or 'entry'>, to}, "
                + "{op:'remove_transition', from, to}, {op:'set_transition', from, to, abort_task_immediately?, check_latency?, "
                + "transition_priority?}. Signatures are entries of the state_ids/mob_behavior data block.",
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["name", "ops"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                StateMachineGraphWidget machine = extension().openStateMachine(arguments.get("name").getAsString());
                JsonObject json = StateMachineData.codec(StateMachineData.Kind.BEHAVIOR).encodeStart(JsonOps.INSTANCE, machine.exportData())
                    .getOrThrow().getAsJsonObject();
                JsonArray states = json.has("states") ? json.getAsJsonArray("states") : new JsonArray();

                json.add("states", states);

                for (JsonElement element : arguments.getAsJsonArray("ops")) {
                    JsonObject op = element.getAsJsonObject();
                    String name = op.get("op").getAsString();

                    switch (name) {
                        case "add_state" -> {
                            JsonObject state = new JsonObject();
                            state.addProperty("state", op.get("state").getAsString());
                            state.addProperty("x", op.has("x") ? op.get("x").getAsInt() : 0);
                            state.addProperty("y", op.has("y") ? op.get("y").getAsInt() : 0);
                            states.add(state);
                        }
                        case "remove_state" -> {
                            String id = op.get("state").getAsString();

                            states.remove(stateOf(states, id));
                            transitionLists(json).forEach(list -> list.asList().removeIf(t -> t.getAsJsonObject().get("to").getAsString().equals(id)));
                        }
                        case "add_transition" -> {
                            JsonObject transition = new JsonObject();
                            transition.addProperty("to", op.get("to").getAsString());
                            transitionsFrom(json, states, op.get("from").getAsString()).add(transition);
                        }
                        case "remove_transition" -> transitionsFrom(json, states, op.get("from").getAsString()).asList()
                            .removeIf(t -> t.getAsJsonObject().get("to").getAsString().equals(op.get("to").getAsString()));
                        case "set_transition" -> {
                            JsonObject transition = transitionsFrom(json, states, op.get("from").getAsString()).asList().stream()
                                .map(JsonElement::getAsJsonObject)
                                .filter(t -> t.get("to").getAsString().equals(op.get("to").getAsString()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("No transition " + op.get("from") + " to " + op.get("to")));

                            for (String field : List.of("abort_task_immediately", "check_latency", "transition_priority")) {
                                if (op.has(field)) transition.add(field, op.get(field));
                            }
                        }
                        default -> throw new IllegalArgumentException("Unknown op " + name);
                    }
                }

                machine.replaceData(StateMachineData.codec(StateMachineData.Kind.BEHAVIOR).parse(JsonOps.INSTANCE, json)
                    .getOrThrow(IllegalArgumentException::new));

                return McpToolResult.text("Done");
            })
        ));
    }

    private static BehaviorTreeEditorExtension extension() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (screen instanceof ScriptBlueprintEditorScreen<?> editor && editor.getExtension() instanceof BehaviorTreeEditorExtension extension) {
            return extension;
        }

        throw new IllegalStateException("The top editor screen is no behavior tree asset");
    }

    private static BehaviorTreeGraphWidget treeCanvas(final JsonObject arguments) {
        BehaviorTreeEditorExtension extension = extension();

        if (arguments.has("state_machine") && arguments.has("state")) {
            return extension.openStateTree(arguments.get("state_machine").getAsString(), arguments.get("state").getAsString());
        }

        BehaviorTreeGraphWidget root = extension.getRootTree();

        if (root == null) throw new IllegalStateException("The behavior tree tab is not built yet");

        return root;
    }

    /// Makes node `id` the last child of `parent`, or the last node ROOT runs when `parent` is `"root"`.
    private static void attach(final JsonObject tree, final JsonArray nodes, final JsonElement parent, final int id) {
        if (parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString() && parent.getAsString().equals("root")) {
            list(tree, "roots").add(id);
        } else {
            children(nodeOf(nodes, parent.getAsInt())).add(id);
        }
    }

    private static JsonObject nodeOf(final JsonArray nodes, final int id) {
        for (JsonElement node : nodes) {
            if (node.getAsJsonObject().get("id").getAsInt() == id) return node.getAsJsonObject();
        }

        throw new IllegalArgumentException("No node " + id);
    }

    private static JsonArray children(final JsonObject node) {
        return list(node, "children");
    }

    private static JsonArray list(final JsonObject owner, final String field) {
        if (!owner.has(field)) owner.add(field, new JsonArray());

        return owner.getAsJsonArray(field);
    }

    private static JsonObject stateOf(final JsonArray states, final String id) {
        for (JsonElement state : states) {
            if (state.getAsJsonObject().get("state").getAsString().equals(id)) return state.getAsJsonObject();
        }

        throw new IllegalArgumentException("No state " + id);
    }

    private static JsonArray transitionsFrom(final JsonObject machine, final JsonArray states, final String from) {
        return from.equals("entry") ? list(machine, "entry_transitions") : list(stateOf(states, from), "transitions");
    }

    private static List<JsonArray> transitionLists(final JsonObject machine) {
        List<JsonArray> lists = new ArrayList<>();

        lists.add(list(machine, "entry_transitions"));
        list(machine, "states").forEach(state -> lists.add(list(state.getAsJsonObject(), "transitions")));
        list(machine, "groups").forEach(group -> lists.add(list(group.getAsJsonObject(), "transitions")));

        return lists;
    }

    /// Drops the condition graphs and state trees from a machine's JSON: they are read with get_script_graph and
    /// get_behavior_tree.
    private static void stripGraphs(final JsonElement machine) {
        if (!machine.isJsonObject()) return;

        JsonObject object = machine.getAsJsonObject();

        object.remove("condition_graph");
        object.remove("tree");
        object.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonArray()) entry.getValue().getAsJsonArray().forEach(McpBehaviorTools::stripGraphs);
            if (entry.getValue().isJsonObject()) stripGraphs(entry.getValue());
        });
    }
}
