package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/// The target of a [MotionWarpingNotifyStateHolder] as the editor holds it: a built-in target or a Java class, or a
/// data pack entry held as the shared live holder.
public sealed interface MotionWarpTargetRef {
    static MotionWarpTargetRef of(final MotionWarpTargetBlueprint blueprint) {
        return blueprint instanceof MotionWarpTargetBlueprint.ByData byData
            ? new Entry(EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY, byData.target().identifier()))
            : new Blueprint(blueprint);
    }

    MotionWarpTargetBlueprint toBlueprint();

    /// What the target select box shows.
    String displayLabel();

    /// A built-in target or a [MotionWarpTargetBlueprint.ByClass].
    record Blueprint(MotionWarpTargetBlueprint blueprint) implements MotionWarpTargetRef {
        @Override
        public MotionWarpTargetBlueprint toBlueprint() {
            return blueprint;
        }

        @Override
        public String displayLabel() {
            return blueprint instanceof MotionWarpTargetBlueprint.ByClass byClass
                ? byClass.type().type().getSimpleName()
                : ParseUtils.toDisplayName(Objects.requireNonNull(AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPES.getKey(blueprint.mapCodec())).getPath());
        }
    }

    /// An entry of the motion warp target data pack registry.
    record Entry(Holder<MotionWarpTargetScriptBlueprint> holder) implements MotionWarpTargetRef {
        public ResourceKey<MotionWarpTargetScriptBlueprint> key() {
            return holder.unwrapKey().orElseThrow();
        }

        @Override
        public MotionWarpTargetBlueprint toBlueprint() {
            return new MotionWarpTargetBlueprint.ByData(key());
        }

        @Override
        public String displayLabel() {
            return EditorHolderCodecs.entryId(holder).toString();
        }
    }
}
