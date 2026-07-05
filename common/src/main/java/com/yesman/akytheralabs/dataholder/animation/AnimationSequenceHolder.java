package com.yesman.akytheralabs.dataholder.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import com.yesman.akytheralabs.SourceTemplateEngine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Modifiable data holder for [AnimationSequence]
public final class AnimationSequenceHolder extends AbstractDataHolder<AnimationSequence>
    implements PlayableAnimationHolder<AnimationSequence>, DTOProvider
{
    public static final Codec<AnimationSequenceHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AnimationManager.CODEC.fieldOf("animation_path").forGetter(AnimationSequenceHolder::getAnimClip),
            Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationSequenceHolder::getFadeInTime),
            Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationSequenceHolder::getFadeOutTime),
            Codec.BOOL.fieldOf("loops").forGetter(AnimationSequenceHolder::loops),
            Codec.BOOL.fieldOf("root_motion").forGetter(AnimationSequenceHolder::isRootMotion),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(AnimationSequenceHolder::getPreviewModel)),
            NotifyTrackHolder.EDITOR_CODEC.optionalFieldOf("notify_tracks", new NotifyTrackHolder())
                .forGetter(AnimationSequenceHolder::getNotifyTrackHolder)
        ).apply(instance, (animClip, fadeInTime, fadeOutTime, loops, rootMotion, previewModel, notifyTrackHolder) -> {
            AnimationSequenceHolder h = new AnimationSequenceHolder();
            h.setAnimClip(animClip);

            h.setFadeInTime(fadeInTime);
            h.setFadeOutTime(fadeOutTime);
            h.setLoops(loops);
            h.setRootMotion(rootMotion);
            previewModel.ifPresent(h::setPreviewModel);

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

    @Nullable
    private EntityPreviewModel previewModel;

    private NotifyTrackHolder notifyTrackHolder = new NotifyTrackHolder();

    /// Editor-only: notified whenever [#rootMotion] flips (incl. undo/redo, since the
    /// history replay calls the setter). The editor screen uses it to add/remove the
    /// root-motion trajectory preview component.
    @Nullable
    private Consumer<Boolean> rootMotionChangeListener;

    /// Editor-only: notified whenever [#animClip] is replaced through the setter (incl. undo/redo).
    /// The editor screen uses it to replay the new clip on its previewer.
    @Nullable
    private Runnable animClipChangeListener;

    // Default constructor: assign the empty accessor
    public AnimationSequenceHolder() {
        animClip = AnimationManager.EMPTY_ANIMATION_ACCESSOR;
        fadeInTime = PlayableAnimation.DEFAULT_FADE_TIME;
        fadeOutTime = PlayableAnimation.DEFAULT_FADE_TIME;
    }

    @Override
    public void readFrom(AnimationSequence object) {
        animClip = object.animClip();
        fadeInTime = object.fadeInTime();
        fadeOutTime = object.fadeOutTime();
        loops = object.loops();
        rootMotion = object.rootMotion();
        notifyTrackHolder.readFromEngineTrack(object.notifyTrack());
    }

    @Override
    public AnimationSequence makeObject() {
        return new AnimationSequence(notifyTrackHolder.toEngineTrack(), animClip, fadeInTime, fadeOutTime, loops, rootMotion);
    }

    public NotifyTrackHolder getNotifyTrackHolder() {
        return notifyTrackHolder;
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

    /// The editor animation clip this sequence references, so a clip rename re-saves this sequence's
    /// JSON. Only editor-owned accessors are [EditorReference]s (the engine empty / foreign-namespace
    /// accessors are not), so they're filtered out. The accessor is shared, so it follows renames in
    /// memory for free; this only drives the on-disk re-save.
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

    public void setRootMotionChangeListener(@Nullable Consumer<Boolean> listener) {
        rootMotionChangeListener = listener;
    }

    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }

    /// Supplies the tokens the `animation_sequence_template.ftl` needs to rebuild this sequence: the
    /// trimmed `animation_path` (re-expanded by `AnimationManager.accessor` in the template), the fade
    /// times / flags as ready Java literals, and `notify_track` — the full notify-track source produced
    /// by [NotifyTrackHolder#getDTO] (either `AnimationNotifyTrack.EMPTY` or `new AnimationNotifyTrack(...)`).
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
        dto.put("notify_track", notifyTrackHolder.getDTO().get("notify_track"));

        return dto;
    }
}