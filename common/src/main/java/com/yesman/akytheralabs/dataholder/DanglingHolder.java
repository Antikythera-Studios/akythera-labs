package com.yesman.akytheralabs.dataholder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// A permanently-unbound [Holder] carrying only a [ResourceKey] — the decode result for a
/// reference whose id resolves nowhere, such as an unknown data block after a namespace move or a
/// removed engine value. It encodes back through its key, so the original id string round-trips on
/// save, and the editor marks referrers through `!isBound()`.
///
/// It stays unbound for its whole life, never picking up a later-created entry the way a
/// [ProjectObjectHolder] does. Used where no [ProjectObjectRegistry] exists to mint a shared
/// project holder.
///
/// Extends [Holder.Reference] rather than implementing [Holder], which is sealed to its own two
/// implementations. `Reference` is the one left `non-sealed`; every method that would read the
/// state it holds for a real registry entry is overridden below.
public final class DanglingHolder<T> extends Holder.Reference<T> {
    private final ResourceKey<T> key;

    public DanglingHolder(final ResourceKey<T> key) {
        super(Type.STAND_ALONE, EditorHolderOwner.get(), key, null);

        this.key = key;
    }

    @Override
    public ResourceKey<T> key() {
        return key;
    }

    @Override
    public T value() {
        throw new IllegalStateException("Dangling reference: " + key.identifier());
    }

    @Override
    public boolean isBound() {
        return false;
    }

    @Override
    public boolean is(final Identifier id) {
        return key.identifier().equals(id);
    }

    @Override
    public boolean is(final ResourceKey<T> resourceKey) {
        return key.equals(resourceKey);
    }

    @Override
    public boolean is(final Predicate<ResourceKey<T>> predicate) {
        return predicate.test(key);
    }

    @Override
    public boolean is(final TagKey<T> tagKey) {
        return false;
    }

    @Override
    public boolean is(final Holder<T> holder) {
        return holder.unwrapKey().map(key::equals).orElse(false);
    }

    @Override
    public Stream<TagKey<T>> tags() {
        return Stream.empty();
    }

    @Override
    public Either<ResourceKey<T>, T> unwrap() {
        return Either.left(key);
    }

    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
        return Optional.of(key);
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(final HolderOwner<T> holderOwner) {
        return true;
    }

    @Override
    public DataComponentMap components() {
        return DataComponentMap.EMPTY;
    }

    @Override
    public boolean areComponentsBound() {
        return true;
    }

    @Override
    public boolean equals(final @Nullable Object obj) {
        if (this == obj) return true;

        return obj instanceof DanglingHolder<?> other && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "DanglingHolder[" + key.identifier() + "]";
    }
}
