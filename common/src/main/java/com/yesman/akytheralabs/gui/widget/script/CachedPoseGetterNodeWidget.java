package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akytheralabs.akythera.animation.AnimGraphCompiler;
import com.yesman.akytheralabs.gui.widget.script.node.CachedPoseGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Reads a pose cached by a [PoseCachingNodeWidget] (UE's "Use cached pose '<name>'") — a pose source
/// with no inputs and one `Pose` output. If the cache is empty when first read, the provider evaluates
/// and stores it.
///
/// Compile rule, owned by [AnimGraphCompiler] rather than a node method: lowers to the engine
/// [CachedPoseReferenceNode] keyed by [#cacheName], which reads the matching pose cache entry. A
/// recursive reference — a getter reached by its own cache's evaluation — is accepted here and
/// loops at runtime.
public final class CachedPoseGetterNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private String cacheName;

    private boolean controlReferenceDriver;

    public CachedPoseGetterNodeWidget(
        Font font, String cacheName, boolean controlReferenceDriver, int graphX, int graphY
    ) {
        super(font, Component.literal("Use cached pose '" + cacheName + "'"), graphX, graphY);

        this.cacheName = cacheName;
        this.controlReferenceDriver = controlReferenceDriver;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The cache name this getter reads — matches a [PoseCachingNodeWidget#cacheName] on the same graph.
    public String cacheName() {
        return cacheName;
    }

    /// Retargets this getter to a renamed cache (title + name), keeping it pointing at the same pose.
    public void setCacheName(String name) {
        this.cacheName = name;
        setMessage(Component.literal("Use cached pose '" + name + "'"));
        relayout();
    }

    /// Whether this reference drives the cached pose's own driver — the `controlReferenceDriver` of the
    /// [com.yesman.akythera.core.animation.driver.CachedPoseReference] it compiles into — and its
    /// setter, which the Details panel's Control Reference Driver row reads and writes.
    public boolean controlReferenceDriver() {
        return controlReferenceDriver;
    }

    public void setControlReferenceDriver(boolean controlReferenceDriver) {
        this.controlReferenceDriver = controlReferenceDriver;
    }

    @Override
    public NodeData toData() {
        return new CachedPoseGetterNodeData(cacheName, controlReferenceDriver);
    }
}
