package com.yesman.akytheralabs.akythera.cinema;

import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// What the cinema scene editor reads off the montage an actor plays, from the open project's entry, else from the
/// loaded registry's. It plays the montage's clip straight through, with no section order.
///
/// @param clip              the montage's segments merged, the root track kept
/// @param fadeInTime        seconds the montage holds its first frame for
/// @param playRate          playback seconds per second
/// @param rootMotion        whether the root track moves the actor
/// @param previewEntityType the entity type the montage is previewed with. Null for a montage of the loaded registry,
///                          and for a project montage with none picked
public record CinemaMontagePreview(AnimationClip clip, float fadeInTime, float playRate, boolean rootMotion, @Nullable Identifier previewEntityType) {
    /// Null for a montage reference that is not picked, or that resolves to no entry.
    public static @Nullable CinemaMontagePreview of(final Holder<AnimationMontage> montage) {
        ResourceKey<AnimationMontage> key = montage.unwrapKey().orElse(null);
        AnimationMontageHolder holder = key == null
            ? null
            : AkytheraProject.getCurrentProject().<AnimationMontage, AnimationMontageHolder>getDataHolder(key).orElse(null);

        if (holder != null) {
            return new CinemaMontagePreview(holder.getMergedClip(), holder.getFadeInTime(), holder.playRate(), holder.isRootMotion(), holder.getPreviewEntityType());
        }

        if (!montage.isBound()) return null;

        AnimationMontage loaded = montage.value();

        return new CinemaMontagePreview(loaded.getMergedAnimClip(), loaded.fadeInTime(), loaded.playRate(), loaded.rootMotion(), null);
    }

    /// The montage's playback time `seconds` after it started playing.
    public float playbackTime(final float seconds) {
        return Math.clamp((seconds - fadeInTime) * playRate, 0.0F, clip.getTotalTime());
    }

    /// Seconds from the montage's start until its last frame.
    public float length() {
        return fadeInTime + clip.getTotalTime() / playRate;
    }
}
