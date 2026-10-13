package com.yesman.akytheralabs.akythera.particle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.core.blueprint.particle.InstantSpawnRule;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpawnRateRule;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/// Modifiable data holder for one [ParticleEmitterBlueprint] of a particle system. The fields are the emitter's
/// Details rows, its modules included, and its keyframes are edited on the emitter track.
///
/// A channel with keys plays its keys alone: its transform field is then a scratch value the editor writes the keyed
/// transform at the playhead into ([#moveToTime]), and an edit to it shows until the playhead moves or a key takes it
/// ([#insertKey]).
public final class ParticleEmitterHolder extends AbstractDataHolder<ParticleEmitterBlueprint> {
    /// The shortest time span of a [SpawnType#SPAWN_RATE] emitter, in seconds, as [SpawnRateRule] allows.
    public static final float MIN_DURATION = 0.05F;

    /// Two keys closer than this, in seconds, are at the same time.
    private static final float KEY_EPSILON = 1.0E-4F;

    private static final float DEFAULT_RATE = 10.0F;
    private static final float DEFAULT_DURATION = 1.0F;

    public static final Codec<ParticleEmitterHolder> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter(holder -> holder.name),
                ResourceKey.codec(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY).optionalFieldOf("particle")
                    .forGetter(holder -> holder.particle instanceof ParticleRef.Piece piece ? Optional.of(piece.key()) : Optional.empty()),
                Identifier.CODEC.optionalFieldOf("vanilla_particle")
                    .forGetter(holder -> holder.particle instanceof ParticleRef.Vanilla vanilla ? Optional.of(vanilla.id()) : Optional.empty()),
                Codec.FLOAT.optionalFieldOf("start", 0.0F).forGetter(holder -> holder.start),
                SpawnType.CODEC.optionalFieldOf("spawn_type", SpawnType.INSTANT).forGetter(holder -> holder.spawnType),
                Codec.INT.optionalFieldOf("count", 1).forGetter(holder -> holder.count),
                Codec.FLOAT.optionalFieldOf("rate", DEFAULT_RATE).forGetter(holder -> holder.rate),
                Codec.FLOAT.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(holder -> holder.duration),
                Codec.BOOL.optionalFieldOf("loop", false).forGetter(holder -> holder.loop),
                Codec.BOOL.optionalFieldOf("hide_particles_on_finish", false).forGetter(holder -> holder.hideParticlesOnFinish),
                TransformHolder.CODEC.optionalFieldOf("pivot").forGetter(holder -> Optional.of(holder.pivot)),
                TransformHolder.CODEC.optionalFieldOf("transform").forGetter(holder -> Optional.of(holder.transform)),
                KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("pivot_keys", KeyframeTrack.empty()).forGetter(holder -> holder.pivotKeys),
                KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("transform_keys", KeyframeTrack.empty()).forGetter(holder -> holder.transformKeys),
                ParticleModuleHolder.Stored.CODEC.listOf().optionalFieldOf("initializers", List.of())
                    .forGetter(holder -> holder.initializers.modules().stream().map(ParticleModuleHolder::toStored).toList()),
                ParticleModuleHolder.Stored.CODEC.listOf().optionalFieldOf("updaters", List.of())
                    .forGetter(holder -> holder.updaters.modules().stream().map(ParticleModuleHolder::toStored).toList())
            ).apply(instance, (name, particle, vanillaParticle, start, spawnType, count, rate, duration, loop, hide, pivot, transform, pivotKeys, transformKeys, initializers, updaters) -> {
                ParticleEmitterHolder holder = new ParticleEmitterHolder(name);
                holder.particle = particle.filter(key -> !key.identifier().equals(DataHolder.EMPTY_REFERENCE)).<ParticleRef>map(ParticleRef.Piece::new)
                    .or(() -> vanillaParticle.map(ParticleRef.Vanilla::new))
                    .orElse(ParticleRef.NONE);
                holder.start = start;
                holder.spawnType = spawnType;
                holder.count = count;
                holder.rate = rate;
                holder.duration = duration;
                holder.loop = loop;
                holder.hideParticlesOnFinish = hide;
                pivot.ifPresent(value -> holder.pivot = value);
                transform.ifPresent(value -> holder.transform = value);
                holder.pivotKeys = pivotKeys;
                holder.transformKeys = transformKeys;
                initializers.forEach(stored -> holder.initializers.modules().add(ParticleModuleHolder.of(stored, false)));
                updaters.forEach(stored -> holder.updaters.modules().add(ParticleModuleHolder.of(stored, true)));

                return holder;
            })
    );

    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_NAME, order = 0)
    private String name;

    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_PARTICLE, order = 1, rebuildOnWrite = true)
    private ParticleRef particle = ParticleRef.NONE;

    /// In seconds since the system started. Edited on the emitter track only.
    private float start;

    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_SPAWN_TYPE, order = 3, rebuildOnWrite = true)
    private SpawnType spawnType = SpawnType.INSTANT;

    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_COUNT, order = 4, min = 1, visibleWhen = "isInstant")
    private int count = 1;

    /// Particles per second.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_RATE, order = 5, min = 0, visibleWhen = "isSpawnRate")
    private float rate = DEFAULT_RATE;

    /// The time span, in seconds. Edited on the emitter track only.
    private float duration = DEFAULT_DURATION;

    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_LOOP, order = 7, visibleWhen = "isSpawnRate")
    private boolean loop;

    /// An [SpawnType#INSTANT] emitter finishes on the tick after it spawns, so the row is hidden for it.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_HIDE_PARTICLES_ON_FINISH, order = 8, visibleWhen = "isSpawnRate")
    private boolean hideParticlesOnFinish;

    /// The space [#transform] is in, in the system's space.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_PIVOT, order = 9)
    private TransformHolder pivot = TransformHolder.identity();

    /// The emitter's transform in the pivot's space.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_TRANSFORM, order = 10)
    private TransformHolder transform = TransformHolder.identity();

    private KeyframeTrack<Transform> pivotKeys = KeyframeTrack.empty();
    private KeyframeTrack<Transform> transformKeys = KeyframeTrack.empty();

    /// Run once on each particle right after it spawns.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_INITIALIZERS, order = 11, rebuildOnWrite = true)
    private final ParticleModuleList initializers = new ParticleModuleList(false);

    /// Run on each particle every tick.
    @EditorEditable(label = LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_UPDATERS, order = 12, rebuildOnWrite = true)
    private final ParticleModuleList updaters = new ParticleModuleList(true);

    public ParticleEmitterHolder(final String name) {
        this.name = name;
    }

    // Whether a row shows, see [EditorEditable#visibleWhen]

    boolean isInstant() {
        return spawnType == SpawnType.INSTANT;
    }

    boolean isSpawnRate() {
        return spawnType == SpawnType.SPAWN_RATE;
    }

    @Override
    public void readFrom(final ParticleEmitterBlueprint object) {
        name = object.name();
        particle = object.particle().map(ParticleRef.Piece::new, ParticleRef.Vanilla::new);
        start = object.start();

        switch (object.spawn()) {
            case InstantSpawnRule instant -> {
                spawnType = SpawnType.INSTANT;
                count = instant.count();
            }
            case SpawnRateRule spawnRate -> {
                spawnType = SpawnType.SPAWN_RATE;
                rate = spawnRate.rate();
                duration = spawnRate.duration();
                loop = spawnRate.loop();
            }
            default -> throw new IllegalArgumentException("The editor can't edit the spawn rule " + object.spawn().getClass().getSimpleName());
        }

        hideParticlesOnFinish = object.hideParticlesOnFinish();
        pivot = TransformHolder.of(object.pivot());
        transform = TransformHolder.of(object.transform());
        pivotKeys = object.pivotKeys();
        transformKeys = object.transformKeys();
        initializers.modules().clear();
        updaters.modules().clear();
        object.initializers().forEach(initializer -> initializers.modules().add(ParticleModuleHolder.of(initializer, false)));
        object.updaters().forEach(updater -> updaters.modules().add(ParticleModuleHolder.of(updater, true)));
    }

    /// Leaves out keys past the time span, every key of an instant emitter, and module rows with no module picked. A
    /// keyed channel's rest transform is its first key's.
    ///
    /// @throws IllegalStateException when the emitter names no particle, or a module can't be built
    @Override
    public ParticleEmitterBlueprint makeObject() {
        if (!hasParticle()) throw new IllegalStateException("Emitter " + name + " names no particle");

        KeyframeTrack<Transform> playedPivotKeys = playedKeys(pivotKeys);
        KeyframeTrack<Transform> playedTransformKeys = playedKeys(transformKeys);

        return new ParticleEmitterBlueprint(
            name,
            switch (particle) {
                case ParticleRef.Piece piece -> Either.left(piece.key());
                case ParticleRef.Vanilla vanilla -> Either.right(vanilla.id());
                case ParticleRef.None _ -> throw new IllegalStateException("Emitter " + name + " names no particle");
            },
            start,
            spawnType == SpawnType.INSTANT ? new InstantSpawnRule(count) : new SpawnRateRule(rate, duration, loop),
            spawnType == SpawnType.SPAWN_RATE && hideParticlesOnFinish,
            playedPivotKeys.isEmpty() ? pivot.makeObject() : playedPivotKeys.keys().getFirst().value(),
            playedTransformKeys.isEmpty() ? transform.makeObject() : playedTransformKeys.keys().getFirst().value(),
            playedPivotKeys,
            playedTransformKeys,
            initializers.modules().stream().flatMap(module -> module.makeInitializer().stream()).toList(),
            updaters.modules().stream().flatMap(module -> module.makeUpdater().stream()).toList()
        );
    }

    /// The keys of `keys` the emitter plays: those inside the time span, and none for an instant emitter.
    private KeyframeTrack<Transform> playedKeys(final KeyframeTrack<Transform> keys) {
        return isSpawnRate() ? new KeyframeTrack<>(keys.keys().stream().filter(key -> key.time() <= duration).toList()) : KeyframeTrack.empty();
    }

    /// Whether the emitter names a particle piece or a vanilla particle.
    public boolean hasParticle() {
        return particle != ParticleRef.NONE;
    }

    /// The space [#getTransform] is in, in the system's space.
    public TransformHolder getPivot() {
        return pivot;
    }

    /// The emitter's transform in the pivot's space.
    public TransformHolder getTransform() {
        return transform;
    }

    public ParticleRef getParticle() {
        return particle;
    }

    /// Names `particle`, and removes the module rows that do not apply to it.
    public void setParticle(final ParticleRef particle) {
        Class<? extends AkytheraParticle> particleClass = particle.particleClass();

        this.particle = particle;
        initializers.modules().removeIf(module -> !module.appliesTo(particleClass));
        updaters.modules().removeIf(module -> !module.appliesTo(particleClass));
    }

    /// The initializer rows, then the updater rows.
    public Stream<ParticleModuleHolder> modules() {
        return Stream.concat(initializers.modules().stream(), updaters.modules().stream());
    }

    public ParticleChoice getParticleChoice() {
        return new ParticleChoice(particle, List.copyOf(initializers.modules()), List.copyOf(updaters.modules()));
    }

    public void setParticleChoice(final ParticleChoice choice) {
        particle = choice.particle();
        initializers.modules().clear();
        initializers.modules().addAll(choice.initializers());
        updaters.modules().clear();
        updaters.modules().addAll(choice.updaters());
    }

    public String getName() {
        return name;
    }

    /// In seconds since the system started.
    public float getStart() {
        return start;
    }

    /// In seconds since the system started. A negative start becomes zero.
    public void setStart(final float start) {
        this.start = Math.max(start, 0.0F);
    }

    public SpawnType getSpawnType() {
        return spawnType;
    }

    /// The time span in seconds: [#duration] for a [SpawnType#SPAWN_RATE] emitter, zero for an instant one.
    public float getSpan() {
        return spawnType == SpawnType.SPAWN_RATE ? duration : 0.0F;
    }

    /// The time span a [SpawnType#SPAWN_RATE] emitter has, in seconds, kept while the emitter is instant.
    public float getDuration() {
        return duration;
    }

    /// In seconds, at least [#MIN_DURATION] and never before the last key. Only a [SpawnType#SPAWN_RATE] emitter has
    /// one.
    public void setDuration(final float duration) {
        this.duration = Math.max(duration, Math.max(MIN_DURATION, lastKeyTime()));
    }

    /// Moves the start to `start` seconds while the time span's end and every key stay where they are on the time
    /// axis. The start stays within zero, the first key, and [#MIN_DURATION] before the end.
    public void setStartKeepingEnd(final float start) {
        float end = this.start + duration;
        float clamped = Math.clamp(start, 0.0F, Math.min(end - MIN_DURATION, this.start + firstKeyTime()));

        shiftKeys(this.start - clamped);
        duration = end - clamped;
        this.start = clamped;
    }

    public KeyframeTrack<Transform> getKeys(final KeyChannel channel) {
        return channel == KeyChannel.PIVOT ? pivotKeys : transformKeys;
    }

    /// The transform `channel` keys.
    public TransformHolder getKeyedTransform(final KeyChannel channel) {
        return channel == KeyChannel.PIVOT ? pivot : transform;
    }

    /// Keys `channel` at `seconds` since the system started with the channel's transform field, see
    /// [#insertKey(KeyChannel, float, Transform)].
    public void insertKey(final KeyChannel channel, final float seconds) {
        insertKey(channel, seconds, getKeyedTransform(channel).makeObject());
    }

    /// Keys `channel` at `seconds` since the system started with `value`, replacing a key at the same time. A time
    /// outside the time span grows the span to it. An instant emitter takes no keys.
    public void insertKey(final KeyChannel channel, final float seconds, final Transform value) {
        if (!isSpawnRate()) return;

        if (seconds < start) {
            float grown = start - seconds;

            shiftKeys(grown);
            duration += grown;
            start = seconds;
        }

        float time = seconds - start;

        putKey(channel, time, value);
        duration = Math.max(duration, time);
    }

    /// Keys `channel` at `time` seconds since the time span started with `value`, replacing a key at the same time.
    public void putKey(final KeyChannel channel, final float time, final Transform value) {
        List<KeyframeTrack.Key<Transform>> keys = new ArrayList<>(getKeys(channel).keys());

        keys.removeIf(key -> Math.abs(key.time() - time) < KEY_EPSILON);
        keys.add(new KeyframeTrack.Key<>(time, value));
        setKeys(channel, new KeyframeTrack<>(keys));
    }

    /// Removes `channel`'s key at `time` seconds since the time span started, if there is one.
    public void removeKey(final KeyChannel channel, final float time) {
        setKeys(channel, new KeyframeTrack<>(getKeys(channel).keys().stream().filter(key -> Math.abs(key.time() - time) >= KEY_EPSILON).toList()));
    }

    /// Writes each keyed channel's transform at `seconds` since the system started into its transform field.
    public void moveToTime(final float seconds) {
        if (!isSpawnRate()) return;

        float spanTime = new SpawnRateRule(rate, duration, loop).spanTime(Math.max(seconds - start, 0.0F));

        for (KeyChannel channel : KeyChannel.values()) {
            KeyframeTrack<Transform> keys = getKeys(channel);

            if (!keys.isEmpty()) getKeyedTransform(channel).readFrom(keys.sample(spanTime, Transform.IDENTITY, Transform::slerp));
        }
    }

    public Timeline getTimeline() {
        return new Timeline(start, duration, pivotKeys, transformKeys);
    }

    public void setTimeline(final Timeline timeline) {
        start = timeline.start();
        duration = timeline.duration();
        pivotKeys = timeline.pivotKeys();
        transformKeys = timeline.transformKeys();
    }

    private void setKeys(final KeyChannel channel, final KeyframeTrack<Transform> keys) {
        if (channel == KeyChannel.PIVOT) {
            pivotKeys = keys;
        } else {
            transformKeys = keys;
        }
    }

    /// Moves every key `seconds` later in the time span.
    private void shiftKeys(final float seconds) {
        for (KeyChannel channel : KeyChannel.values()) {
            setKeys(channel, new KeyframeTrack<>(getKeys(channel).keys().stream().map(key -> new KeyframeTrack.Key<>(key.time() + seconds, key.value())).toList()));
        }
    }

    /// The earliest key's time in the time span, or the span's length with no keys.
    private float firstKeyTime() {
        return (float)Stream.of(pivotKeys, transformKeys).flatMap(keys -> keys.keys().stream()).mapToDouble(KeyframeTrack.Key::time).min().orElse(duration);
    }

    /// The latest key's time in the time span, or zero with no keys.
    private float lastKeyTime() {
        return (float)Stream.of(pivotKeys, transformKeys).flatMap(keys -> keys.keys().stream()).mapToDouble(KeyframeTrack.Key::time).max().orElse(0.0D);
    }

    public boolean loops() {
        return spawnType == SpawnType.SPAWN_RATE && loop;
    }

    /// Which of an emitter's transforms a key animates.
    public enum KeyChannel {
        PIVOT, TRANSFORM
    }

    /// An emitter's particle and the module rows it keeps, as one undo value.
    public record ParticleChoice(ParticleRef particle, List<ParticleModuleHolder> initializers, List<ParticleModuleHolder> updaters) {
    }

    /// An emitter's place on the time axis and its keys, as one undo value.
    ///
    /// @param start    in seconds since the system started
    /// @param duration the time span, in seconds
    public record Timeline(float start, float duration, KeyframeTrack<Transform> pivotKeys, KeyframeTrack<Transform> transformKeys) {
    }

    /// How an emitter spawns, see [InstantSpawnRule] and [SpawnRateRule].
    public enum SpawnType implements StringRepresentable {
        INSTANT, SPAWN_RATE;

        public static final Codec<SpawnType> CODEC = StringRepresentable.fromEnum(SpawnType::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
