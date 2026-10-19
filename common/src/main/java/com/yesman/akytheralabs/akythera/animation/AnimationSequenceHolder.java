package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import com.yesman.akytheralabs.SourceTemplateEngine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Modifiable data holder for [AnimationSequence]
public final class AnimationSequenceHolder extends AbstractDataHolder<AnimationSequence>
    implements PlayableAnimationHolder<AnimationSequence>, DTOProvider
{
    public static final Codec<AnimationSequenceHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            EditorAssetAccessors.ANIMATION_CODEC.fieldOf("animation_path").forGetter(AnimationSequenceHolder::getAnimClip),
            Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationSequenceHolder::getFadeInTime),
            Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationSequenceHolder::getFadeOutTime),
            Codec.BOOL.fieldOf("loops").forGetter(AnimationSequenceHolder::loops),
            Codec.BOOL.fieldOf("root_motion").forGetter(AnimationSequenceHolder::isRootMotion),
            Codec.FLOAT.optionalFieldOf("rate_scale", 1.0F).forGetter(AnimationSequenceHolder::rateScale),
            Codec.FLOAT.optionalFieldOf("phase_offset", 0.0F).forGetter(AnimationSequenceHolder::phaseOffset),
            Identifier.CODEC.optionalFieldOf("preview_entity_type")
                .forGetter(ParseUtils.provideOptional(AnimationSequenceHolder::getPreviewEntityType)),
            PreviewItem.CODEC.listOf().optionalFieldOf("preview_items", List.of())
                .forGetter(AnimationSequenceHolder::getPreviewItems),
            NotifyTrackHolder.EDITOR_CODEC.optionalFieldOf("notify_tracks", new NotifyTrackHolder())
                .forGetter(AnimationSequenceHolder::getNotifyTrackHolder)
        ).apply(instance, (animClip, fadeInTime, fadeOutTime, loops, rootMotion, rateScale, phaseOffset, previewEntityType, previewItems, notifyTrackHolder) -> {
            AnimationSequenceHolder h = new AnimationSequenceHolder();
            h.setAnimClip(animClip);

            h.setFadeInTime(fadeInTime);
            h.setFadeOutTime(fadeOutTime);
            h.setLoops(loops);
            h.setRootMotion(rootMotion);
            h.setRateScale(rateScale);
            h.setPhaseOffset(phaseOffset);
            previewEntityType.ifPresent(h::setPreviewEntityType);
            h.setPreviewItems(previewItems);

            if (!notifyTrackHolder.getTracks().isEmpty()) {
                h.notifyTrackHolder = notifyTrackHolder;
            }

            return h;
        })
    );

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_ANIMATION, order = 0)
    private AssetAccessor<AnimationClip> animClip;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_FADE_IN, min = 0F, order = 1)
    private float fadeInTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_FADE_OUT, min = 0F, order = 2)
    private float fadeOutTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_LOOPS, order = 3)
    private boolean loops;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_ROOT_MOTION, order = 4)
    private boolean rootMotion;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_RATE_SCALE, min = 0.01F, order = 5)
    private float rateScale;

    @EditorEditable(label = LangKeys.GUI_ANIM_SEQUENCE_SCREEN_WIDGET_PHASE_OFFSET, min = 0F, max = 1F, order = 6)
    private float phaseOffset;

    @Nullable
    private Identifier previewEntityType;

    /// The items the preview holds, one per slot. Editor-only.
    private List<PreviewItem> previewItems = List.of();

    private NotifyTrackHolder notifyTrackHolder = new NotifyTrackHolder();

    /// Notified with the new value whenever [#rootMotion] flips.
    @Nullable
    private Consumer<Boolean> rootMotionChangeListener;

    /// Notified whenever [#animClip] is replaced through [#setAnimClip].
    @Nullable
    private Runnable animClipChangeListener;

    public AnimationSequenceHolder() {
        animClip = AnimationManager.EMPTY_ANIMATION_ACCESSOR;
        fadeInTime = PlayableAnimation.DEFAULT_FADE_TIME;
        fadeOutTime = PlayableAnimation.DEFAULT_FADE_TIME;
        rateScale = 1.0F;
    }

    @Override
    public void readFrom(AnimationSequence object) {
        animClip = object.animClip();
        fadeInTime = object.fadeInTime();
        fadeOutTime = object.fadeOutTime();
        loops = object.loops();
        rootMotion = object.rootMotion();
        rateScale = object.rateScale();
        phaseOffset = object.phaseOffset();
        notifyTrackHolder.readFromEngineTrack(object.notifyTrack());
    }

    @Override
    public AnimationSequence makeObject() {
        return new AnimationSequence(notifyTrackHolder.toEngineTrack(), animClip, fadeInTime, fadeOutTime, loops, rootMotion, rateScale, phaseOffset);
    }

    public NotifyTrackHolder getNotifyTrackHolder() {
        return notifyTrackHolder;
    }

    /// The first broken notify row the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return notifyTrackHolder.firstFailure();
    }

    public AssetAccessor<AnimationClip> getAnimClip() {
        return animClip;
    }

    public void setAnimClip(AssetAccessor<AnimationClip> animClip) {
        this.animClip = animClip;
        if (animClipChangeListener != null) animClipChangeListener.run();
    }

    public void setAnimClipChangeListener(@Nullable Runnable listener) {
        animClipChangeListener = listener;
    }

    /// The editor-owned clip this sequence references. The engine empty and foreign-namespace
    /// accessors are not [EditorReference]s and yield an empty stream.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return animClip instanceof EditorReference ref ? Stream.of(ref) : Stream.empty();
    }

    @Override
    public float getFadeInTime() {
        return fadeInTime;
    }

    public void setFadeInTime(float v) {
        fadeInTime = v;
    }

    @Override
    public float getFadeOutTime() {
        return fadeOutTime;
    }

    @Override
    public float getTotalFrameTime() {
        return animClip.get().getTotalTime();
    }

    public void setFadeOutTime(float v) {
        fadeOutTime = v;
    }

    @Override
    public boolean loops() {
        return loops;
    }

    public void setLoops(boolean v) {
        loops = v;
    }

    public boolean isRootMotion() {
        return rootMotion;
    }

    public void setRootMotion(boolean v) {
        rootMotion = v;
        if (rootMotionChangeListener != null) rootMotionChangeListener.accept(v);
    }

    public float rateScale() {
        return rateScale;
    }

    public void setRateScale(float v) {
        rateScale = v;
    }

    public float phaseOffset() {
        return phaseOffset;
    }

    public void setPhaseOffset(float v) {
        phaseOffset = v;
    }

    public void setRootMotionChangeListener(@Nullable Consumer<Boolean> listener) {
        rootMotionChangeListener = listener;
    }

    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    public void setPreviewEntityType(@Nullable Identifier previewEntityType) {
        this.previewEntityType = previewEntityType;
    }

    public List<PreviewItem> getPreviewItems() {
        return previewItems;
    }

    public void setPreviewItems(List<PreviewItem> previewItems) {
        this.previewItems = List.copyOf(previewItems);
    }

    /// The tokens `animation_sequence_template.ftl` renders: the trimmed `animation_path` that the
    /// template re-expands through `AnimationManager.accessor`, the fade times and flags as Java
    /// literals, and the `notify_track` source from [NotifyTrackHolder#getDTO].
    @Override
    public Map<String, Object> getDTO() {
        Identifier animId = animClip.identifier();
        String trimmedPath = animId.getPath().replace(AnimationManager.DIRECTORY, "");

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("animation_path", animId.getNamespace() + ":" + trimmedPath);
        dto.put("fade_in_time", SourceTemplateEngine.floatLiteral(fadeInTime));
        dto.put("fade_out_time", SourceTemplateEngine.floatLiteral(fadeOutTime));
        dto.put("loops", Boolean.toString(loops));
        dto.put("root_motion", Boolean.toString(rootMotion));
        dto.put("rate_scale", SourceTemplateEngine.floatLiteral(rateScale));
        dto.put("phase_offset", SourceTemplateEngine.floatLiteral(phaseOffset));
        dto.put("notify_track", notifyTrackHolder.getDTO().get("notify_track"));

        return dto;
    }
}