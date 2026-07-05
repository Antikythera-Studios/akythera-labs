package com.yesman.akytheralabs.dataholder.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.animation.impl.AkytheraAnimationLayerSignature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Modifiable data holder for [AnimationMontage].
///
/// The merged preview clip is baked & owned here (see [#getMergedClip]) so a
/// segment edit hot-reloads the preview without rebuilding the whole montage.
public final class AnimationMontageHolder extends AbstractDataHolder<AnimationMontage>
    implements PlayableAnimationHolder<AnimationMontage>, DTOProvider
{
    public static final Codec<AnimationMontageHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AnimationManager.CODEC.listOf().optionalFieldOf("animations", List.of())
                .forGetter(h -> h.segments),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("sections", Map.of())
                .forGetter(h -> h.sections),
            Codec.STRING.listOf().listOf().optionalFieldOf("playing_orders", List.of())
                .forGetter(h -> h.playingOrders),
            Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationMontageHolder::getFadeInTime),
            Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationMontageHolder::getFadeOutTime),
            Codec.BOOL.fieldOf("loops").forGetter(AnimationMontageHolder::loops),
            Codec.BOOL.fieldOf("root_motion").forGetter(h -> h.rootMotion),
            AnimationLayerSignature.ENUM_MANAGER.getNameCodec()
                .optionalFieldOf("layer_signature", AkytheraAnimationLayerSignature.BASE)
                .forGetter(AnimationMontageHolder::layerSignature),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(AnimationMontageHolder::getPreviewModel)),
            NotifyTrackHolder.EDITOR_CODEC.optionalFieldOf("notify_tracks", new NotifyTrackHolder())
                .forGetter(AnimationMontageHolder::getNotifyTrackHolder)
        ).apply(instance, (segs, secs, orders, fin, fout, lps, rm, sig, pm, nth) -> {
            AnimationMontageHolder h = new AnimationMontageHolder();
            segs.forEach(h::addSegment);
            h.sections.putAll(secs);
            orders.forEach(seq -> h.playingOrders.add(new ArrayList<>(seq)));
            h.fadeInTime = fin;
            h.fadeOutTime = fout;
            h.loops = lps;
            h.rootMotion = rm;
            h.layerSignature = sig;
            pm.ifPresent(h::setPreviewModel);
            if (!nth.getTracks().isEmpty()) h.notifyTrackHolder = nth;
            return h;
        })
    );

    /// Ordered animation segments (drag&drop targets). Never null entries.
    private final List<AssetAccessor<AnimationClip>> segments = new ArrayList<>();

    /// Editor-side section model. name -> start time. Mutable, unlike SectionInfo.
    private final Map<String, Float> sections = new LinkedHashMap<>();

    /// Section playing orders — many sequences, each an ordered chain of section names.
    /// Edited by the Montage Sections tab as stacked combo rows.
    private final List<List<String>> playingOrders = new ArrayList<>();

    /// Reuse the exact same notify model as the sequence editor.
    private NotifyTrackHolder notifyTrackHolder = new NotifyTrackHolder();

    /// Editor-side merged preview clip — baked & owned here. Lazy: a segment edit
    /// flips `mergedClipDirty`, the bake runs on next [#getMergedClip], coalescing a
    /// burst of edits (and the bulk codec load) into one bake.
    @Nullable
    private AnimationClip cachedMergedClip;
    private boolean mergedClipDirty = true;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_FADE_IN, min = 0F, order = 0)
    private float fadeInTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_FADE_OUT, min = 0F, order = 1)
    private float fadeOutTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_LOOPS, order = 2)
    private boolean loops;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_ROOT_MOTION, order = 3)
    private boolean rootMotion;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_LAYER_SIGNATURE, order = 4)
    private AnimationLayerSignature layerSignature = AkytheraAnimationLayerSignature.BASE;

    @Nullable
    private EntityPreviewModel previewModel;

    /// Editor-only: notified whenever [#rootMotion] flips (incl. undo/redo, since the history
    /// replay calls the setter). The editor screen uses it to add/remove the trajectory preview.
    @Nullable
    private Consumer<Boolean> rootMotionChangeListener;

    public AnimationMontageHolder() {
        fadeInTime = PlayableAnimation.DEFAULT_FADE_TIME;
        fadeOutTime = PlayableAnimation.DEFAULT_FADE_TIME;
    }

    @Override
    public void readFrom(AnimationMontage object) {
        notifyTrackHolder.readFromEngineTrack(object.notifyTrack());

        segments.clear();
        object.getAnimSegments().forEach(this::addSegment);

        fadeInTime = object.fadeInTime();
        fadeOutTime = object.fadeOutTime();
        loops = object.loops();
        rootMotion = object.rootMotion();
        layerSignature = object.layerSignature();
        // TODO: SectionInfo exposes no public enumeration, so sections + playingOrder can't be
        // recovered here. Accepted (decision 1 = A) — the editor loads via EDITOR_CODEC, not readFrom.
        // Revisit only if importing a runtime montage back into the editor is ever needed.
    }

    @Override
    public AnimationMontage makeObject() {
        AnimationMontage.Builder b = AnimationMontage.builder();

        AnimationNotifyTrack track = notifyTrackHolder.toEngineTrack();
        for (AnimationNotifyTrack.NotifyInTimeStamp n : track.serializeNotifies()) {
            b.addAnimationNotify(n.animNotify(), n.timeStamp());
        }

        for (AnimationNotifyTrack.NotifyStateInTimeWindow ns : track.serializeNotifyStates()) {
            b.addAnimationNotifyState(ns.animNotifyState(), ns.timeWindow().from(), ns.timeWindow().to());
        }

        sections.forEach(b::createMontageSectionAt);

        for (List<String> seq : playingOrders) {
            if (!seq.isEmpty()) b.addSectionPlayingOrder(seq.toArray(new String[0]));
        }

        segments.forEach(b::addAnimSegment);

        return b.fadeInTime(fadeInTime).fadeOutTime(fadeOutTime)
            .loops(loops).rootMotionEnabled(rootMotion)
            .layerSignature(layerSignature)
            .create();
    }

    /// Supplies the tokens the `animation_montage_template.ftl` needs to rebuild this montage as an
    /// [AnimationMontage.Builder] chain: the segment ids (template wraps each in
    /// `AnimationManager.accessor(Identifier.parse(...))`), the notify / notify-state builder rows (each
    /// `expr` is the per-type fragment source via [com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder#toSourceCode]),
    /// the sections and playing orders, the fade times / flags as ready Java literals, and `layer_signature`
    /// — present only when it differs from the builder default ([AkytheraAnimationLayerSignature#BASE]).
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new LinkedHashMap<>();

        List<String> segmentIds = new ArrayList<>(segments.size());
        for (AssetAccessor<AnimationClip> seg : segments) {
            Identifier id = seg.identifier();
            String trimmedPath = id.getPath().replace(AnimationManager.DIRECTORY, "");
            segmentIds.add(id.getNamespace() + ":" + trimmedPath);
        }
        dto.put("segments", segmentIds);

        List<Map<String, Object>> notifyRows = new ArrayList<>();
        List<Map<String, Object>> stateRows = new ArrayList<>();
        for (NotifyTrackHolder.Track t : notifyTrackHolder.getTracks()) {
            for (NotifyTrackHolder.MutableNotify n : t.notifies) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("expr", n.holder.toSourceCode());
                row.put("time", SourceTemplateEngine.floatLiteral(n.time));
                notifyRows.add(row);
            }

            for (NotifyTrackHolder.MutableNotifyState ns : t.notifyStates) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("expr", ns.holder.toSourceCode());
                row.put("from", SourceTemplateEngine.floatLiteral(ns.from));
                row.put("to", SourceTemplateEngine.floatLiteral(ns.to));
                stateRows.add(row);
            }
        }
        dto.put("notifies", notifyRows);
        dto.put("notify_states", stateRows);

        List<Map<String, Object>> sectionRows = new ArrayList<>();
        sections.forEach((name, time) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("time", SourceTemplateEngine.floatLiteral(time));
            sectionRows.add(row);
        });
        dto.put("sections", sectionRows);

        List<String> orderArgs = new ArrayList<>();
        for (List<String> order : playingOrders) {
            if (order.isEmpty()) {
                continue;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < order.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(order.get(i)).append('"');
            }
            orderArgs.add(sb.toString());
        }
        dto.put("playing_orders", orderArgs);

        dto.put("fade_in_time", SourceTemplateEngine.floatLiteral(fadeInTime));
        dto.put("fade_out_time", SourceTemplateEngine.floatLiteral(fadeOutTime));
        dto.put("loops", Boolean.toString(loops));
        dto.put("root_motion", Boolean.toString(rootMotion));

        // Builder defaults to BASE, so only emit a .layerSignature(...) call when it differs. The only
        // registered implementation is the AkytheraAnimationLayerSignature enum, so reference it by name.
        if (layerSignature != AkytheraAnimationLayerSignature.BASE && layerSignature instanceof Enum<?> e) {
            dto.put("layer_signature", layerSignature.getClass().getSimpleName() + "." + e.name());
        }

        return dto;
    }

    /// ***************************************************************
    /// Segments — list ops flip the clip dirty flag
    /// ***************************************************************
    public void addSegment(AssetAccessor<AnimationClip> clip) {
        insertSegment(segments.size(), clip);
    }

    /// Drop a clip at a specific slot (drag from asset browser between two blocks).
    public void insertSegment(int index, AssetAccessor<AnimationClip> clip) {
        segments.add(index, clip);
        mergedClipDirty = true;
    }

    /// Reorder by dragging a block onto another block's edge.
    public void moveSegment(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= segments.size() || to >= segments.size()) return;
        segments.add(to, segments.remove(from));
        mergedClipDirty = true;
    }

    public void setSegment(int i, AssetAccessor<AnimationClip> clip) {
        segments.set(i, clip);
        mergedClipDirty = true;
    }

    public void removeSegment(int i) {
        segments.remove(i);
        mergedClipDirty = true;
    }

    public List<AssetAccessor<AnimationClip>> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /// Per-segment [from, to] window (seconds) along the merged timeline. Same offset
    /// math as [#bakeMergedClip] ([AnimationMontage#SEGMENT_GAP] between clips). Empty if no segments.
    /// The Segments row uses this to place blocks; the Sections row snaps to these edges.
    public List<TimeWindow> getSegmentBounds() {
        List<TimeWindow> out = new ArrayList<>(segments.size());
        float time = 0.0F;
        for (AssetAccessor<AnimationClip> seg : segments) {
            float dur = seg.get().getTotalTime();
            out.add(TimeWindow.create(time, time + dur));
            time += dur + AnimationMontage.SEGMENT_GAP;
        }

        return out;
    }

    /// Display name for a segment block (clip id, asset directory prefix stripped).
    public String getSegmentLabel(int i) {
        return segments.get(i).identifier().toString().replace(AnimationManager.DIRECTORY, "");
    }

    public int segmentCount() {
        return segments.size();
    }

    /// The editor clips this montage references, so a clip rename re-saves this montage's JSON.
    /// Only editor-owned accessors are [EditorReference]s (engine empty / foreign are not), so
    /// they're filtered out. The accessors are shared, so they follow renames in memory for free;
    /// this only drives the on-disk re-save.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return segments.stream()
            .filter(c -> c instanceof EditorReference)
            .map(c -> (EditorReference) c);
    }

    /// ***************************************************************
    /// Merged preview clip — holder bakes & owns it (hot-reload)
    /// ***************************************************************

    /// Editor-side merged clip, rebaked only when a segment edit marked it dirty.
    public AnimationClip getMergedClip() {
        if (mergedClipDirty || cachedMergedClip == null) {
            cachedMergedClip = bakeMergedClip();
            mergedClipDirty = false;
        }

        return cachedMergedClip;
    }

    /// Copied from `AnimationMontage#buildMergedClip` (private there), retargeted at
    /// `this.segments`. Keep in sync if the engine merge logic ever changes.
    private AnimationClip bakeMergedClip() {
        AnimationClip.Builder animClipBuilder = AnimationClip.builder();
        float time = 0.0F;
        // Running root offset so each segment's root track continues where the previous ended.
        Vec3 rootAccum = Vec3.ZERO;
        Map<String, List<Keyframe>> bones = new HashMap<>();

        // Union of every bone across all segments. A bone missing from a segment is pinned to identity
        // across that segment's window so a neighbour's keyframes can't bleed into it.
        Set<String> allBones = new HashSet<>();

        for (AssetAccessor<AnimationClip> animation : segments) {
            animation.get().visitBoneTracks((name, track) -> allBones.add(name));
        }

        for (AssetAccessor<AnimationClip> animation : segments) {
            final AnimationClip clip = animation.get();
            final float segmentOffset = time;
            final float segEnd = clip.getTotalTime();

            final Vec3 segRootStart = rootMotion ? rootLocOf(clip, 0.0F) : Vec3.ZERO;
            final Vec3 rootShift = rootMotion ? rootAccum.subtract(segRootStart) : Vec3.ZERO;

            final Pose firstPose = clip.getPose(0.0F);
            final Pose lastPose = clip.getPose(segEnd);

            for (String name : allBones) {
                List<Keyframe> merged = bones.computeIfAbsent(name, k -> new ArrayList<>());

                if (!clip.hasBoneTransform(name)) {
                    // Not animated in this segment → hold identity across its window.
                    merged.add(new Keyframe(segmentOffset, Transform.IDENTITY));
                    merged.add(new Keyframe(segEnd + segmentOffset, Transform.IDENTITY));
                    continue;
                }

                // First pose
                merged.add(new Keyframe(segmentOffset, shiftRoot(name, firstPose.orElseIdentity(name), rootShift)));

                // Own track keyframes
                for (Keyframe kf : clip.getBoneTransform(name).keyframes()) {
                    merged.add(new Keyframe(kf.timeStamp() + segmentOffset, shiftRoot(name, kf.transform(), rootShift)));
                }

                // Last pose, unless the final keyframe already sits at the segment end
                if (Math.abs(merged.getLast().timeStamp() - (segEnd + segmentOffset)) > AnimationMontage.LAST_FRAME_EPSILON) {
                    merged.add(new Keyframe(segEnd + segmentOffset, shiftRoot(name, lastPose.orElseIdentity(name), rootShift)));
                }
            }

            if (rootMotion) {
                rootAccum = rootAccum.add(rootLocOf(clip, segEnd).subtract(segRootStart));
            }

            time += segEnd + AnimationMontage.SEGMENT_GAP;
        }

        bones.forEach((name, keyframes) -> animClipBuilder.addBoneAnimation(name, new BoneAnimationTrack(keyframes)));

        return animClipBuilder.build();
    }

    /// Raw root-bone location at `t` (clip pose keeps the root, unlike `AnimationMontage.getPose`).
    private static Vec3 rootLocOf(AnimationClip clip, float t) {
        return clip.getPose(Math.clamp(t, 0.0F, clip.getTotalTime()))
            .orElseZero(ArmatureLoader.ROOT_BONE).loc();
    }

    /// Returns `transform` with `rootShift` added to its location — but only for the root bone.
    /// Other bones pass through unchanged.
    private static Transform shiftRoot(String boneName, Transform transform, Vec3 rootShift) {
        if (!ArmatureLoader.ROOT_BONE.equals(boneName)) return transform;
        return new Transform(transform.loc().add(rootShift), transform.rot(), transform.sca());
    }

    /// ***************************************************************
    /// Accessors
    /// ***************************************************************
    public NotifyTrackHolder getNotifyTrackHolder() {
        return notifyTrackHolder;
    }

    /// Live, mutable. The Montage Sections tab edits this directly (step 4).
    public Map<String, Float> getSections() {
        return sections;
    }

    /// Live, mutable. The Montage Sections tab edits these sequences directly.
    public List<List<String>> getPlayingOrders() {
        return playingOrders;
    }

    /// Insert a section at a time (seconds); auto-name "Section{n}" avoiding dupes. Returns the name.
    public String addSectionAt(float startSeconds) {
        int n = sections.size() + 1;
        String name = "Section" + n;
        while (sections.containsKey(name)) name = "Section" + (++n);
        sections.put(name, startSeconds);

        return name;
    }

    /// Drop a section and scrub it out of every sequence so the chains stay valid.
    /// Sequences emptied by the removal are dropped too.
    public void removeSection(String name) {
        sections.remove(name);
        for (List<String> seq : playingOrders) seq.removeIf(name::equals);
        playingOrders.removeIf(List::isEmpty);
    }

    public void setSectionTime(String name, float startSeconds) {
        if (sections.containsKey(name)) sections.put(name, startSeconds);
    }

    /// Rename a section, keeping its start time and its slot in the playing order.
    /// No-op (returns false) if the new name is blank, unchanged, missing, or already taken.
    public boolean renameSection(String oldName, String newName) {
        if (newName == null || newName.isBlank() || oldName.equals(newName)) return false;
        if (!sections.containsKey(oldName) || sections.containsKey(newName)) return false;

        // Rebuild in place to swap the one key while preserving insertion order.
        LinkedHashMap<String, Float> reordered = new LinkedHashMap<>();
        sections.forEach((k, v) -> reordered.put(k.equals(oldName) ? newName : k, v));
        sections.clear();
        sections.putAll(reordered);

        for (List<String> seq : playingOrders) {
            for (int i = 0; i < seq.size(); i++) {
                if (seq.get(i).equals(oldName)) seq.set(i, newName);
            }
        }

        return true;
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

    public void setFadeOutTime(float v) {
        fadeOutTime = v;
    }

    @Override
    public float getTotalFrameTime() {
        return getMergedClip().getTotalTime();
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
        // The merged clip bakes root accumulation only when root motion is on, so a flip must rebake.
        mergedClipDirty = true;
        if (rootMotionChangeListener != null) rootMotionChangeListener.accept(v);
    }

    public void setRootMotionChangeListener(@Nullable Consumer<Boolean> listener) {
        rootMotionChangeListener = listener;
    }

    public AnimationLayerSignature layerSignature() {
        return layerSignature;
    }

    public void setLayerSignature(AnimationLayerSignature layerSignature) {
        this.layerSignature = layerSignature;
    }

    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }
}