package com.yesman.akytheralabs.dev.hotreload;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegisterObjectsEvent;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dev.DataPackRegistryReloader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/// Registers the [AkytheraRegistries.Frozen] entries of reloaded namespaces anew, by posting
/// [RegisterObjectsEvent] again.
///
/// No entry is removed. An entry the listeners no longer register keeps its old value, except a
/// [ScriptFunction], which becomes [ScriptModularizeHelper#removedFunction]. A [Holder.Reference] of a kept id
/// rebinds to the new value, so the holders handed out at mod construction stay valid.
public final class FrozenRegistryReloader {
    private FrozenRegistryReloader() {}

    /// Posts [RegisterObjectsEvent] and refills the registries it registered into. Registrations to a namespace
    /// outside `namespaces` are dropped. Run while no other thread reads the registries.
    public static void reload(final Collection<String> namespaces) {
        Map<ResourceKey<? extends Registry<?>>, Map<ResourceKey<?>, Object>> registered = new LinkedHashMap<>();

        BuiltInEventHooks.Registry.REGISTER_OBJECTS.post(new RegisterObjectsEvent(new RegisterObjectsEvent.Registrars() {
            @Override
            public <T> ModPlatform.Registrar<T> getRegistrar(final ResourceKey<? extends Registry<T>> id, final String namespace) {
                return new CapturingRegistrar<>(id, namespace, namespaces.contains(namespace) ? registered.computeIfAbsent(id, _ -> new LinkedHashMap<>()) : null);
            }
        }, true));

        Set<ResourceKey<? extends Registry<?>>> registries = new LinkedHashSet<>(registered.keySet());

        for (ResourceKey<?> entry : RegisterObjectsEvent.registeredEntries()) {
            if (namespaces.contains(entry.identifier().getNamespace())) registries.add(ResourceKey.createRegistryKey(entry.registry()));
        }

        for (ResourceKey<? extends Registry<?>> registryKey : registries) {
            refill(registryKey, namespaces, registered.getOrDefault(registryKey, Map.of()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void refill(final ResourceKey<? extends Registry<?>> registryKey, final Collection<String> namespaces, final Map<ResourceKey<?>, Object> fresh) {
        if (!(BuiltInRegistries.REGISTRY.getValue(registryKey.identifier()) instanceof WritableRegistry<?> found)) {
            AkytheraLabs.LOGGER.warn("Registry {} is not writable; skipping its hot reload", registryKey.identifier());
            return;
        }

        WritableRegistry<T> registry = (WritableRegistry<T>) found;
        Map<ResourceKey<T>, T> entries = new LinkedHashMap<>();

        registry.listElements().forEach(reference -> entries.put(reference.key(), reference.value()));
        // An equal value keeps the old object, which decoded data may still hold
        fresh.forEach((key, value) -> entries.merge((ResourceKey<T>) key, (T) value, (old, renewed) -> old.equals(renewed) ? old : renewed));

        for (ResourceKey<?> entry : RegisterObjectsEvent.registeredEntries()) {
            if (!entry.registry().equals(registryKey.identifier()) || !namespaces.contains(entry.identifier().getNamespace()) || fresh.containsKey(entry)) continue;

            if (registryKey.equals(AkytheraRegistries.Frozen.SCRIPT_FUNCTION_KEY) && entries.get(entry) instanceof ScriptFunction removed) {
                entries.put((ResourceKey<T>) entry, (T) ScriptModularizeHelper.removedFunction(removed, entry.identifier()));
            }
        }

        DataPackRegistryReloader.refill(registry, entries);
        AkytheraLabs.LOGGER.info("Hot reloaded {} entries of registry {}", fresh.size(), registryKey.identifier());
    }

    /// Stores what a listener registers into `registered`, or drops it when `registered` is null.
    private record CapturingRegistrar<T>(ResourceKey<? extends Registry<T>> registry, String namespace, @Nullable Map<ResourceKey<?>, Object> registered) implements ModPlatform.Registrar<T> {
        @Override
        public <O extends T> Holder<O> register(final String name, final Supplier<O> supplier) {
            return store(name, supplier.get());
        }

        @Override
        public <O extends T> Holder<O> register(final String name, final Function<Identifier, O> supplier) {
            Identifier id = Identifier.fromNamespaceAndPath(namespace, name);

            return store(name, supplier.apply(id));
        }

        private <O extends T> Holder<O> store(final String name, final O value) {
            if (registered != null) registered.put(ResourceKey.create(registry, Identifier.fromNamespaceAndPath(namespace, name)), value);

            return Holder.direct(value);
        }
    }
}
