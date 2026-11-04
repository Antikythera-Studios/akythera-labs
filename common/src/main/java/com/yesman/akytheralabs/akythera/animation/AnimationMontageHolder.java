package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationLayerSignature;
import com.yesman.akythera.core.animation.ik.AkytheraIKType;
import com.yesman.akythera.core.animation.ik.IKType;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Modifiable data holder for [AnimationMontage].
///
/// The merged preview clip is baked and owned here — see [#getMergedClip].
public final class AnimationMontageHolder extends AbstractDataHolder<AnimationMontage>
    implements PlayableAnimationHolder<AnimationMontage>, DTOProvider
{
    public static final Codec<AnimationMontageHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            EditorAssetAccessors.ANIMATION_CODEC.listOf().optionalFieldOf("animations", List.of())
                .forGetter(h -> h.segments),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("sections", Map.of())
                .forGetter(h -> h.sections),
            Codec.STRING.listOf().listOf().optionalFieldOf("playing_orders", List.of())
                .forGetter(h -> h.playingOrders),
            Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationMontageHolder::getFadeInTime),
            Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationMontageHolder::getFadeOutTime),
            Codec.BOOL.fieldOf("loops").forGetter(AnimationMontageHolder::loops),
            Codec.BOOL.fieldOf("root_motion").forGetter(h -> h.rootMotion),
            Codec.BOOL.optionalFieldOf("move_vertical", true).forGetter(AnimationMontageHolder::moveVertical),
            AnimationLayerSignature.ENUM_MANAGER.getNameCodec()
                .optionalFieldOf("layer_signature", AkytheraAnimationLayerSignature.BASE)
                .forGetter(AnimationMontageHolder::layerSignature),
            Codec.FLOAT.optionalFieldOf("play_rate", 1.0F).forGetter(AnimationMontageHolder::playRate),
            IKType.ENUM_MANAGER.getNameCodec().optionalFieldOf("ik_type", AkytheraIKType.NONE).forGetter(AnimationMontageHolder::getIKType),
            Identifier.CODEC.optionalFieldOf("preview_entity_type")
                .forGetter(ParseUtils.provideOptional(AnimationMontageHolder::getPreviewEntityType)),
            PreviewItem.CODEC.listOf().optionalFieldOf("preview_items", List.of())
                .forGetter(AnimationMontageHolder::getPreviewItems),
            NotifyTrackHolder.EDITOR_CODEC.optionalFieldOf("notify_tracks", new NotifyTrackHolder())
                .forGetter(AnimationMontageHolder::getNotifyTrackHolder)
        ).apply(instance, (segs, secs, orders, fin, fout, lps, rm, mv, sig, rate, ik, pm, items, nth) -> {
            AnimationMontageHolder h = new AnimationMontageHolder();
            segs.forEach(h::addSegment);
            h.sections.putAll(secs);
            orders.forEach(seq -> h.playingOrders.add(new ArrayList<>(seq)));
            h.fadeInTime = fin;
            h.fadeOutTime = fout;
            h.loops = lps;
            h.rootMotion = rm;
            h.moveVertical = mv;
            h.layerSignature = sig;
            h.playRate = rate;
            h.ikType = ik;
            pm.ifPresent(h::setPreviewEntityType);
            h.setPreviewItems(items);
            if (!nth.getTracks().isEmpty()) h.notifyTrackHolder = nth;
            return h;
        })
    );

    /// The montage's clips in playback order.
    private final List<AssetAccessor<AnimationClip>> segments = new ArrayList<>();

    /// Section name -> its start time, in seconds along the merged timeline.
    private final Map<String, Float> sections = new LinkedHashMap<>();

    /// Section playing orders — many sequences, each an ordered chain of section names.
    private final List<List<String>> playingOrders = new ArrayList<>();

    private NotifyTrackHolder notifyTrackHolder = new NotifyTrackHolder();

    /// The merged preview clip, rebaked by [#getMergedClip] when a segment edit flips
    /// [#mergedClipDirty].
    @Nullable
    private AnimationClip cachedMergedClip;
    private boolean mergedClipDirty = true;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_FADE_IN, min = 0F, order = 0)
    private float fadeInTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_FADE_OUT, min = 0F, order = 1)
    private float fadeOutTime;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_LOOPS, order = 2)
    private boolean loops;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_ROOT_MOTION, order = 3, rebuildOnWrite = true)
    private boolean rootMotion;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_MOVE_VERTICAL, order = 4, indent = 1, visibleWhen = "isRootMotion")
    private boolean moveVertical = true;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_LAYER_SIGNATURE, order = 5)
    private AnimationLayerSignature layerSignature = AkytheraAnimationLayerSignature.BASE;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_PLAY_RATE, min = 0.01F, order = 6)
    private float playRate = 1.0F;

    @EditorEditable(label = LangKeys.GUI_ANIM_MONTAGE_SCREEN_WIDGET_IK_TYPE, order = 7)
    private IKType ikType = AkytheraIKType.NONE;

    @Nullable
    private Identifier previewEntityType;

    /// The items the preview holds, one per slot. Editor-only.
    private List<PreviewItem> previewItems = List.of();

    /// Notified with the new value whenever [#rootMotion] flips.
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
        moveVertical = object.moveVertical();
        layerSignature = object.layerSignature();
        playRate = object.playRate();
        ikType = object.ikType();
        // TODO: SectionInfo exposes no public enumeration, so sections and playingOrders cannot be
        // recovered from a runtime montage.
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
            .loops(loops).rootMotionEnabled(rootMotion).moveVertical(moveVertical)
            .layerSignature(layerSignature)
            .playRate(playRate)
            .ikType(ikType)
            .create();
    }

    /// The tokens `animation_montage_template.ftl` renders into an [AnimationMontage.Builder] chain:
    /// the segment ids, the notify and notify-state rows (each `expr` from
    /// [AnimationNotifyHolder#toSourceCode]), the sections and playing orders, the fade times and
    /// flags as Java literals, `layer_signature` when it differs from [AkytheraAnimationLayerSignature#BASE], and
    /// `ik_type` when it differs from [AkytheraIKType#NONE].
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
        dto.put("move_vertical", Boolean.toString(moveVertical));
        dto.put("play_rate", SourceTemplateEngine.floatLiteral(playRate));

        // The builder defaults to BASE. Every registered signature is an enum constant, so a
        // differing one renders as `<SimpleName>.<CONSTANT>`.
        if (layerSignature != AkytheraAnimationLayerSignature.BASE && layerSignature instanceof Enum<?> e) {
            dto.put("layer_signature", layerSignature.getClass().getSimpleName() + "." + e.name());
        }

        if (ikType != AkytheraIKType.NONE) dto.put("ik_type", ScriptNodeSourceCompiler.dataBlockLookup("IKType", ikType));

        return dto;
    }

    // ***************************************************************
    // Segments — list ops flip the clip dirty flag
    // ***************************************************************
    public void addSegment(AssetAccessor<AnimationClip> clip) {
        insertSegment(segments.size(), clip);
    }

    /// Inserts a clip at `index`.
    public void insertSegment(int index, AssetAccessor<AnimationClip> clip) {
        segments.add(index, clip);
        mergedClipDirty = true;
    }

    /// Moves the segment at `from` to `to`. No-op when either index is out of range.
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

    /// Per-segment `[from, to]` window in seconds along the merged timeline, with an
    /// [AnimationMontage#SEGMENT_GAP] between clips as in [AnimationMontage#mergeSegments].
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

    /// The editor-owned clips this montage references, and what its notifies reference. Engine and
    /// foreign accessors are not [EditorReference]s and are filtered out.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            segments.stream()
                .filter(c -> c instanceof EditorReference)
                .map(c -> (EditorReference) c),
            notifyTrackHolder.referencedReferences()
        );
    }

    // ***************************************************************
    // Merged preview clip
    // ***************************************************************

    /// The merged clip, rebaked when a segment edit marked it dirty.
    public AnimationClip getMergedClip() {
        if (mergedClipDirty || cachedMergedClip == null) {
            cachedMergedClip = AnimationMontage.mergeSegments(segments.stream().<AnimationClip>map(AssetAccessor::get).toList(), rootMotion);
            mergedClipDirty = false;
        }

        return cachedMergedClip;
    }

    // ***************************************************************
    // Accessors
    // ***************************************************************
    public NotifyTrackHolder getNotifyTrackHolder() {
        return notifyTrackHolder;
    }

    /// The first broken notify row the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return notifyTrackHolder.firstFailure();
    }

    /// The live section map, edited in place.
    public Map<String, Float> getSections() {
        return sections;
    }

    /// The live playing-order sequences, edited in place.
    public List<List<String>> getPlayingOrders() {
        return playingOrders;
    }

    /// Inserts a section at `startSeconds` under the first free `Section{n}` name, and returns it.
    public String addSectionAt(float startSeconds) {
        int n = sections.size() + 1;
        String name = "Section" + n;
        while (sections.containsKey(name)) name = "Section" + (++n);
        sections.put(name, startSeconds);

        return name;
    }

    /// Drops a section and scrubs it out of every playing order. Sequences the removal empties are
    /// dropped too.
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
        // The merged clip bakes root accumulation only when root motion is on.
        mergedClipDirty = true;
        if (rootMotionChangeListener != null) rootMotionChangeListener.accept(v);
    }

    public boolean moveVertical() {
        return moveVertical;
    }

    public void setMoveVertical(boolean v) {
        moveVertical = v;
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

    public float playRate() {
        return playRate;
    }

    public void setPlayRate(float v) {
        playRate = v;
    }

    public IKType getIKType() {
        return ikType;
    }

    public void setIKType(final IKType ikType) {
        this.ikType = ikType;
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
}