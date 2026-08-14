package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.PoseCachingNodeWidget;

/// Caches the pose wired into it under [#cacheName], so a [CachedPoseGetterNodeData] elsewhere on the
/// same Anim Graph reuses it without re-evaluating the sub-tree. A sink: the pose compiler registers
/// it at construct time.
public record CachedPoseNodeData(String cacheName) implements NodeData {
    public static final MapCodec<CachedPoseNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("variable", "").forGetter(CachedPoseNodeData::cacheName)
            ).apply(instance, CachedPoseNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new PoseCachingNodeWidget(ctx.font(), cacheName, ctx.x(), ctx.y());
    }
}
