package com.yesman.akytheralabs.dataholder.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolderMapper;
import com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Mutable, editor-only model for the Notify Track section of an animation.
///
/// On disk the engine's [AnimationNotifyTrack] stores everything in two flat
/// lists keyed by timestamp / time window. The editor groups them into named
/// rows ("Track1", "Track2", ...) for ergonomic editing. The track name is
/// purely an editor concept — [#toEngineTrack()] folds every entry across all
/// rows back into the flat shape that the engine codec round-trips.
public final class NotifyTrackHolder implements DTOProvider {
    public static final Codec<NotifyTrackHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Track.CODEC.listOf().optionalFieldOf("tracks", List.of())
                .forGetter(h -> h.tracks)
        ).apply(instance, list -> {
            NotifyTrackHolder h = new NotifyTrackHolder();
            h.tracks.addAll(list);
            return h;
        })
    );

    private final List<Track> tracks = new ArrayList<>();

    /// Construct from an engine track. Loses the row partition (engine doesn't
    /// know about row names), so everything lands on a single default row.
    public void readFromEngineTrack(final AnimationNotifyTrack engineTrack) {
        tracks.clear();
        Track defaultTrack = new Track("Track1");

        for (var n : engineTrack.serializeNotifies()) {
            defaultTrack.notifies.add(new MutableNotify(holderFor(n.animNotify()), n.timeStamp()));
        }

        for (var ns : engineTrack.serializeNotifyStates()) {
            defaultTrack.notifyStates.add(new MutableNotifyState(
                holderFor(ns.animNotifyState()),
                ns.timeWindow().from(),
                ns.timeWindow().to()
            ));
        }

        tracks.add(defaultTrack);
    }

    /// Fold every row's contents into a single engine track. Track names are
    /// dropped — the engine stores no per-row identifier.
    public AnimationNotifyTrack toEngineTrack() {
        List<AnimationNotifyTrack.NotifyInTimeStamp> outNotifies = new ArrayList<>();
        List<AnimationNotifyTrack.NotifyStateInTimeWindow> outStates = new ArrayList<>();

        for (Track t : tracks) {
            for (MutableNotify n : t.notifies) {
                outNotifies.add(new AnimationNotifyTrack.NotifyInTimeStamp(n.holder.makeObject(), n.time));
            }
            for (MutableNotifyState ns : t.notifyStates) {
                outStates.add(new AnimationNotifyTrack.NotifyStateInTimeWindow(
                    ns.holder.makeObject(), TimeWindow.create(ns.from, ns.to)
                ));
            }
        }

        return new AnimationNotifyTrack(outNotifies, outStates);
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public Track addTrack() {
        String base = "Track";
        int i = tracks.size() + 1;
        while (containsTrackNamed(base + i)) i++;
        Track t = new Track(base + i);
        tracks.add(t);
        return t;
    }

    public void removeTrack(Track t) {
        tracks.remove(t);
    }

    private boolean containsTrackNamed(String name) {
        for (Track t : tracks) {
            if (t.name.equals(name)) return true;
        }

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AnimationNotifyHolder<?> holderFor(AnimationNotify<?> n) {
        Supplier<AnimationNotifyHolder<?>> factory = DataHolderMapper.notifyHolderFor(n.mapCodec());
        if (factory == null) {
            throw new IllegalStateException(
                "No DataHolderMapper entry for notify " + n.getClass().getName()
                + " — register it in AkytheraLabs.bootstrapDataHolderMappings()"
            );
        }
        AnimationNotifyHolder h = factory.get();
        h.readFrom(n);
        return h;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AnimationNotifyStateHolder<?> holderFor(AnimationNotifyState<?> n) {
        Supplier<AnimationNotifyStateHolder<?>> factory = DataHolderMapper.notifyStateHolderFor(n.mapCodec());
        if (factory == null) {
            throw new IllegalStateException(
                "No DataHolderMapper entry for notify-state " + n.getClass().getName()
                + " — register it in AkytheraLabs.bootstrapDataHolderMappings()"
            );
        }
        AnimationNotifyStateHolder h = factory.get();
        h.readFrom(n);
        return h;
    }

    /// Renders the whole notify track to a single Java source string under the `notify_track` key,
    /// ready to be spliced into the AnimationSequence constructor by the sequence template. Each
    /// notify / notify-state delegates to its own per-type fragment (`notify/<type>.ftl` etc.) via
    /// [com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder#toSourceCode]; this
    /// holder only stitches the timestamps / time windows and the surrounding
    /// `new AnimationNotifyTrack(List.of(...), List.of(...))` (or `AnimationNotifyTrack.EMPTY`).
    @Override
    public Map<String, Object> getDTO() {
        List<String> notifyExprs = new ArrayList<>();
        List<String> stateExprs = new ArrayList<>();

        for (Track t : tracks) {
            for (MutableNotify n : t.notifies) {
                notifyExprs.add(
                    "new AnimationNotifyTrack.NotifyInTimeStamp("
                        + n.holder.toSourceCode() + ", " + SourceTemplateEngine.floatLiteral(n.time) + ")"
                );
            }

            for (MutableNotifyState ns : t.notifyStates) {
                stateExprs.add(
                    "new AnimationNotifyTrack.NotifyStateInTimeWindow("
                        + ns.holder.toSourceCode() + ", TimeWindow.create("
                        + SourceTemplateEngine.floatLiteral(ns.from) + ", " + SourceTemplateEngine.floatLiteral(ns.to) + "))"
                );
            }
        }

        return Map.of("notify_track", buildTrackSource(notifyExprs, stateExprs));
    }

    /// Assemble the `new AnimationNotifyTrack(...)` expression (or the EMPTY singleton). Indentation is
    /// baked in so the multi-line string drops cleanly under the `${dto.notify_track}` token, which sits
    /// 16 columns deep inside the generated `new AnimationSequence(` argument list.
    private static String buildTrackSource(List<String> notifyExprs, List<String> stateExprs) {
        if (notifyExprs.isEmpty() && stateExprs.isEmpty()) {
            return "AnimationNotifyTrack.EMPTY";
        }

        String indent16 = " ".repeat(16);
        String indent20 = " ".repeat(20);
        String indent24 = " ".repeat(24);

        return "new AnimationNotifyTrack(\n"
            + indent20 + listOf(notifyExprs, indent20, indent24) + ",\n"
            + indent20 + listOf(stateExprs, indent20, indent24) + "\n"
            + indent16 + ")";
    }

    /// Render `List.of(entry, entry, ...)` multi-line, or `List.of()` when empty. `closeIndent` is the
    /// column of the `List.of(` opener (its closing paren aligns there); `entryIndent` indents each entry.
    private static String listOf(List<String> exprs, String closeIndent, String entryIndent) {
        if (exprs.isEmpty()) {
            return "List.of()";
        }

        StringBuilder sb = new StringBuilder("List.of(\n");

        for (int i = 0; i < exprs.size(); i++) {
            sb.append(entryIndent).append(exprs.get(i));
            sb.append(i < exprs.size() - 1 ? ",\n" : "\n");
        }

        return sb.append(closeIndent).append(")").toString();
    }

    public static final class Track {
        public static final Codec<Track> CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                Codec.STRING.fieldOf("name").forGetter(t -> t.name),
                MutableNotify.CODEC.listOf().optionalFieldOf("notifies", List.of()).forGetter(t -> t.notifies),
                MutableNotifyState.CODEC.listOf().optionalFieldOf("notify_states", List.of()).forGetter(t -> t.notifyStates)
            ).apply(inst, (n, ns, nss) -> {
                Track t = new Track(n);
                t.notifies.addAll(ns);
                t.notifyStates.addAll(nss);
                return t;
            })
        );

        public String name;
        public final List<MutableNotify> notifies = new ArrayList<>();
        public final List<MutableNotifyState> notifyStates = new ArrayList<>();

        public Track(String name) {
            this.name = name;
        }

        public boolean isEmpty() {
            return notifies.isEmpty() && notifyStates.isEmpty();
        }
    }

    public static final class MutableNotify {
        /// Round-trips through engine `AnimationNotify` so the JSON shape on
        /// disk stays identical to the old codec (registry-name dispatch).
        /// In memory we keep the holder — that's what the editor mutates.
        public static final Codec<MutableNotify> CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                AnimationNotify.CODEC.fieldOf("notify").forGetter(m -> m.holder.makeObject()),
                Codec.FLOAT.fieldOf("time").forGetter(m -> m.time)
            ).apply(inst, (engineInstance, time) -> new MutableNotify(holderFor(engineInstance), time))
        );

        public AnimationNotifyHolder<?> holder;

        @EditorEditable(label = "Time", min = 0F, order = 0)
        public float time;

        public MutableNotify(AnimationNotifyHolder<?> holder, float time) {
            this.holder = holder;
            this.time = time;
        }
    }

    public static final class MutableNotifyState {
        public static final Codec<MutableNotifyState> CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                AnimationNotifyState.CODEC.fieldOf("notify_state").forGetter(m -> m.holder.makeObject()),
                Codec.FLOAT.fieldOf("from").forGetter(m -> m.from),
                Codec.FLOAT.fieldOf("to").forGetter(m -> m.to)
            ).apply(inst, (engineInstance, from, to) -> new MutableNotifyState(holderFor(engineInstance), from, to))
        );

        public AnimationNotifyStateHolder<?> holder;

        @EditorEditable(label = "Begin", min = 0F, order = 0)
        public float from;

        @EditorEditable(label = "End", min = 0F, order = 1)
        public float to;

        public MutableNotifyState(AnimationNotifyStateHolder<?> holder, float from, float to) {
            this.holder = holder;
            this.from = from;
            this.to = to;
        }
    }

    /// Convenience: a marker for "currently selected" used by the editor —
    /// kept off the concrete field types so widgets and holder don't share
    /// identity assumptions.
    public sealed interface NotifyRef permits NotifyRef.Point, NotifyRef.State {
        Track track();
        record Point(Track track, MutableNotify mutableNotify) implements NotifyRef {}
        record State(Track track, MutableNotifyState mutableNotifyState) implements NotifyRef {}
    }

}
