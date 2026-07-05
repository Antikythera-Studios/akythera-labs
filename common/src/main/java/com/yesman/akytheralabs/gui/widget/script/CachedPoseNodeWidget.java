package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Caches the [Pose] wired into it under a name (UE's "New Save cached pose"), so a
/// [CachedPoseGetterNodeWidget] elsewhere on the same Anim Graph can reuse it without re-evaluating
/// the sub-tree. A pose sink: one `Pose` input, no output.
///
/// Compile rule (in [com.yesman.akytheralabs.dataholder.animation.AnimGraphCompiler], not a node
/// method): the wired pose lowers to a [com.yesman.akythera.api.data.modularize.node.PureNode]
/// registered under [#cacheName] in the blueprint's `cachedPoseProviders` map. The engine
/// [CachedPoseReferenceNode] evaluates it lazily on first
/// reference and caches the result in the script locals for the rest of the frame.
public final class CachedPoseNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF2A2A2A;

    private String cacheName;

    public CachedPoseNodeWidget(Font font, String cacheName, int graphX, int graphY) {
        super(font, Component.literal("Cache: " + cacheName), graphX, graphY);

        this.cacheName = cacheName;

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The name this pose is cached under — referenced by a [CachedPoseGetterNodeWidget] and unique
    /// within the graph.
    public String cacheName() {
        return cacheName;
    }

    /// Renames the cache in place (title + name), e.g. when edited in the Details panel. The referencing
    /// [CachedPoseGetterNodeWidget]s are retargeted separately (see
    /// [ScriptNodeGraphWidget#renameCachedPoseGetters]).
    public void setCacheName(String name) {
        this.cacheName = name;
        setMessage(Component.literal("Cache: " + name));
        relayout();
    }
}
