package com.yesman.akytheralabs.gui.widget.script;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.node.CachedPoseGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// The **flattened** persisted form of a node-graph canvas (one blueprint flow entry —
/// construct / update / pose). Where the runtime [com.yesman.akythera.api.data.modularize.node.ScriptNode]
/// is a *tree* (a node holds its children inline), the editor file is *flat*: every node is a row
/// carrying its own id and graph position, and every wire is a row referencing two node ids. This
/// keeps the on-canvas layout and the exact wiring round-trippable, which the tree form cannot
/// express, and lets [AnimatorScriptHolder] re-derive the tree on demand (its `makeObject` "compile").
///
/// Produced by [ScriptNodeGraphWidget#exportGraph] and consumed by [ScriptNodeGraphWidget#importGraph];
/// serialized through [#CODEC] inside the holder's editor codec.
public record ScriptGraphData(List<GraphNode> nodes, List<WireData> wires, List<FailedNode> failedNodes) {
    /// Convenience form for the (overwhelmingly common) case of a graph with no failed rows —
    /// every live-editing construction site uses this.
    public ScriptGraphData(List<GraphNode> nodes, List<WireData> wires) {
        this(nodes, wires, List.of());
    }

    public static final ScriptGraphData EMPTY = new ScriptGraphData(List.of(), List.of());

    /// One node row that failed to decode (unregistered kind, or a function / type that no longer
    /// resolves): enough of the row to keep it on canvas (id for wires, x/y for placement —
    /// best-effort re-read from the raw), the raw JSON re-emitted verbatim on save, and the failure
    /// message for the red banner / tooltip.
    public record FailedNode(int id, int x, int y, JsonElement raw, String message) {}

    /// Never-failing per-node codec: a row [GraphNode#CODEC] can't decode becomes a [FailedNode]
    /// carrying the raw JSON instead of failing the whole graph. Encode re-emits a failed row's raw
    /// verbatim.
    private static final Codec<Either<GraphNode, FailedNode>> NODE_ELEMENT_CODEC = new Codec<>() {
        @Override
        public <D> DataResult<Pair<Either<GraphNode, FailedNode>, D>> decode(DynamicOps<D> ops, D input) {
            String message;

            try {
                DataResult<Pair<GraphNode, D>> result = GraphNode.CODEC.decode(ops, input);

                if (result.result().isPresent()) {
                    return result.map(p -> p.mapFirst(Either::left));
                }

                message = result.error().map(DataResult.Error::message).orElse("Unknown node decode error");
            } catch (Exception e) {
                message = e.toString();
            }

            Dynamic<D> d = new Dynamic<>(ops, input);
            FailedNode failed = new FailedNode(
                d.get("id").asInt(-1), d.get("x").asInt(0), d.get("y").asInt(0),
                d.convert(JsonOps.INSTANCE).getValue(), message
            );
            AkytheraLabs.LOGGER.warn("Keeping unresolved graph node (id {}): {}", failed.id(), message);

            return DataResult.success(Pair.of(Either.right(failed), ops.empty()));
        }

        @Override
        public <D> DataResult<D> encode(Either<GraphNode, FailedNode> input, DynamicOps<D> ops, D prefix) {
            return input.map(
                node -> GraphNode.CODEC.encode(node, ops, prefix),
                failed -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, failed.raw()).convert(ops).getValue())
            );
        }
    };

    public static final Codec<ScriptGraphData> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                NODE_ELEMENT_CODEC.listOf().optionalFieldOf("nodes", List.of())
                    .forGetter(d ->
                        Stream.concat(
                            d.nodes.stream().map(Either::<GraphNode, FailedNode>left),
                            d.failedNodes.stream().map(Either::<GraphNode, FailedNode>right)
                        ).toList()
                    ),
                WireData.CODEC.listOf().optionalFieldOf("wires", List.of()).forGetter(ScriptGraphData::wires)
            ).apply(instance, (elements, wires) -> {
                List<GraphNode> nodes = new ArrayList<>();
                List<FailedNode> failed = new ArrayList<>();

                for (Either<GraphNode, FailedNode> e : elements) {
                    e.ifLeft(nodes::add).ifRight(failed::add);
                }

                return new ScriptGraphData(List.copyOf(nodes), wires, List.copyOf(failed));
            })
    );

    public boolean isEmpty() {
        return nodes.isEmpty() && failedNodes.isEmpty();
    }

    /// Compile gate: any unresolved (failed-decode) node is a hard compile error — the failed row was
    /// *something*, and compiling without it would produce a script that behaves differently from
    /// what the file describes. Every graph compiler calls this on entry; the editor screens
    /// pre-check [#failedNodes] to report the rows individually before this throw is reachable.
    public void requireNoFailedNodes() {
        if (!failedNodes.isEmpty()) {
            throw new IllegalStateException(
                failedNodes.size() + " unresolved node(s) in the graph — fix or delete them before compiling");
        }
    }

    /// The first unresolved (failed-decode) node as a reportable exception, or null when the graph is
    /// clean. Feeds the aggregate nested-failure signal a root holder surfaces on its content tile: a
    /// graph the guard salvaged around a broken row leaves the holder structurally valid but corrupt.
    public @Nullable Exception firstFailure() {
        if (failedNodes.isEmpty()) return null;

        FailedNode f = failedNodes.getFirst();

        return new IllegalStateException("Unresolved node (id " + f.id() + "): " + f.message());
    }

    /// The shared rename-tracking instances this canvas' nodes bind, for the owning holder's
    /// [com.yesman.akytheralabs.dataholder.DataHolder#referencedReferences]. A failed row's references
    /// stay inside its raw JSON, which is re-emitted verbatim, so it contributes none.
    public Stream<EditorReference> referencedReferences() {
        return nodes.stream().flatMap(n -> n.data().referencedReferences());
    }

    /// The ids this canvas names that resolve to nothing (see [NodeData#missingReferences]).
    public Stream<Identifier> missingReferences() {
        return nodes.stream().flatMap(n -> n.data().missingReferences());
    }

    /// Every row whose payload is of `type`, in row order.
    public <D extends NodeData> Stream<GraphNode> nodesOf(Class<D> type) {
        return nodes.stream().filter(n -> type.isInstance(n.data()));
    }

    /// A copy with every cached-pose getter referencing `oldName` retargeted to `newName`, keeping the
    /// getters in a (closed-tab) sub-graph pointing at a cache renamed on the Anim Graph. Returns
    /// `this` unchanged when no getter matches.
    public ScriptGraphData withCachedPoseGettersRenamed(String oldName, String newName) {
        boolean matches = nodes.stream().anyMatch(
            n -> n.data() instanceof CachedPoseGetterNodeData getter && getter.cacheName().equals(oldName));

        if (!matches) return this;

        List<GraphNode> renamed = nodes.stream()
            .map(n -> n.data() instanceof CachedPoseGetterNodeData getter && getter.cacheName().equals(oldName)
                ? n.withData(new CachedPoseGetterNodeData(newName))
                : n)
            .toList();

        return new ScriptGraphData(renamed, wires, failedNodes);
    }

    /// One persisted wire: the output pin `fromPin` of node `from` feeds the input pin `toPin` of
    /// node `to`. Endpoints are node ids, resolved back to live widgets on import.
    public record WireData(int from, int fromPin, int to, int toPin) {
        public static final Codec<WireData> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.INT.fieldOf("from").forGetter(WireData::from),
                    Codec.INT.fieldOf("from_pin").forGetter(WireData::fromPin),
                    Codec.INT.fieldOf("to").forGetter(WireData::to),
                    Codec.INT.fieldOf("to_pin").forGetter(WireData::toPin)
                ).apply(instance, WireData::new)
        );
    }
}
