package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import org.jspecify.annotations.Nullable;

/// The pose an additive animation is read relative to: which clip, and where on it.
///
/// @see com.yesman.akythera.core.asset.loader.AnimationLoader.ImportType#ADDITIVE
public record ReferencePose(@Nullable AssetAccessor<AnimationClip> clip, float timeStamp) {
    public static final ReferencePose EMPTY = new ReferencePose(null, 0.0F);

    public String displayName() {
        return clip == null ? AccessorSelectionModal.NONE_LABEL : AccessorSelectionModal.displayName(clip);
    }
}
