package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;

/// An editor screen whose viewport can play one animation on demand.
///
/// [EditorWidgetFactory] makes an animation row in the Details panel pressable when the open screen
/// answers this, so pressing the row shows the clip on the screen's preview model.
public interface AnimationPreviewHost {
    /// Plays `clip` on the preview, bringing it into view. Loops and advances itself.
    void previewAnimation(AssetAccessor<AnimationClip> clip);
}
