package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.gui.widget.CompileResultToast;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// One run of Compile over an entry ([DataHolder#compile]). Collects the node errors of every canvas it
/// validates and the first page that failed; later checks still mark their canvases, but name no page.
public final class Compilation {
    private final Map<String, Map<Integer, List<Component>>> marks = new LinkedHashMap<>();

    private @Nullable Component failedPage;

    /// The editor's Compile on `holder`: `screenChecks` first, for what only the open screen knows, then
    /// [DataHolder#compile]. Records the outcome on the holder and pops the toast.
    public static void compileInEditor(final DataHolder<?> holder, final Consumer<Compilation> screenChecks) {
        Compilation compilation = new Compilation();

        screenChecks.accept(compilation);
        holder.compile(compilation);

        CompileFailure failure = compilation.result();

        if (failure != null) AkytheraLabs.LOGGER.warn("Compile failed on '{}'", failure.page().getString());

        CompileResultToast.show(holder, failure);
    }

    /// Fails the compile on `page`, unless an earlier check already did. Null passes.
    public void fail(final @Nullable Component page) {
        if (failedPage == null) failedPage = page;
    }

    public boolean failed() {
        return failedPage != null;
    }

    /// Validates `canvas` and keeps its node errors under `key`, failing on `page` when it holds any.
    public void canvas(final String key, final ScriptNodeGraphWidget canvas, final Component page) {
        canvas(key, canvas, page, null);
    }

    /// [#canvas(String, ScriptNodeGraphWidget, Component)], also flagging each node `payloadErrors` returns an
    /// error for ([ScriptNodeGraphWidget#addPayloadErrors]).
    public void canvas(
        final String key, final ScriptNodeGraphWidget canvas, final Component page,
        final @Nullable Function<NodeData, @Nullable Component> payloadErrors
    ) {
        boolean invalid = !canvas.validate().isEmpty();

        if (payloadErrors != null) canvas.addPayloadErrors(payloadErrors);

        Map<Integer, List<Component>> flagged = new HashMap<>();

        canvas.assignIds().forEach((node, id) -> {
            List<Component> errors = node.validationErrors();

            if (!errors.isEmpty()) flagged.put(id, errors);
        });

        if (!flagged.isEmpty()) marks.put(key, flagged);
        if (invalid || !flagged.isEmpty()) fail(page);
    }

    /// Fails on the unresolved (failed-decode) nodes `failedNodes`, which no compiler lowers.
    public void unresolved(final List<ScriptGraphData.FailedNode> failedNodes) {
        if (!failedNodes.isEmpty()) fail(Component.literal("Unresolved nodes: " + failedNodes.size()));
    }

    /// Builds the runtime object with `build` once every check passed, failing on its error.
    public void build(final Supplier<?> build) {
        if (failed()) return;

        try {
            build.get();
        } catch (RuntimeException e) {
            fail(Component.literal(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /// The failure to record, or null when the compile passed.
    public @Nullable CompileFailure result() {
        return failedPage == null ? null : new CompileFailure(failedPage, marks);
    }
}
