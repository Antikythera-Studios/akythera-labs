package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// Compact version of [Registry]. Avoided implementing it due to NeoForged added more methods to implement.
@NullMarked
public final class ProjectObjectRegistry<T, H extends DataHolder<T>> {
    private final ResourceKey<? extends Registry<T>> registryKey;

    private final Map<ResourceKey<T>, T> objectByResourceKey = new HashMap<>();
    private final Map<T, ResourceKey<T>> objectByResourceKeyInv = new HashMap<>();
    private final Map<Identifier, T> objectByIdentifier = new HashMap<>();
    private final Map<T, Identifier> objectByIdentifierInv = new HashMap<>();

    private final Map<ResourceKey<T>, H> dataHolderByResourceKey = new HashMap<>();
    private final Map<H, ResourceKey<T>> dataHolderByResourceKeyInv = new HashMap<>();
    private final Map<Identifier, H> dataHolderByIdentifier = new HashMap<>();
    private final Map<H, Identifier> dataHolderByIdentifierInv = new HashMap<>();

    /// One [ProjectObjectHolder] per key, shared by every referrer. Identity is stable across
    /// renames (`rekey` mutates the holder's key in place) so referrers never need rewiring.
    private final Map<ResourceKey<T>, ProjectObjectHolder<T>> holders = new HashMap<>();

    private final Supplier<H> defaultObjectProvider;
    private final Codec<H> codec;

    public ProjectObjectRegistry(ResourceKey<? extends Registry<T>> registryKey, Supplier<H> defaultObjectProvider, Codec<H> codec) {
        this.registryKey = registryKey;
        this.defaultObjectProvider = defaultObjectProvider;
        this.codec = codec;
    }

    /// Return value always null. Don't use it
    @SuppressWarnings("unchecked")
    public Holder.@Nullable Reference<H> register(ResourceKey<T> resourceKey, H dataHolder) {
        dataHolderByResourceKey.put(resourceKey, dataHolder);
        dataHolderByResourceKeyInv.put(dataHolder, resourceKey);
        dataHolderByIdentifier.put(resourceKey.identifier(), dataHolder);
        dataHolderByIdentifierInv.put(dataHolder, resourceKey.identifier());

        // Push the key onto the holder so its bind callbacks can hit
        // saveEntry / getEntryFilePath without re-querying us.
        if (dataHolder instanceof AbstractDataHolder<?> a) {
            ((AbstractDataHolder<T>) a).setOwnResourceKey(resourceKey);
        }

        T object = dataHolder.makeObject();

        objectByResourceKey.put(resourceKey, object);
        objectByResourceKeyInv.put(object, resourceKey);
        objectByIdentifier.put(resourceKey.identifier(), object);
        objectByIdentifierInv.put(object, resourceKey.identifier());

        return null;
    }

    /// Swap the holder registered under `key` for `newHolder` (e.g. a reload of the on-disk state),
    /// **keeping** the shared [ProjectObjectHolder] so referrers stay wired. Clears the previous
    /// holder's / object's inverse-map entries, then [#register]s the replacement — which rewrites the
    /// forward maps, pushes the key onto the new holder, and refreshes the cached object.
    public void replaceDataHolder(ResourceKey<T> key, H newHolder) {
        H oldHolder = dataHolderByResourceKey.get(key);
        if (oldHolder != null) {
            dataHolderByResourceKeyInv.remove(oldHolder);
            dataHolderByIdentifierInv.remove(oldHolder);
        }

        T oldObject = objectByResourceKey.get(key);
        if (oldObject != null) {
            objectByResourceKeyInv.remove(oldObject);
            objectByIdentifierInv.remove(oldObject);
        }

        register(key, newHolder);
    }

    public void remove(Identifier identifier) {
        // Remove data holders
        H dataHolderRemoved = dataHolderByIdentifier.remove(identifier);
        dataHolderByIdentifierInv.remove(dataHolderRemoved);
        ResourceKey<T> rk = ResourceKey.create(registryKey, identifier);
        dataHolderByResourceKey.remove(rk);
        dataHolderByResourceKeyInv.remove(dataHolderRemoved);

        // Remove objects
        T objectRemoved = objectByIdentifier.remove(identifier);
        objectByIdentifierInv.remove(objectRemoved);
        objectByResourceKey.remove(rk);
        objectByResourceKeyInv.remove(objectRemoved);

        // Drop the shared holder so a re-created id gets a fresh one.
        holders.remove(rk);
    }

    /// One shared [ProjectObjectHolder] for `resourceKey`, created lazily. Referrers hold this
    /// instance; its `value()` always reads the current cached object and its key follows [#rekey].
    public ProjectObjectHolder<T> projectHolder(ResourceKey<T> resourceKey) {
        return holders.computeIfAbsent(resourceKey, k -> new ProjectObjectHolder<>(this, k));
    }

    /// Re-run `makeObject()` for one entry and swap the fresh object into all object maps (+ inverses).
    /// Every [ProjectObjectHolder] for this key then returns the refreshed object. Call on a property
    /// edit (the object instance is replaced; the holder + key are untouched).
    public void refreshObject(ResourceKey<T> resourceKey) {
        H holder = dataHolderByResourceKey.get(resourceKey);
        if (holder == null) return;

        T old = objectByResourceKey.get(resourceKey);

        if (old != null) {
            objectByResourceKeyInv.remove(old);
            objectByIdentifierInv.remove(old);
        }

        T fresh = holder.makeObject();
        objectByResourceKey.put(resourceKey, fresh);
        objectByResourceKeyInv.put(fresh, resourceKey);
        objectByIdentifier.put(resourceKey.identifier(), fresh);
        objectByIdentifierInv.put(fresh, resourceKey.identifier());
    }

    /// "Rename the holder": move one entry from `oldKey` to `newKey` **without** replacing the
    /// object instance. Re-keys the dataHolder + object maps, pushes the new key onto the holder,
    /// and mutates the one shared [ProjectObjectHolder] — so every referrer now reports the new id
    /// with no per-reference rewrite. (Data block objects read their id live, so the preserved
    /// instance transparently reflects the new id.)
    public void rekey(ResourceKey<T> oldKey, ResourceKey<T> newKey) {
        H holder = dataHolderByResourceKey.remove(oldKey);
        if (holder == null) return;

        dataHolderByResourceKey.put(newKey, holder);
        dataHolderByResourceKeyInv.put(holder, newKey);
        dataHolderByIdentifier.remove(oldKey.identifier());
        dataHolderByIdentifier.put(newKey.identifier(), holder);
        dataHolderByIdentifierInv.put(holder, newKey.identifier());

        T obj = objectByResourceKey.remove(oldKey);

        if (obj != null) {
            objectByResourceKey.put(newKey, obj);
            objectByResourceKeyInv.put(obj, newKey);
            objectByIdentifier.remove(oldKey.identifier());
            objectByIdentifier.put(newKey.identifier(), obj);
            objectByIdentifierInv.put(obj, newKey.identifier());
        }

        if (holder instanceof AbstractDataHolder<?> a) {
            //noinspection unchecked
            ((AbstractDataHolder<T>) a).setOwnResourceKey(newKey);
        }

        ProjectObjectHolder<T> ph = holders.remove(oldKey);

        if (ph != null) {
            ph.setKey(newKey);
            holders.put(newKey, ph);
        }
    }

    public void clearEntry() {
        dataHolderByResourceKey.clear();
        dataHolderByResourceKeyInv.clear();
        dataHolderByIdentifier.clear();
        dataHolderByIdentifierInv.clear();

        objectByResourceKey.clear();
        objectByResourceKeyInv.clear();
        objectByIdentifier.clear();
        objectByIdentifierInv.clear();

        holders.clear();
    }

    /// Rebuild every key in this registry with `newNamespace` as the
    /// `Identifier` namespace, preserving the original holder instances
    /// **and** the shared [ProjectObjectHolder]s (their keys are mutated in
    /// place, so referrers stay valid). Used by the project-rename pipeline.
    public void remapNamespace(String newNamespace) {
        if (dataHolderByResourceKey.isEmpty()) return;

        List<Map.Entry<ResourceKey<T>, H>> oldHolders = new ArrayList<>(dataHolderByResourceKey.entrySet());
        Map<ResourceKey<T>, ProjectObjectHolder<T>> oldProjectHolders = new HashMap<>(holders);

        dataHolderByResourceKey.clear();
        dataHolderByResourceKeyInv.clear();
        dataHolderByIdentifier.clear();
        dataHolderByIdentifierInv.clear();
        objectByResourceKey.clear();
        objectByResourceKeyInv.clear();
        objectByIdentifier.clear();
        objectByIdentifierInv.clear();
        holders.clear();

        for (Map.Entry<ResourceKey<T>, H> e : oldHolders) {
            Identifier oldId = e.getKey().identifier();
            Identifier newId = Identifier.fromNamespaceAndPath(newNamespace, oldId.getPath());
            ResourceKey<T> newKey = ResourceKey.create(registryKey, newId);
            H holder = e.getValue();

            if (holder instanceof AbstractDataHolder<?> a) {
                @SuppressWarnings("unchecked")
                AbstractDataHolder<T> typed = (AbstractDataHolder<T>) a;
                typed.setOwnResourceKey(newKey);
            }

            T object = holder.makeObject();

            dataHolderByResourceKey.put(newKey, holder);
            dataHolderByResourceKeyInv.put(holder, newKey);
            dataHolderByIdentifier.put(newId, holder);
            dataHolderByIdentifierInv.put(holder, newId);

            objectByResourceKey.put(newKey, object);
            objectByResourceKeyInv.put(object, newKey);
            objectByIdentifier.put(newId, object);
            objectByIdentifierInv.put(object, newId);

            // Preserve the shared referrer-facing holder (mutate its key in place).
            ProjectObjectHolder<T> ph = oldProjectHolders.get(e.getKey());
            if (ph != null) {
                ph.setKey(newKey);
                holders.put(newKey, ph);
            }
        }
    }

    /// Live view over the holder values; for iteration only. Use `register`
    /// / `remove` / `remapNamespace` / `clearEntry` to mutate.
    public Collection<H> dataHolders() {
        return dataHolderByResourceKey.values();
    }

    public Codec<H> getCodec() {
        return codec;
    }

    public H provideDefault() {
        return defaultObjectProvider.get();
    }

    public ResourceKey<? extends Registry<T>> key() {
        return registryKey;
    }

    public @Nullable Identifier getKey(T h) {
        return objectByIdentifierInv.get(h);
    }

    public Optional<ResourceKey<T>> getResourceKey(T t) {
        return Optional.ofNullable(objectByResourceKeyInv.get(t));
    }

    public Optional<ResourceKey<T>> getResourceKeyByHolder(H h) {
        return Optional.ofNullable(dataHolderByResourceKeyInv.get(h));
    }

    public Optional<Identifier> getIdentifierByHolder(H h) {
        return Optional.ofNullable(dataHolderByIdentifierInv.get(h));
    }

    public @Nullable T getValue(@Nullable ResourceKey<T> resourceKey) {
        return objectByResourceKey.get(resourceKey);
    }

    public @Nullable H getDataHolder(@Nullable ResourceKey<T> resourceKey) {
        return dataHolderByResourceKey.get(resourceKey);
    }

    public @Nullable T getValue(@Nullable Identifier identifier) {
        return objectByIdentifier.get(identifier);
    }

    public Set<Identifier> keySet() {
        return dataHolderByIdentifier.keySet();
    }

    public Set<Map.Entry<ResourceKey<T>, T>> entrySet() {
        return objectByResourceKey.entrySet();
    }

    public Set<ResourceKey<T>> registryKeySet() {
        return objectByResourceKey.keySet();
    }

    public Set<Map.Entry<ResourceKey<T>, H>> dataHolderEntrySet() {
        return dataHolderByResourceKey.entrySet();
    }

    public Set<ResourceKey<T>> typedRegistryKeySet() {
        return dataHolderByResourceKey.keySet();
    }

    public boolean containsKey(Identifier identifier) {
        return dataHolderByIdentifier.containsKey(identifier);
    }


    public boolean containsKey(ResourceKey<T> resourceKey) {
        return dataHolderByResourceKey.containsKey(resourceKey);
    }

    @Deprecated
    public Holder.Reference<T> createIntrusiveHolder(T h) {
        return null;
    }

    @Deprecated
    public Optional<Holder.Reference<T>> get(int i) {
        return Optional.empty();
    }

    @Deprecated
    public Optional<Holder.Reference<T>> get(Identifier identifier) {
        return Optional.empty();
    }

    @Deprecated
    public DataComponentLookup<T> componentLookup() {
        return null;
    }

    @Deprecated
    public Stream<Holder.Reference<T>> listElements() {
        return Stream.empty();
    }

    @Deprecated
    public Stream<HolderSet.Named<T>> listTags() {
        return Stream.empty();
    }

    @Deprecated
    public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
        return Optional.empty();
    }

    @Deprecated
    public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
        return Optional.empty();
    }

    public Iterator<T> iterator() {
        return objectByIdentifier.values().iterator();
    }

    public Iterator<H> dataHolderIterator() {
        return dataHolderByIdentifier.values().iterator();
    }
}
