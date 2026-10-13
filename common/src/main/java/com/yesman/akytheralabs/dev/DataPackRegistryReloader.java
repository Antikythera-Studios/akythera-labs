package com.yesman.akytheralabs.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Dev-only hot reload for the [AkytheraRegistries.DataPack] datapack registries.
///
/// Vanilla loads these registries from datapacks exactly once at world load and freezes them;
/// `/reload` reuses the frozen [RegistryAccess] and never re-decodes them. This re-reads each
/// registry's JSON from the (already reloaded) [ResourceManager] and refills the live registry
/// instances in place, imitating what a `SimplePreparableReloadListener` would do.
///
/// Single-player owner only. There is no networking: the client's synced copy is refilled
/// in-process from the server registry (safe because both live in the same JVM).
@NullMarked
public final class DataPackRegistryReloader {
    private DataPackRegistryReloader() {}

    private record RegistryEntry<T>(ResourceKey<Registry<T>> key, Codec<T> codec) {}

    /// Reloaded in order: an [EntityPatchBlueprint] resolves the animator script its armature names,
    /// so it decodes after the registry holding those.
    private static final List<RegistryEntry<?>> REGISTRIES = List.of(
        new RegistryEntry<>(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AnimationMontage.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AnimationSequence.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, BlendSpace2D.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ItemPatchBlueprint.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, DataDrivenAnimatorScript.AnimatorScriptBlueprint.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, EntityPatchBlueprint.CODEC),
        new RegistryEntry<>(AkytheraRegistries.DataPack.WIDGET_KEY, WidgetBlueprint.CODEC)
    );

    /// Server thread: re-decode every datapack registry from the server's freshly reloaded
    /// [ResourceManager] and refill the live registry instances.
    public static void reloadServer(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        ResourceManager resourceManager = server.getResourceManager();
        RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);

        for (RegistryEntry<?> entry : REGISTRIES) {
            reloadFromDisk(access, resourceManager, ops, entry);
        }
    }

    /// Same-JVM, single-player only: mirror the server's (already refilled) registries into the
    /// client's synced copy. No packet is sent; the value objects are shared directly.
    public static void copyServerToClient(RegistryAccess serverAccess, RegistryAccess clientAccess) {
        for (RegistryEntry<?> entry : REGISTRIES) {
            copyInProcess(serverAccess, clientAccess, entry);
        }
    }

    private static <T> void reloadFromDisk(RegistryAccess access, ResourceManager resourceManager, RegistryOps<JsonElement> ops, RegistryEntry<T> entry) {
        WritableRegistry<T> registry = asWritable(access, entry.key());

        if (registry == null) {
            return;
        }

        Map<ResourceKey<T>, T> decoded = new LinkedHashMap<>();
        FileToIdConverter converter = FileToIdConverter.registry(entry.key());

        for (Map.Entry<Identifier, Resource> resource : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceKey<T> elementKey = ResourceKey.create(entry.key(), converter.fileToId(resource.getKey()));

            try (Reader reader = resource.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<T> result = entry.codec().parse(ops, json);

                result.resultOrPartial(error -> AkytheraLabs.LOGGER.error("Failed to decode datapack registry element {}: {}", elementKey, error))
                    .ifPresent(value -> {
                        decoded.put(elementKey, value);
                    });
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to read datapack registry element {}", resource.getKey(), e);
            }
        }

        RegistryValidator<T> validator = AkytheraRegistries.DataPack.getValidator(entry.key());

        // The checks a world load runs across the entries, run on a staged copy until what is left passes: an
        // entry failing them is left out like one failing to decode, which may fail an entry naming it in turn.
        while (validator != null) {
            MappedRegistry<T> staged = new MappedRegistry<>(entry.key(), Lifecycle.stable());
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

        AkytheraLabs.LOGGER.info("Reloaded {} entries into registry {}", decoded.size(), entry.key().identifier());
    }

    private static <T> void copyInProcess(RegistryAccess serverAccess, RegistryAccess clientAccess, RegistryEntry<T> entry) {
        WritableRegistry<T> target = asWritable(clientAccess, entry.key());

        if (target == null) {
            return;
        }

        Registry<T> source = serverAccess.lookupOrThrow(entry.key());
        Map<ResourceKey<T>, T> entries = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<T>, T> element : source.entrySet()) {
            entries.put(element.getKey(), element.getValue());
        }

        refill(target, entries);
    }

    /// Unfreeze -> clear the lookup maps (but keep `byKey`, so existing [Holder.Reference]s rebind to the new
    /// values instead of dangling) -> re-register -> refreeze.
    ///
    /// The `frozen` flag is toggled directly: vanilla's `freeze()` throws when tags are already
    /// bound while the NeoForge variant short-circuits, and the flag behaves identically on both
    /// loaders. Tags are left untouched — they reference the same rebound `Holder.Reference`s and
    /// stay valid.
    @SuppressWarnings("unchecked")
    private static <T> void refill(WritableRegistry<T> registry, Map<ResourceKey<T>, T> entries) {
        MappedRegistryAccessor<T> accessor = (MappedRegistryAccessor<T>) registry;

        accessor.akythera$setFrozen(false);
        accessor.akythera$getById().clear();
        accessor.akythera$getToId().clear();
        accessor.akythera$getByLocation().clear();
        accessor.akythera$getByValue().clear();
        accessor.akythera$getRegistrationInfos().clear();

        entries.forEach((key, value) -> registry.register(key, value, RegistrationInfo.BUILT_IN));

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