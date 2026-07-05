package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// The **flattened** persisted form of a node-graph canvas (one blueprint flow entry —
/// construct / update / pose). Where the runtime [com.yesman.akythera.api.data.modularize.node.ScriptNode]
/// is a *tree* (a node holds its children inline), the editor file is *flat*: every node is
/// a row carrying its own id and graph position, and every wire is a row referencing two node
/// ids. This keeps the on-canvas layout (positions) and the exact wiring round-trippable, which
/// the tree form cannot express, and lets [com.yesman.akytheralabs.dataholder.animation.AnimatorScriptHolder]
/// re-derive the tree on demand (its `makeObject` "compile").
///
/// Produced by [ScriptNodeGraphWidget#exportGraph] and consumed by
/// [ScriptNodeGraphWidget#importGraph]; serialized through [#CODEC] inside the holder's editor codec.
public record ScriptGraphData(List<NodeData> nodes, List<WireData> wires) {
    public static final ScriptGraphData EMPTY = new ScriptGraphData(List.of(), List.of());

    /// Declared before [#CODEC]: that field forces [NodeData]'s static init, whose codec reads this
    /// one — so it must already be assigned (a later declaration would leave it null at that point).
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(
        s -> Kind.valueOf(s.toUpperCase(Locale.ROOT)),
        k -> k.name().toLowerCase(Locale.ROOT)
    );

    public static final Codec<ScriptGraphData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            NodeData.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(ScriptGraphData::nodes),
            WireData.CODEC.listOf().optionalFieldOf("wires", List.of()).forGetter(ScriptGraphData::wires)
        ).apply(instance, ScriptGraphData::new)
    );

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /// A copy with every [Kind#CACHED_POSE_GETTER] node referencing `oldName` retargeted to `newName`,
    /// keeping the getters in a (closed-tab) sub-graph pointing at a cache renamed on the Anim Graph.
    /// Returns `this` unchanged when no getter matches.
    public ScriptGraphData withCachedPoseGettersRenamed(String oldName, String newName) {
        boolean matches = nodes.stream().anyMatch(
            n -> n.kind() == Kind.CACHED_POSE_GETTER && n.variable().filter(oldName::equals).isPresent());
        if (!matches) return this;

        List<NodeData> renamed = nodes.stream()
            .map(n -> n.kind() == Kind.CACHED_POSE_GETTER && n.variable().filter(oldName::equals).isPresent()
                ? n.withVariable(newName, null)
                : n)
            .toList();
        return new ScriptGraphData(renamed, wires);
    }

    /// The kinds of node a canvas can hold. The discriminator that decides both how a row is
    /// rebuilt into a widget ([ScriptNodeGraphWidget#importGraph]) and how it is compiled into an
    /// engine node ([com.yesman.akytheralabs.dataholder.animation.ScriptGraphCompiler]).
    ///
    /// `REROUTE` knots are cosmetic and transparent to evaluation, so they are persisted to keep the
    /// on-canvas layout round-trippable but spliced through by the compiler (which traces each wire
    /// back past any chain of knots to the real source feeding it).
    public enum Kind {
        ENTRY, SEQUENCE, BRANCH, FLOW_FUNCTION, PURE_FUNCTION,
        LITERAL, SELF, VARIABLE_GETTER, VARIABLE_SETTER, COMPARATOR, OPERATOR, REROUTE,
        // Anim Graph (pose data-flow) nodes.
        OUTPUT_POSE, ANIM_SEQUENCE_PLAYER, BLEND_SPACE_PLAYER, STATE_MACHINE,
        CACHED_POSE, CACHED_POSE_GETTER, ANIM_LAYER,
        // Explicit numeric type conversion (its from/to ScriptTypes ride in variableType/literalType).
        TYPE_CONVERT,
        // Transition condition graph root + boolean logic.
        TRANSITION_RESULT, NOT, LOGICAL_OPERATION,
        // Entity-patch blueprint entry point (onConstruct / onJoinWorld / update); its kind rides in `op`.
        ENTITY_PATCH_ENTRY
    }

    /// One persisted node. Only the fields relevant to [#kind] are populated; the rest stay empty.
    ///
    /// @param id          stable identity within the graph, referenced by [WireData]. The entry
    ///                    node is always id 0.
    /// @param x           graph-space position (preserved so the canvas reopens unchanged).
    /// @param function    the [ScriptFunction] of a `FLOW_FUNCTION` / `PURE_FUNCTION` node.
    /// @param variable    the member-variable name of a `VARIABLE_GETTER` / `VARIABLE_SETTER` node.
    /// @param variableType the member-variable type, kept so the getter/setter widget rebuilds with
    ///                    the right pin colour/type without consulting the variables list.
    /// @param literalType the [ScriptType] of a `LITERAL` node.
    /// @param literalValue the literal's value in its raw text form (see [LiteralNodeWidget]).
    /// @param op          the operator enum name of a `COMPARATOR` / `OPERATOR` / `LOGICAL_OPERATION` node.
    /// @param outputs     the dynamic output-pin count of a `SEQUENCE` node (0 for fixed-shape nodes).
    /// @param exec        whether a `REROUTE` knot threads an execution wire (vs. a data value) — its
    ///                    one pin mirrors this so the knot reloads with the right shape and color.
    /// @param outLabels   the output-pin labels of the `ENTRY` node — the runtime context variable
    ///                    names (`frameTime` / `partialTick` / `layers`) its data outputs map to.
    /// @param asset       the bound project asset id of an `ANIM_SEQUENCE_PLAYER` (a sequence) or
    ///                    `BLEND_SPACE_PLAYER` (a blendspace) node — empty for every other kind.
    public record NodeData(
        int id,
        Kind kind,
        int x,
        int y,
        Optional<ScriptFunction> function,
        Optional<String> variable,
        Optional<ScriptType> variableType,
        Optional<ScriptType> literalType,
        Optional<String> literalValue,
        Optional<String> op,
        int outputs,
        boolean exec,
        List<String> outLabels,
        Optional<Identifier> asset
    ) {
        public static final Codec<NodeData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.INT.fieldOf("id").forGetter(NodeData::id),
                KIND_CODEC.fieldOf("kind").forGetter(NodeData::kind),
                Codec.INT.fieldOf("x").forGetter(NodeData::x),
                Codec.INT.fieldOf("y").forGetter(NodeData::y),
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().optionalFieldOf("function")
                    .forGetter(NodeData::function),
                Codec.STRING.optionalFieldOf("variable").forGetter(NodeData::variable),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("variable_type")
                    .forGetter(NodeData::variableType),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("literal_type")
                    .forGetter(NodeData::literalType),
                Codec.STRING.optionalFieldOf("literal_value").forGetter(NodeData::literalValue),
                Codec.STRING.optionalFieldOf("op").forGetter(NodeData::op),
                Codec.INT.optionalFieldOf("outputs", 0).forGetter(NodeData::outputs),
                Codec.BOOL.optionalFieldOf("exec", false).forGetter(NodeData::exec),
                Codec.STRING.listOf().optionalFieldOf("out_labels", List.of()).forGetter(NodeData::outLabels),
                Identifier.CODEC.optionalFieldOf("asset").forGetter(NodeData::asset)
            ).apply(instance, NodeData::new)
        );

        /// A bare node with only id/kind/position set; the kind-specific fields are filled by the
        /// `with*` builders so [ScriptNodeGraphWidget#exportGraph] reads cleanly.
        public static NodeData of(int id, Kind kind, int x, int y) {
            return new NodeData(id, kind, x, y,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, false, List.of(), Optional.empty());
        }

        public NodeData withFunction(ScriptFunction f) {
            return new NodeData(id, kind, x, y, Optional.of(f), variable, variableType,
                literalType, literalValue, op, outputs, exec, outLabels, asset);
        }

        public NodeData withVariable(String name, @Nullable ScriptType type) {
            return new NodeData(id, kind, x, y, function, Optional.of(name), Optional.ofNullable(type),
                literalType, literalValue, op, outputs, exec, outLabels, asset);
        }

        public NodeData withLiteral(@Nullable ScriptType type, String value) {
            return new NodeData(id, kind, x, y, function, variable, variableType,
                Optional.ofNullable(type), Optional.of(value), op, outputs, exec, outLabels, asset);
        }

        public NodeData withOp(String operatorName) {
            return new NodeData(id, kind, x, y, function, variable, variableType,
                literalType, literalValue, Optional.of(operatorName), outputs, exec, outLabels, asset);
        }

        public NodeData withOutputs(int count) {
            return new NodeData(id, kind, x, y, function, variable, variableType,
                literalType, literalValue, op, count, exec, outLabels, asset);
        }

        /// Records a `REROUTE` knot's pin shape: `exec` selects an execution vs. data wire, `type`
        /// the data value type (empty for an exec or wildcard knot) so it reloads with the right color.
        public NodeData withReroute(boolean exec, @Nullable ScriptType type) {
            return new NodeData(id, kind, x, y, function, variable, Optional.ofNullable(type),
                literalType, literalValue, op, outputs, exec, outLabels, asset);
        }

        public NodeData withOutLabels(List<String> labels) {
            return new NodeData(id, kind, x, y, function, variable, variableType,
                literalType, literalValue, op, outputs, exec, List.copyOf(labels), asset);
        }

        /// Records a `TYPE_CONVERT` node's cast: the `from` source type (in `variableType`) and the
        /// `to` target type (in `literalType`), so the converter reloads with the right pins and the
        /// compiler can rebuild the engine `TypeConvertNode`.
        public NodeData withTypeConvert(@Nullable ScriptType from, @Nullable ScriptType to) {
            return new NodeData(id, kind, x, y, function, variable, Optional.ofNullable(from),
                Optional.ofNullable(to), literalValue, op, outputs, exec, outLabels, asset);
        }

        /// Records the bound project asset id of a pose-player node (`ANIM_SEQUENCE_PLAYER` /
        /// `BLEND_SPACE_PLAYER`) so it reloads bound to the same sequence / blendspace.
        public NodeData withAsset(Identifier id) {
            return new NodeData(this.id, kind, x, y, function, variable, variableType,
                literalType, literalValue, op, outputs, exec, outLabels, Optional.of(id));
        }
    }

    /// One persisted wire: the output pin `fromPin` of node `from` feeds the input pin `toPin`
    /// of node `to`. Endpoints are node ids, resolved back to live widgets on import.
    public record WireData(int from, int fromPin, int to, int toPin) {
        public static final Codec<WireData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.INT.fieldOf("from").forGetter(WireData::from),
                Codec.INT.fieldOf("from_pin").forGetter(WireData::fromPin),
                Codec.INT.fieldOf("to").forGetter(WireData::to),
                Codec.INT.fieldOf("to_pin").forGetter(WireData::toPin)
            ).apply(instance, WireData::new)
        );
    }
}
