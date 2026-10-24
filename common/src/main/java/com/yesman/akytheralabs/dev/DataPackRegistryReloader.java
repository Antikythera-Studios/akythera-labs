package com.yesman.akytheralabs.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.mixin.HolderReferenceAccessor;
import com.yesman.akytheralabs.mixin.MappedRegistryAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// Dev-only hot reload for the [AkytheraRegistries.DataPack] datapack registries.
///
/// Vanilla loads these registries from datapacks exactly once at world load and freezes them;
/// `/reload` reuses the frozen [RegistryAccess] and never re-decodes them. This re-reads each
/// registry's JSON from the (already reloaded) [ResourceManager] and refills the live registry
/// instances in place, the way a world load fills them.
///
/// Single-player owner only. There is no networking: the client's synced copy is refilled
/// in-process from the server registry (safe because both live in the same JVM).
@NullMarked
public final class DataPackRegistryReloader {
    private DataPackRegistryReloader() {}

    /// Server thread: re-decode every datapack registry from the server's freshly reloaded
    /// [ResourceManager] and refill the live registry instances.
    public static void reloadServer(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        ResourceManager resourceManager = server.getResourceManager();
        RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);

        for (AkytheraRegistries.DataPack.Registration<?> registration : AkytheraRegistries.DataPack.REGISTRATIONS) {
            reloadFromDisk(access, resourceManager, ops, registration);
        }
    }

    /// Same-JVM, single-player only: mirror the server's (already refilled) registries into the
    /// client's synced copy. No packet is sent; the value objects are shared directly.
    public static void copyServerToClient(RegistryAccess serverAccess, RegistryAccess clientAccess) {
        for (AkytheraRegistries.DataPack.Registration<?> registration : AkytheraRegistries.DataPack.REGISTRATIONS) {
            copyInProcess(serverAccess, clientAccess, registration);
        }
    }

    private static <T> void reloadFromDisk(RegistryAccess access, ResourceManager resourceManager, RegistryOps<JsonElement> ops, AkytheraRegistries.DataPack.Registration<T> registration) {
        WritableRegistry<T> registry = asWritable(access, registration.key());

        if (registry == null) {
            return;
        }

        Map<ResourceKey<T>, T> decoded = new LinkedHashMap<>();
        FileToIdConverter converter = FileToIdConverter.registry(registration.key());

        for (Map.Entry<Identifier, Resource> resource : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceKey<T> elementKey = ResourceKey.create(registration.key(), converter.fileToId(resource.getKey()));

            try (Reader reader = resource.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<T> result = registration.codec().parse(ops, json);

                result.resultOrPartial(error -> AkytheraLabs.LOGGER.error("Failed to decode datapack registry element {}: {}", elementKey, error))
                    .ifPresent(value -> {
                        decoded.put(elementKey, value);
                    });
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to read datapack registry element {}", resource.getKey(), e);
            }
        }

        RegistryValidator<T> validator = AkytheraRegistries.DataPack.getValidator(registration.key());

        // The checks a world load runs across the entries, run on a staged copy until what is left passes: an
        // entry failing them is left out like one failing to decode, which may fail an entry naming it in turn.
        while (validator != null) {
            MappedRegistry<T> staged = new MappedRegistry<>(registration.key(), Lifecycle.stable());
            decoded.forEach((key, value) -> staged.register(key, value, RegistrationInfo.BUILT_IN));
            staged.freeze();

            Map<ResourceKey<?>, Exception> errors = new HashMap<>();
            validator.validate(staged, errors);

            if (errors.isEmpty()) break;

            errors.forEach((key, error) -> {
                AkytheraLabs.LOGGER.error("Datapack registry element {} failed validation: {}", key.identifier(), error.getMessage());
                decoded.remove(key);
            });
        }

        refill(registry, decoded);

        AkytheraLabs.LOGGER.info("Reloaded {} entries into registry {}", decoded.size(), registration.key().identifier());
    }

    private static <T> void copyInProcess(RegistryAccess serverAccess, RegistryAccess clientAccess, AkytheraRegistries.DataPack.Registration<T> registration) {
        WritableRegistry<T> target = asWritable(clientAccess, registration.key());

        if (target == null) {
            return;
        }

        Registry<T> source = serverAccess.lookupOrThrow(registration.key());
        Map<ResourceKey<T>, T> entries = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<T>, T> element : source.entrySet()) {
            entries.put(element.getKey(), element.getValue());
        }

        refill(target, entries);
    }

    /// Unfreeze -> drop the keys `entries` lacks and clear the other lookup maps -> re-register and bind each
    /// value -> refreeze. A key kept in `byKey` keeps its [Holder.Reference], so existing references rebind to
    /// the new values instead of dangling.
    ///
    /// The `frozen` flag is toggled directly: vanilla's `freeze()` throws when tags are already
    /// bound while the NeoForge variant short-circuits, and the flag behaves identically on both
    /// loaders. Tags are left untouched — they reference the same rebound `Holder.Reference`s and
    /// stay valid.
    @SuppressWarnings("unchecked")
    private static <T> void refill(WritableRegistry<T> registry, Map<ResourceKey<T>, T> entries) {
        MappedRegistryAccessor<T> accessor = (MappedRegistryAccessor<T>) registry;

        accessor.akythera$setFrozen(false);
        accessor.akythera$getByKey().keySet().retainAll(entries.keySet());
        accessor.akythera$getById().clear();
        accessor.akythera$getToId().clear();
        accessor.akythera$getByLocation().clear();
        accessor.akythera$getByValue().clear();
        accessor.akythera$getRegistrationInfos().clear();

        entries.forEach((key, value) -> ((HolderReferenceAccessor<T>) registry.register(key, value, RegistrationInfo.BUILT_IN)).akythera$bindValue(value));

        accessor.akythera$setFrozen(true);
    }

    private static <T> @Nullable WritableRegistry<T> asWritable(RegistryAccess access, ResourceKey<Registry<T>> key) {
        Optional<Registry<T>> registry = access.lookup(key);

        // when registry doesn't exist, especially when no entry in registry.
        if (registry.isEmpty()) return null;

        if (registry.get() instanceof WritableRegistry<T> writable) {
            return writable;
        }

        AkytheraLabs.LOGGER.warn("Registry {} is not writable; skipping datapack reload", key.identifier());

        return null;
    }
}
