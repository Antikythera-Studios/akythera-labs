package com.yesman.akytheralabs.akythera.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.AbortMode;
import com.yesman.akythera.api.world.behavior.TreeNodeBlueprint;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// The persisted canvas of one behavior tree: the ROOT entry box, every placed node as a row with its own id and
/// position, the children by id, and the conditions and subtasks stacked on it. A node's parameters are kept as the
/// JSON of its node type, which the details panel edits field by field.
///
/// @param roots  the ids of the placements the ROOT entry runs, left to right
/// @param entryX the canvas x of the ROOT entry box
/// @param entryY the canvas y of the ROOT entry box
public record BehaviorTreeData(List<NodeData> nodes, List<Integer> roots, int entryX, int entryY) {
    public static final BehaviorTreeData EMPTY = new BehaviorTreeData(List.of(), List.of(), 0, 0);

    public static final Codec<BehaviorTreeData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                NodeData.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(BehaviorTreeData::nodes),
                Codec.INT.listOf().optionalFieldOf("roots", List.of()).forGetter(BehaviorTreeData::roots),
                Codec.INT.optionalFieldOf("entry_x", 0).forGetter(BehaviorTreeData::entryX),
                Codec.INT.optionalFieldOf("entry_y", 0).forGetter(BehaviorTreeData::entryY)
            ).apply(instance, BehaviorTreeData::new)
    );

    public BehaviorTreeData {
        nodes = List.copyOf(nodes);
        roots = List.copyOf(roots);
    }

    public @Nullable NodeData node(final int id) {
        return nodes.stream().filter(node -> node.id() == id).findFirst().orElse(null);
    }

    /// The rows ROOT runs, left to right. An id that names no row is left out.
    public List<NodeData> rootNodes() {
        return roots.stream().map(this::node).filter(Objects::nonNull).toList();
    }

    /// The placements ROOT runs in the engine form [TreeNodeBlueprint#CODEC] reads. A child id that names no row is
    /// left out.
    public List<JsonElement> compile() {
        return rootNodes().stream().<JsonElement>map(this::compile).toList();
    }

    private JsonObject compile(final NodeData node) {
        JsonObject placement = new JsonObject();
        JsonArray children = new JsonArray();
        JsonArray conditions = new JsonArray();
        JsonArray subtasks = new JsonArray();

        for (int childId : node.children()) {
            NodeData child = node(childId);

            if (child != null) children.add(compile(child));
        }

        for (Attachment condition : node.conditions()) {
            JsonObject entry = new JsonObject();
            entry.add("node", condition.node());
            conditions.add(entry);
        }

        for (Attachment subtask : node.subtasks()) {
            JsonObject entry = new JsonObject();
            entry.add("node", subtask.node());
            entry.addProperty("interval", subtask.interval());
            subtasks.add(entry);
        }

        placement.add("node", node.node());
        placement.add("children", children);
        placement.add("conditions", conditions);
        placement.add("subtasks", subtasks);
        placement.addProperty("abort_mode", node.abortMode().getSerializedName());

        return placement;
    }

    /// One placed task or flow control node.
    ///
    /// @param node     the node type's JSON, `type` included
    /// @param children the ids of the children, left to right
    public record NodeData(
        int id, int x, int y, JsonElement node, List<Integer> children, List<Attachment> conditions, List<Attachment> subtasks,
        AbortMode abortMode
    ) {
        public static final Codec<NodeData> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.INT.fieldOf("id").forGetter(NodeData::id),
                    Codec.INT.fieldOf("x").forGetter(NodeData::x),
                    Codec.INT.fieldOf("y").forGetter(NodeData::y),
                    ExtraCodecs.JSON.fieldOf("node").forGetter(NodeData::node),
                    Codec.INT.listOf().optionalFieldOf("children", List.of()).forGetter(NodeData::children),
                    Attachment.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(NodeData::conditions),
                    Attachment.CODEC.listOf().optionalFieldOf("subtasks", List.of()).forGetter(NodeData::subtasks),
                    AbortMode.CODEC.optionalFieldOf("abort_mode", AbortMode.NONE).forGetter(NodeData::abortMode)
                ).apply(instance, NodeData::new)
        );

        public NodeData {
            children = List.copyOf(children);
            conditions = List.copyOf(conditions);
            subtasks = List.copyOf(subtasks);
        }
    }

    /// A condition or a subtask stacked on a node.
    ///
    /// @param node     the node type's JSON, `type` included
    /// @param interval server ticks between two subtask ticks. Unused by a condition
    public record Attachment(JsonElement node, int interval) {
        public static final Codec<Attachment> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ExtraCodecs.JSON.fieldOf("node").forGetter(Attachment::node),
                    Codec.INT.optionalFieldOf("interval", 10).forGetter(Attachment::interval)
                ).apply(instance, Attachment::new)
        );
    }
}
