package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/// Editor-side reference from a BlendSpace2D node to a `PlayableAnimation` entry — an anim sequence,
/// montage or blendspace2d, editor-created (a project `ProjectObjectHolder`) or built-in (a
/// `RegistryAccess` holder) — or `Empty`. It holds a live [Holder], so playback reads the entry's
/// last-saved object.
///
/// Serialization mirrors the engine `BlendSpace2D.AnimationNode` animation shape:
/// `{ "type": "sequence", "sequence": "myproject:walk" }` etc.
public sealed interface AnimationRef {
    Empty EMPTY = new Empty();

    /// The entry's live [Holder] for playback. Empty and unbound targets read as
    /// [PlayableAnimation#EMPTY_ANIM_HOLDER].
    Holder<? extends @NonNull PlayableAnimation> toEngineHolder();

    /// Display string for the editor — current id or "—" for empty.
    String displayName();

    record Empty() implements AnimationRef {
        @Override
        public Holder<? extends @NonNull PlayableAnimation> toEngineHolder() {
            return PlayableAnimation.EMPTY_ANIM_HOLDER;
        }

        @Override
        public String displayName() { return "—"; }
    }

    record Sequence(Holder<AnimationSequence> holder) implements AnimationRef {
        @Override
        public Holder<? extends @NonNull PlayableAnimation> toEngineHolder() { return safe(holder); }

        @Override
        public String displayName() { return displayNameOf(holder); }
    }

    record Montage(Holder<AnimationMontage> holder) implements AnimationRef {
        @Override
        public Holder<? extends @NonNull PlayableAnimation> toEngineHolder() { return safe(holder); }

        @Override
        public String displayName() { return displayNameOf(holder); }
    }

    record Blend(Holder<BlendSpace2D> holder) implements AnimationRef {
        @Override
        public Holder<? extends @NonNull PlayableAnimation> toEngineHolder() { return safe(holder); }

        @Override
        public String displayName() { return displayNameOf(holder); }
    }

    private static Holder<? extends @NonNull PlayableAnimation> safe(Holder<? extends @NonNull PlayableAnimation> h) {
        return h.isBound() ? h : PlayableAnimation.EMPTY_ANIM_HOLDER;
    }

    private static String displayNameOf(Holder<?> h) {
        return h.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    /// Codec mirroring the engine `BlendSpace2D.AnimationNode` animation shape. Encodes the holder's key id; decodes
    /// it back to a [Holder] via project ∪ `RegistryAccess`. `Empty` is `{ "type": "empty" }`.
    Codec<AnimationRef> CODEC = Codec.STRING.dispatch("type", AnimationRef::tagOf, AnimationRef::codecForTag);

    private static String tagOf(AnimationRef ref) {
        return switch (ref) {
            case Empty ignored    -> "empty";
            case Sequence ignored -> "sequence";
            case Montage ignored  -> "montage";
            case Blend ignored    -> "blend";
        };
    }

    private static MapCodec<? extends AnimationRef> codecForTag(String tag) {
        return switch (tag) {
            case "empty"    -> EMPTY_CODEC;
            case "sequence" -> SEQUENCE_CODEC;
            case "montage"  -> MONTAGE_CODEC;
            case "blend"    -> BLEND_CODEC;
            default -> throw new IllegalArgumentException("Unknown AnimationRef type: " + tag);
        };
    }

    MapCodec<Empty> EMPTY_CODEC = MapCodec.unit(EMPTY);

    MapCodec<Sequence> SEQUENCE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Identifier.CODEC.fieldOf("sequence").forGetter(ref -> idOf(ref.holder()))
    ).apply(i, id -> new Sequence(resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, id))));

    MapCodec<Montage> MONTAGE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Identifier.CODEC.fieldOf("montage").forGetter(ref -> idOf(ref.holder()))
    ).apply(i, id -> new Montage(resolve(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, id))));

    MapCodec<Blend> BLEND_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Identifier.CODEC.fieldOf("blend").forGetter(ref -> idOf(ref.holder()))
    ).apply(i, id -> new Blend(resolve(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, id))));

    private static Identifier idOf(Holder<?> h) {
        return h.unwrapKey().orElseThrow().identifier();
    }

    /// An id as a [Holder], looked up across the project and `RegistryAccess`. A missing id resolves
    /// to an unbound project placeholder.
    private static <T> Holder<T> resolve(ResourceKey<? extends Registry<T>> registryKey, Identifier id) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Holder<T> h = project.getEntryFromProjectAndRegistry(registryKey, id);
        return h != null
            ? h
            : project.<T, DataHolder<T>>getModifiableRegistry(registryKey).projectHolder(ResourceKey.create(registryKey, id));
    }
}
