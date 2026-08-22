package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.CachedPoseGetterNodeWidget;
import org.jspecify.annotations.Nullable;

/// Reads the pose cached under [#cacheName] by a [CachedPoseNodeData] on the same graph.
///
/// `controlReferenceDriver` is
/// [com.yesman.akythera.core.animation.driver.CachedPoseReference#controlReferenceDriver].
public record CachedPoseGetterNodeData(String cacheName, boolean controlReferenceDriver) implements NodeData {
    /// Whether a freshly placed getter drives the cached pose's own driver: it does not.
    public static final boolean DEFAULT_CONTROL_REFERENCE_DRIVER = false;

    public static final MapCodec<CachedPoseGetterNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("variable", "").forGetter(CachedPoseGetterNodeData::cacheName),
                Codec.BOOL.optionalFieldOf("control_reference_driver", DEFAULT_CONTROL_REFERENCE_DRIVER).forGetter(CachedPoseGetterNodeData::controlReferenceDriver)
            ).apply(instance, CachedPoseGetterNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new CachedPoseGetterNodeWidget(ctx.font(), cacheName, controlReferenceDriver, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new CachedPoseReferenceNode(cacheName, controlReferenceDriver);
    }
}
