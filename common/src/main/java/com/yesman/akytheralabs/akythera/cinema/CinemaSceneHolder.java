package com.yesman.akytheralabs.akythera.cinema;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Modifiable data holder for a [CinemaScene]: its actors and how long it keeps them placed.
public final class CinemaSceneHolder extends AbstractDataHolder<CinemaScene> implements DTOProvider {
    /// The shortest scene, in seconds.
    public static final float MIN_DURATION = 0.05F;

    private static final float DEFAULT_DURATION = 1.0F;
    private static final String NEW_ACTOR_ROLE = "actor";

    public static final Codec<CinemaSceneHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                CinemaActorHolder.CODEC.listOf().optionalFieldOf("actors", List.of()).forGetter(holder -> holder.actors),
                Codec.FLOAT.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(holder -> holder.duration)
            ).apply(instance, (actors, duration) -> {
                CinemaSceneHolder holder = new CinemaSceneHolder();

                holder.actors.addAll(actors);
                holder.setDuration(duration);

                return holder;
            })
    );

    /// In the order the cinema track lists them.
    private final List<CinemaActorHolder> actors = new ArrayList<>();

    /// How long the scene keeps its actors placed, in seconds.
    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_DURATION, min = MIN_DURATION, order = 0)
    private float duration = DEFAULT_DURATION;

    @Override
    public void readFrom(final CinemaScene object) {
        actors.clear();

        object.actors().forEach((role, actor) -> {
            CinemaActorHolder holder = new CinemaActorHolder(role);

            holder.readFrom(actor);
            actors.add(holder);
        });

        setDuration(object.duration());
    }

    /// @throws IllegalStateException when two actors share a role
    @Override
    public CinemaScene makeObject() {
        Map<String, CinemaScene.Actor> built = new LinkedHashMap<>();

        for (CinemaActorHolder actor : actors) {
            if (built.put(actor.getRole(), actor.makeObject()) != null) {
                throw new IllegalStateException("Two actors share the role '" + actor.getRole() + "'");
            }
        }

        return new CinemaScene(built, duration);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return actors.stream().flatMap(DataHolder::referencedReferences);
    }

    /// `duration`, and `actors`: each a `role`, its `transform` tokens ([TransformHolder#sourceTokens]), its `montage`
    /// id when it has one, its `ignore_block_collision` and `ignore_entity_collision`, its `force_third_person` and
    /// `camera_follows_actor`, and its `camera_keys`, each a `time` and its `transform` tokens.
    @Override
    public Map<String, Object> getDTO() {
        List<Map<String, Object>> actorRows = new ArrayList<>();

        for (CinemaActorHolder actor : actors) {
            Map<String, Object> row = new HashMap<>();

            row.put("role", actor.getRole());
            row.put("transform", actor.getTransform().sourceTokens());
            row.put("ignore_block_collision", actor.isIgnoreBlockCollision());
            row.put("ignore_entity_collision", actor.isIgnoreEntityCollision());
            row.put("force_third_person", actor.isForceThirdPerson());
            row.put("camera_follows_actor", actor.isCameraFollowsActor());
            row.put("camera_keys", actor.getCameraKeys().keys().stream()
                .map(key -> Map.of("time", key.time(), "transform", TransformHolder.of(key.value()).sourceTokens()))
                .toList());

            if (actor.hasMontage()) row.put("montage", EditorHolderCodecs.entryId(actor.getMontage()).toString());

            actorRows.add(row);
        }

        return Map.of("duration", duration, "actors", actorRows);
    }

    public List<CinemaActorHolder> getActors() {
        return actors;
    }

    /// A new actor with a role no other actor has.
    public CinemaActorHolder newActor() {
        Set<String> taken = actors.stream().map(CinemaActorHolder::getRole).collect(Collectors.toSet());
        String role = NEW_ACTOR_ROLE;

        for (int suffix = 2; taken.contains(role); suffix++) {
            role = NEW_ACTOR_ROLE + "_" + suffix;
        }

        return new CinemaActorHolder(role);
    }

    public float getDuration() {
        return duration;
    }

    /// In seconds, at least [#MIN_DURATION].
    public void setDuration(final float duration) {
        this.duration = Math.max(duration, MIN_DURATION);
    }

    /// When the longest montage among the actors ends, in seconds since the scene started. Zero with no montage.
    public float getLength() {
        return (float)actors.stream()
            .map(actor -> CinemaMontagePreview.of(actor.getMontage()))
            .mapToDouble(preview -> preview == null ? 0.0D : preview.length())
            .max()
            .orElse(0.0D);
    }
}
