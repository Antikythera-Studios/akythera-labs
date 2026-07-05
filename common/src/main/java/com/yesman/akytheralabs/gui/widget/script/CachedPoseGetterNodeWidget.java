package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Reads a pose cached by a [CachedPoseNodeWidget] (UE's "Use cached pose '<name>'") — a pose source
/// with no inputs and one `Pose` output. If the cache is empty when first read, the provider evaluates
/// and stores it.
///
/// Compile rule (in [com.yesman.akytheralabs.dataholder.animation.AnimGraphCompiler], not a node
/// method): lowers to the engine
/// [CachedPoseReferenceNode] keyed by [#cacheName], which
/// reads the matching `cachedPoseProviders` entry. A **recursive** reference (a getter reached by its
/// own cache's evaluation) is not rejected and would loop at runtime — author with care.
public final class CachedPoseGetterNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private String cacheName;

    public CachedPoseGetterNodeWidget(Font font, String cacheName, int graphX, int graphY) {
        super(font, Component.literal("Use cached pose '" + cacheName + "'"), graphX, graphY);

        this.cacheName = cacheName;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The cache name this getter reads — matches a [CachedPoseNodeWidget#cacheName] on the same graph.
    public String cacheName() {
        return cacheName;
    }

    /// Retargets this getter to a renamed cache (title + name), keeping it pointing at the same pose.
    public void setCacheName(String name) {
        this.cacheName = name;
        setMessage(Component.literal("Use cached pose '" + name + "'"));
        relayout();
    }
}
