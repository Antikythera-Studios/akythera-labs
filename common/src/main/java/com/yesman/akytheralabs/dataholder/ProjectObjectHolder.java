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

/// A [Holder] over a [ProjectObjectRegistry] entry. `value()` reads the registry's *live* cached
/// object by key, so it never goes stale: a property edit replaces the object in the registry
/// (see [ProjectObjectRegistry#refreshObject]) and a rename mutates this holder's [#key] in place
/// (see [ProjectObjectRegistry#rekey]). Every referrer shares the one cached instance per key
/// ([ProjectObjectRegistry#projectHolder]), so a rename is a single key mutation here — no
/// per-reference rewrite.
/// Extends [Holder.Reference] rather than implementing [Holder], which is sealed to its own two
/// implementations. `Reference` is the one left `non-sealed`; it keeps the key it was built with in
/// a field of its own that refuses to be rebound, so [#key] shadows it and every method that would
/// read it is overridden below.
public final class ProjectObjectHolder<T> extends Holder.Reference<T> implements EditorReference {
    private final ProjectObjectRegistry<T, ?> registry;
    private ResourceKey<T> key;

    ProjectObjectHolder(ProjectObjectRegistry<T, ?> registry, ResourceKey<T> key) {
        super(Type.STAND_ALONE, EditorHolderOwner.get(), key, null);

        this.registry = registry;
        this.key = key;
    }

    @Override
    public ResourceKey<T> key() {
        return key;
    }

    /// Called only by [ProjectObjectRegistry#rekey] — "renames" this shared holder.
    void setKey(ResourceKey<T> newKey) {
        this.key = newKey;
    }

    @Override
    public T value() {
        T v = registry.getValue(key);
        if (v == null) {
            throw new IllegalStateException("Unbound project object holder: " + key.identifier());
        }
        return v;
    }

    @Override
    public boolean isBound() {
        return registry.getValue(key) != null;
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
        return obj instanceof ProjectObjectHolder<?> other
            && registry == other.registry
            && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectObjectHolder[" + key.identifier() + "]";
    }
}