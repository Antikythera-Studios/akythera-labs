package com.yesman.akytheralabs.dataholder.animation;

import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akytheralabs.dataholder.DataHolder;

/// Holder version interface for [PlayableAnimation]
public interface PlayableAnimationHolder<T> extends DataHolder<T> {
    /// Pose blending time when montage starts to play
    float getFadeInTime();

    /// Pose blending time when montage terminates
    float getFadeOutTime();

    /// Returns a max frame time of animation
    float getTotalFrameTime();

    /// Returns whether the animation should loop when it ends
    boolean loops();
}
