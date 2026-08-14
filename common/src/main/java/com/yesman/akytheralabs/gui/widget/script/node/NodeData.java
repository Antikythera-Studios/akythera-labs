package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/// One persisted node's payload — everything about a node that is **not** its id or its canvas
/// position (those live on [GraphNode]). One implementation per node type, each declaring only the
/// fields that type actually has.
///
/// A type takes part in the editor by overriding the hooks it needs and inheriting the rest:
///
///   - [#createWidget] (required) — rebuild the canvas widget this row describes;
///   - [#compilePure] — lower this node's output into an engine [PureNode];
///   - [FlowNodeData] (implement, for statement nodes) — take part in the flow skeleton;
///   - [#valueInputs] / [#shareableOutput] — take part in the flow compiler's common-subexpression
///     elimination;
///   - [#passThrough] — be spliced out of wire traversal (a reroute knot);
///   - [#referencedReferences] / [#missingReferences] — take part in the rename re-save sweep and
///     the pre-compile dangling-reference gate.
///
/// The inverse direction is [AbstractNodeWidget#toData]. Adding a node type means writing those two
/// methods and one [ScriptNodeTypes] registration — nothing else.
public interface NodeData {
    /// This node type's payload codec — and its dispatch discriminator: [ScriptNodeTypes] maps it to
    /// and from the `kind` written in the file. Always the type's own `CODEC` constant, so the
    /// instance is stable (the encode-side lookup is by identity).
    MapCodec<? extends NodeData> mapCodec();

    /// Rebuilds the canvas widget for this row. Null drops the row from the canvas (an entry row on a
    /// canvas whose host supplies no entry widget).
    @Nullable AbstractNodeWidget createWidget(NodeBuildContext ctx);

    /// This node's output pin `fromPin` as an engine [PureNode], or null when this node produces no
    /// value there (a sink, or a source the hosting canvas does not support — the caller then
    /// substitutes [PureCompileContext#defaultValue]).
    default @Nullable PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return null;
    }

    /// Whether wire traversal splices straight through this node to whatever feeds its input 0 — a
    /// reroute knot, which is layout only and transparent to evaluation.
    default boolean passThrough() {
        return false;
    }

    /// Whether an output pin of this node may be hoisted into a `cse_<id>_<pin>` local when more than
    /// one consumer reads it (see [com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler]).
    default boolean shareableOutput() {
        return false;
    }

    /// The data input pins this node reads when lowered as a value, for the shared-output walk.
    default IntStream valueInputs() {
        return IntStream.empty();
    }

    /// The shared rename-tracking instances this node binds
    /// (see [com.yesman.akytheralabs.dataholder.DataHolder#referencedReferences]).
    default Stream<EditorReference> referencedReferences() {
        return Stream.empty();
    }

    /// The ids this node names that currently resolve to nothing — reported on the content tile and
    /// refused by the compile gate.
    default Stream<Identifier> missingReferences() {
        return Stream.empty();
    }
}
