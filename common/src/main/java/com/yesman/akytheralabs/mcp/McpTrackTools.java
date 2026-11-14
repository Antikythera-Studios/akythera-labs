package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaSceneHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.editor.StableIndexes;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import com.yesman.akytheralabs.gui.widget.cinema.CinemaTrackPanel;
import com.yesman.akytheralabs.gui.widget.particle.EmitterTrackPanel;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/// The [McpTool]s that read and edit the track panel of the top editor screen: the notify tracks of an animation
/// sequence or montage, the emitter tracks of a particle system, and the actor and camera tracks of a cinema scene.
final class McpTrackTools {
    private McpTrackTools() {}

    private static final List<TrackKind<?>> KINDS = List.of(new NotifyTracks(), new EmitterTracks(), new CinemaTracks());

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_tracks",
            "Returns the tracks of the top editor screen as JSON, with 'kind': 'notify' (animation sequence or montage: "
                + "'length' in seconds and the notify rows, each with its point notifies and notify states), 'emitter' "
                + "(particle system: each emitter's name, time span and pivot and transform keys) or 'cinema' (cinema "
                + "scene: the scene's editor JSON, actors and their camera keys included, and 'length', the longest "
                + "montage). Times are seconds. Rows, notifies, notify states and emitters carry an 'index' that "
                + "edit_tracks addresses them by; an index stays the same while the editor is open, removals included, "
                + "and is given again from 0 on save. Actors are addressed by role.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> McpTools.onClient(() -> McpToolResult.text(McpTools.PRETTY_GSON.toJson(find().read())))
        ));

        McpTools.register(McpTool.of(
            "edit_tracks",
            "Edits the tracks of the top editor screen as one undoable step; a failing op undoes the ops before it. "
                + "Rows, notifies, notify states and emitters are addressed by the 'index' get_tracks shows. Ops for "
                + "'notify': {op:'add_track'}, {op:'remove_track', track}, {op:'rename_track', track, name}, "
                + "{op:'add_notify', track, notify:<notify JSON with 'type'>, time}, {op:'add_notify_state', track, "
                + "notify_state:<JSON with 'type'>, from, to}, {op:'set_notify', index, notify?, time?}, "
                + "{op:'set_notify_state', index, notify_state?, from?, to?}, {op:'remove_notify', index}, "
                + "{op:'remove_notify_state', index}. Ops for 'emitter': {op:'add_emitter'}, "
                + "{op:'remove_emitter', emitter}, {op:'set_timeline', emitter, start?, duration?}, {op:'put_key', "
                + "emitter, channel:'pivot'|'transform', time, value:<transform>}, {op:'remove_key', emitter, channel, "
                + "time}; key times are seconds since the emitter's time span started. Ops for 'cinema': "
                + "{op:'set_duration', duration}, {op:'add_actor'}, {op:'remove_actor', actor:<role>}, {op:'set_actor', "
                + "actor, fields:<actor editor JSON fields>}, {op:'put_camera_key', actor, time, value:<transform>}, "
                + "{op:'remove_camera_key', actor, time}. A transform is {location:[x,y,z], rotation:[x,y,z,w], "
                + "sca:[x,y,z]}, as get_tracks shows.",
            """
            {
              "type": "object",
              "properties": {
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["ops"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Found<?> found = find();
                HistoryManager history = HistoryManager.getInstance();
                List<WorkHistory> steps = new ArrayList<>();
                List<String> report = new ArrayList<>();

                try {
                    for (JsonElement element : arguments.getAsJsonArray("ops")) {
                        steps.addAll(history.capture(() -> {
                            String line = found.apply(element.getAsJsonObject());

                            if (line != null) report.add(line);
                        }));
                    }
                } catch (RuntimeException e) {
                    history.capture(() -> steps.reversed().forEach(WorkHistory::undo));
                    throw e;
                }

                if (!steps.isEmpty()) {
                    history.pushHistory(new WorkHistory() {
                        @Override
                        public void undo() {
                            steps.reversed().forEach(WorkHistory::undo);
                        }

                        @Override
                        public void redo() {
                            steps.forEach(WorkHistory::redo);
                        }
                    });
                }

                return McpToolResult.text(report.isEmpty() ? "Done" : String.join("\n", report));
            })
        ));
    }

    /// The track panel of the top editor screen, with the kind that edits it.
    private static Found<?> find() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        for (TrackKind<?> kind : screen == null ? List.<TrackKind<?>>of() : KINDS) {
            List<?> panels = McpTools.findWidgets(screen, kind.panelType());

            if (!panels.isEmpty()) return Found.of(kind, panels.getFirst());
        }

        throw new IllegalStateException("The top editor screen has no track: open an animation sequence or montage, a particle system or a cinema scene");
    }

    /// The element of `indexes` at the index in `op`'s `key` member.
    ///
    /// @throws IllegalArgumentException naming `what` when no element still sits at the index
    private static <T> T element(final StableIndexes<T> indexes, final JsonObject op, final String key, final String what) {
        int index = op.get(key).getAsInt();
        T element = indexes.get(index);

        if (element == null) throw new IllegalArgumentException("No " + what + " at index " + index + ": removed, or not listed by get_tracks");

        return element;
    }

    /// `holder`, decoded from the op's notify or notify state JSON.
    ///
    /// @throws IllegalArgumentException when the JSON failed to decode into a working holder
    private static <H extends DataHolder<?>> H decoded(final H holder) {
        Exception failure = holder.getDeserializationFailureException();

        if (failure != null) throw new IllegalArgumentException("Invalid notify JSON: " + failure.getMessage(), failure);

        return holder;
    }

    /// `value` encoded by `codec`, with its stable `index`.
    private static <T> JsonObject indexed(final Codec<T> codec, final T value, final int index) {
        JsonObject json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();

        json.addProperty("index", index);

        return json;
    }

    /// `base` with the members of `changes` named `keys` written over it.
    private static JsonObject merge(final JsonObject base, final JsonObject changes, final String... keys) {
        JsonObject merged = base.deepCopy();

        for (String key : keys) {
            if (changes.has(key)) merged.add(key, changes.get(key));
        }

        return merged;
    }

    private static Transform transform(final JsonObject op) {
        return Transform.CODEC.parse(JsonOps.INSTANCE, op.get("value")).getOrThrow(IllegalArgumentException::new);
    }

    /// One kind of track panel, and the ops `edit_tracks` runs on it.
    private interface TrackKind<P> {
        Class<P> panelType();

        /// The panel's tracks as JSON, `kind` included.
        JsonObject read(P panel);

        /// Runs `op` on `panel`, pushing the history entries the user's edit would. Answers a line for the reply, or
        /// null.
        @Nullable String apply(P panel, JsonObject op);
    }

    private record Found<P>(TrackKind<P> kind, P panel) {
        static <P> Found<P> of(final TrackKind<P> kind, final Object panel) {
            return new Found<>(kind, kind.panelType().cast(panel));
        }

        JsonObject read() {
            return kind.read(panel);
        }

        @Nullable String apply(final JsonObject op) {
            return kind.apply(panel, op);
        }
    }

    // ***************************************************************
    // Notify tracks
    // ***************************************************************

    private static final class NotifyTracks implements TrackKind<AnimationTrackEditorPanel> {
        @Override
        public Class<AnimationTrackEditorPanel> panelType() {
            return AnimationTrackEditorPanel.class;
        }

        @Override
        public JsonObject read(final AnimationTrackEditorPanel panel) {
            JsonObject json = new JsonObject();
            JsonArray tracks = new JsonArray();

            for (NotifyTrackHolder.Track track : panel.holder().getTracks()) {
                JsonObject row = new JsonObject();
                JsonArray notifies = new JsonArray();
                JsonArray states = new JsonArray();

                track.notifies.forEach(notify -> notifies.add(indexed(NotifyTrackHolder.MutableNotify.CODEC, notify, panel.notifyIndexes().indexOf(notify))));
                track.notifyStates.forEach(state -> states.add(indexed(NotifyTrackHolder.MutableNotifyState.CODEC, state, panel.notifyStateIndexes().indexOf(state))));
                row.addProperty("index", panel.trackIndexes().indexOf(track));
                row.addProperty("name", track.name);
                row.add("notifies", notifies);
                row.add("notify_states", states);
                tracks.add(row);
            }

            json.addProperty("kind", "notify");
            json.addProperty("length", panel.playableAnimationHolder().getTotalFrameTime());
            json.add("tracks", tracks);

            return json;
        }

        @Override
        public @Nullable String apply(final AnimationTrackEditorPanel panel, final JsonObject op) {
            List<NotifyTrackHolder.Track> tracks = panel.holder().getTracks();
            HistoryManager history = HistoryManager.getInstance();
            String name = op.get("op").getAsString();

            switch (name) {
                case "add_track" -> {
                    NotifyTrackHolder.Track added = panel.holder().addTrack();

                    history.pushHistory(new ListInsert<>(tracks, tracks.size() - 1, added, panel::rebuildEntries));
                    panel.rebuildEntries();

                    return "added track " + panel.trackIndexes().indexOf(added) + " '" + added.name + "'";
                }
                case "remove_track" -> {
                    NotifyTrackHolder.Track removed = element(panel.trackIndexes(), op, "track", "track");
                    int index = tracks.indexOf(removed);

                    tracks.remove(index);
                    history.pushHistory(new ListRemove<>(tracks, index, removed, panel::rebuildEntries));
                    panel.setSelected(null);
                    panel.rebuildEntries();
                }
                case "rename_track" -> {
                    NotifyTrackHolder.Track track = element(panel.trackIndexes(), op, "track", "track");
                    String renamed = op.get("name").getAsString();
                    Consumer<String> rename = value -> {
                        track.name = value;
                        panel.rebuildEntries();
                    };

                    history.pushHistory(new DataChange<>(track.name, renamed, rename));
                    rename.accept(renamed);
                }
                case "add_notify" -> {
                    NotifyTrackHolder.Track track = element(panel.trackIndexes(), op, "track", "track");
                    NotifyTrackHolder.MutableNotify added = NotifyTrackHolder.MutableNotify.CODEC.parse(JsonOps.INSTANCE, op)
                        .getOrThrow(IllegalArgumentException::new);

                    decoded(added.holder);
                    track.notifies.add(added);
                    history.pushHistory(new ListInsert<>(track.notifies, track.notifies.size() - 1, added, panel::rebuildEntries));
                    panel.rebuildEntries();

                    return "added notify " + panel.notifyIndexes().indexOf(added) + " on track " + panel.trackIndexes().indexOf(track);
                }
                case "add_notify_state" -> {
                    NotifyTrackHolder.Track track = element(panel.trackIndexes(), op, "track", "track");
                    NotifyTrackHolder.MutableNotifyState added = NotifyTrackHolder.MutableNotifyState.CODEC.parse(JsonOps.INSTANCE, op)
                        .getOrThrow(IllegalArgumentException::new);

                    decoded(added.holder);
                    track.notifyStates.add(added);
                    history.pushHistory(new ListInsert<>(track.notifyStates, track.notifyStates.size() - 1, added, panel::rebuildEntries));
                    panel.rebuildEntries();

                    return "added notify state " + panel.notifyStateIndexes().indexOf(added) + " on track " + panel.trackIndexes().indexOf(track);
                }
                case "set_notify" -> {
                    NotifyTrackHolder.MutableNotify notify = element(panel.notifyIndexes(), op, "index", "notify");
                    JsonObject json = NotifyTrackHolder.MutableNotify.CODEC.encodeStart(JsonOps.INSTANCE, notify).getOrThrow().getAsJsonObject();
                    NotifyTrackHolder.MutableNotify parsed = NotifyTrackHolder.MutableNotify.CODEC.parse(JsonOps.INSTANCE, merge(json, op, "notify", "time"))
                        .getOrThrow(IllegalArgumentException::new);
                    NotifyTrackHolder.MutableNotify edited = new NotifyTrackHolder.MutableNotify(op.has("notify") ? decoded(parsed.holder) : notify.holder, parsed.time);
                    Consumer<NotifyTrackHolder.MutableNotify> set = value -> {
                        notify.holder = value.holder;
                        notify.time = value.time;
                        panel.rebuildEntries();
                        panel.refreshSelectionView();
                    };

                    history.pushHistory(new DataChange<>(new NotifyTrackHolder.MutableNotify(notify.holder, notify.time), edited, set));
                    set.accept(edited);
                }
                case "set_notify_state" -> {
                    NotifyTrackHolder.MutableNotifyState state = element(panel.notifyStateIndexes(), op, "index", "notify state");
                    JsonObject json = NotifyTrackHolder.MutableNotifyState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
                    NotifyTrackHolder.MutableNotifyState parsed = NotifyTrackHolder.MutableNotifyState.CODEC
                        .parse(JsonOps.INSTANCE, merge(json, op, "notify_state", "from", "to"))
                        .getOrThrow(IllegalArgumentException::new);
                    NotifyTrackHolder.MutableNotifyState edited = new NotifyTrackHolder.MutableNotifyState(
                        op.has("notify_state") ? decoded(parsed.holder) : state.holder, parsed.from, parsed.to
                    );
                    Consumer<NotifyTrackHolder.MutableNotifyState> set = value -> {
                        state.holder = value.holder;
                        state.from = value.from;
                        state.to = value.to;
                        panel.rebuildEntries();
                        panel.refreshSelectionView();
                    };

                    history.pushHistory(new DataChange<>(new NotifyTrackHolder.MutableNotifyState(state.holder, state.from, state.to), edited, set));
                    set.accept(edited);
                }
                case "remove_notify" -> {
                    NotifyTrackHolder.MutableNotify removed = element(panel.notifyIndexes(), op, "index", "notify");
                    List<NotifyTrackHolder.MutableNotify> notifies = tracks.stream().filter(track -> track.notifies.contains(removed)).findFirst().orElseThrow().notifies;
                    int index = notifies.indexOf(removed);

                    notifies.remove(index);
                    history.pushHistory(new ListRemove<>(notifies, index, removed, panel::rebuildEntries));
                    panel.setSelected(null);
                    panel.rebuildEntries();
                }
                case "remove_notify_state" -> {
                    NotifyTrackHolder.MutableNotifyState removed = element(panel.notifyStateIndexes(), op, "index", "notify state");
                    List<NotifyTrackHolder.MutableNotifyState> states = tracks.stream().filter(track -> track.notifyStates.contains(removed)).findFirst().orElseThrow().notifyStates;
                    int index = states.indexOf(removed);

                    states.remove(index);
                    history.pushHistory(new ListRemove<>(states, index, removed, panel::rebuildEntries));
                    panel.setSelected(null);
                    panel.rebuildEntries();
                }
                default -> throw new IllegalArgumentException("Unknown notify op " + name);
            }

            return null;
        }
    }

    // ***************************************************************
    // Emitter tracks
    // ***************************************************************

    private static final class EmitterTracks implements TrackKind<EmitterTrackPanel> {
        @Override
        public Class<EmitterTrackPanel> panelType() {
            return EmitterTrackPanel.class;
        }

        @Override
        public JsonObject read(final EmitterTrackPanel panel) {
            JsonObject json = new JsonObject();
            JsonArray emitters = new JsonArray();

            for (ParticleEmitterHolder emitter : panel.holder().getEmitters()) {
                JsonObject row = new JsonObject();

                row.addProperty("index", panel.emitterIndexes().indexOf(emitter));
                row.addProperty("name", emitter.getName());
                row.addProperty("has_time_span", emitter.hasTimeSpan());
                row.addProperty("start", emitter.getStart());
                row.addProperty("duration", emitter.getDuration());

                for (ParticleEmitterHolder.KeyChannel channel : ParticleEmitterHolder.KeyChannel.values()) {
                    row.add(channel.name().toLowerCase(Locale.ROOT) + "_keys", KeyframeTrack.codec(Transform.CODEC).encodeStart(JsonOps.INSTANCE, emitter.getKeys(channel)).getOrThrow());
                }

                emitters.add(row);
            }

            json.addProperty("kind", "emitter");
            json.add("emitters", emitters);

            return json;
        }

        @Override
        public @Nullable String apply(final EmitterTrackPanel panel, final JsonObject op) {
            List<ParticleEmitterHolder> emitters = panel.holder().getEmitters();
            String name = op.get("op").getAsString();

            if (name.equals("add_emitter")) {
                panel.addEmitter();

                return "added emitter " + panel.emitterIndexes().indexOf(emitters.getLast()) + " '" + emitters.getLast().getName() + "'";
            }

            ParticleEmitterHolder emitter = element(panel.emitterIndexes(), op, "emitter", "emitter");
            Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before = new IdentityHashMap<>();

            before.put(emitter, emitter.getTimeline());

            switch (name) {
                case "remove_emitter" -> {
                    panel.deleteEmitter(emitter);
                    return null;
                }
                case "set_timeline" -> {
                    if (op.has("start")) emitter.setStart(op.get("start").getAsFloat());
                    if (op.has("duration")) emitter.setDuration(op.get("duration").getAsFloat());
                }
                case "put_key", "remove_key" -> {
                    ParticleEmitterHolder.KeyChannel channel = ParticleEmitterHolder.KeyChannel.valueOf(op.get("channel").getAsString().toUpperCase(Locale.ROOT));
                    float time = op.get("time").getAsFloat();

                    if (!emitter.hasTimeSpan()) throw new IllegalArgumentException("Emitter '" + emitter.getName() + "' is instant and takes no keys");

                    if (name.equals("put_key")) {
                        emitter.insertKey(channel, emitter.getStart() + time, transform(op));
                    } else {
                        emitter.removeKey(channel, time);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown emitter op " + name);
            }

            panel.pushTimelineChange(before);

            return null;
        }
    }

    // ***************************************************************
    // Cinema tracks
    // ***************************************************************

    private static final class CinemaTracks implements TrackKind<CinemaTrackPanel> {
        @Override
        public Class<CinemaTrackPanel> panelType() {
            return CinemaTrackPanel.class;
        }

        @Override
        public JsonObject read(final CinemaTrackPanel panel) {
            JsonObject json = CinemaSceneHolder.EDITOR_CODEC.encodeStart(JsonOps.INSTANCE, panel.holder()).getOrThrow().getAsJsonObject();

            json.addProperty("kind", "cinema");
            json.addProperty("length", panel.holder().getLength());

            return json;
        }

        @Override
        public @Nullable String apply(final CinemaTrackPanel panel, final JsonObject op) {
            CinemaSceneHolder holder = panel.holder();
            String name = op.get("op").getAsString();

            switch (name) {
                case "set_duration" -> {
                    float duration = op.get("duration").getAsFloat();
                    Consumer<Float> set = value -> {
                        holder.setDuration(value);
                        panel.refreshSelectionView();
                    };

                    HistoryManager.getInstance().pushHistory(new DataChange<>(holder.getDuration(), duration, set));
                    set.accept(duration);
                    return null;
                }
                case "add_actor" -> {
                    panel.addActor();
                    return "added actor '" + holder.getActors().getLast().getRole() + "'";
                }
                default -> {
                }
            }

            String role = op.get("actor").getAsString();
            CinemaActorHolder actor = holder.getActors().stream()
                .filter(candidate -> candidate.getRole().equals(role))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No actor '" + role + "'"));
            Map<CinemaActorHolder, KeyframeTrack<Transform>> before = new IdentityHashMap<>();

            before.put(actor, actor.getCameraKeys());

            switch (name) {
                case "remove_actor" -> panel.deleteActor(actor);
                case "set_actor" -> {
                    int index = holder.getActors().indexOf(actor);
                    JsonObject json = CinemaActorHolder.CODEC.encodeStart(JsonOps.INSTANCE, actor).getOrThrow().getAsJsonObject();
                    JsonObject fields = op.getAsJsonObject("fields");
                    CinemaActorHolder edited = CinemaActorHolder.CODEC.parse(JsonOps.INSTANCE, merge(json, fields, fields.keySet().toArray(String[]::new)))
                        .getOrThrow(IllegalArgumentException::new);
                    Consumer<CinemaActorHolder> set = value -> {
                        boolean selected = panel.getSelected() == holder.getActors().get(index);

                        holder.getActors().set(index, value);
                        panel.rebuildEntries();

                        if (selected) panel.setSelected(value);
                    };

                    if (holder.getActors().stream().anyMatch(other -> other != actor && other.getRole().equals(edited.getRole()))) {
                        throw new IllegalArgumentException("Another actor has the role '" + edited.getRole() + "'");
                    }

                    HistoryManager.getInstance().pushHistory(new DataChange<>(actor, edited, set));
                    set.accept(edited);
                }
                case "put_camera_key" -> {
                    actor.insertCameraKey(op.get("time").getAsFloat(), transform(op));
                    panel.pushKeysChange(before);
                }
                case "remove_camera_key" -> {
                    actor.removeCameraKey(op.get("time").getAsFloat());
                    panel.pushKeysChange(before);
                }
                default -> throw new IllegalArgumentException("Unknown cinema op " + name);
            }

            return null;
        }
    }
}
