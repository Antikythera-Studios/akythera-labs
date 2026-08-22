package com.yesman.akytheralabs.akythera.datablock;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.mixin.MixinEnumerableDataBlockManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Never-failing wrapper around an [EnumerableDataBlockManager]'s name codec — the data block counterpart of
/// [AbstractDataHolder#guarded].
///
/// A data block a data pack defines is registered by `EnumerableDataBlockReloadListener`, a
/// `PackType#SERVER_DATA` reload listener. `WorldLoader` runs `RegistryDataLoader#load` — which
/// decodes every data pack registry, animator scripts included — and only afterwards
/// `ReloadableServerResources#loadResources`, where that listener runs. An id no mod hardcodes is
/// therefore unknown while the registries that reference it decode, and the plain name codec fails
/// the whole world load over it.
///
/// This wrapper decodes such an id to a stand-in instead. The stand-in answers as the real block
/// from the moment the listener registers it, so the pass is a deferral rather than a substitution:
/// what is lost is only the ordinal, until the reload lands.
///
/// Installed over every manager by [MixinEnumerableDataBlockManager], deliberately from the editor rather
/// than the engine — a player's game should still refuse a data pack that references a block nothing defines.
@NullMarked
public final class GuardedDataBlockCodec {
    private GuardedDataBlockCodec() {}

    /// Reported by a stand-in while its identifier stays unresolved.
    public static final int UNRESOLVED_ORDINAL = -1;

    /// One wrapper per manager, so the codec the engine captures in its static initializers is
    /// the same instance on every call.
    private static final Map<EnumerableDataBlockManager<?>, Codec<?>> GUARDED = new ConcurrentHashMap<>();

    /// One stand-in per manager and identifier: two references to the same unresolved id decode to
    /// the same object, which is what lets [StandIn] answer `equals` by identity.
    private static final Map<EnumerableDataBlockManager<?>, Map<Identifier, EnumerableDataBlock>> STAND_INS =
        new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends EnumerableDataBlock> Codec<T> guard(EnumerableDataBlockManager<T> manager, Codec<T> inner) {
        return (Codec<T>) GUARDED.computeIfAbsent(manager, _ -> new Codec<T>() {
            @Override
            public <D> DataResult<Pair<T, D>> decode(DynamicOps<D> ops, D input) {
                DataResult<Pair<T, D>> result = inner.decode(ops, input);

                if (result.result().isPresent()) {
                    return result;
                }

                // Only an id nothing has registered yet is tolerated. Anything else the inner codec
                // rejects — a malformed identifier, a value that is not a string at all — keeps failing.
                Identifier id = Identifier.CODEC.parse(ops, input).result().orElse(null);
                T standIn = id == null ? null : standIn(manager, id);

                if (standIn == null) {
                    return result;
                }

                AkytheraLabs.LOGGER.warn("Data block {} is not registered yet, decoded as a stand-in", id);

                return DataResult.success(Pair.of(standIn, ops.empty()));
            }

            @Override
            public <D> DataResult<D> encode(T input, DynamicOps<D> ops, D prefix) {
                // A stand-in answers `identifier()` with the id it was decoded from, so the inner
                // codec writes an unresolved reference back exactly as it was read.
                return inner.encode(input, ops, prefix);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends EnumerableDataBlock> @Nullable T standIn(EnumerableDataBlockManager<T> manager, Identifier id) {
        Class<T> blockType = blockTypeOf(manager);

        if (blockType == null) {
            return null;
        }

        return (T) STAND_INS
            .computeIfAbsent(manager, _ -> new ConcurrentHashMap<>())
            .computeIfAbsent(id, key -> (EnumerableDataBlock) Proxy.newProxyInstance(
                blockType.getClassLoader(),
                new Class<?>[] { blockType },
                new StandIn<>(manager, key)
            ));
    }

    /// The interface a stand-in has to implement, taken from labs' catalogue of the editable types.
    /// A manager outside it holds hardcoded entries only — nothing can define one late, so nothing
    /// is guarded there.
    @SuppressWarnings("unchecked")
    private static <T extends EnumerableDataBlock> @Nullable Class<T> blockTypeOf(EnumerableDataBlockManager<T> manager) {
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            if (entry.manager() == manager) {
                return (Class<T>) entry.blockType();
            }
        }

        return null;
    }

    /// Answers as the block `id` names the moment something registers it, and as an inert
    /// placeholder until then.
    private record StandIn<T extends EnumerableDataBlock>(EnumerableDataBlockManager<T> manager, Identifier id)
        implements InvocationHandler
    {
        @Override
        public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] args) throws Throwable {
            T resolved = manager.get(id).orElse(null);

            return switch (method.getName()) {
                case "identifier" -> id;
                case "getEnumManager" -> manager;
                case "universalOrdinal" -> resolved == null ? UNRESOLVED_ORDINAL : resolved.universalOrdinal();
                // One stand-in per id, so identity is the whole of equality; the hash has to stay
                // put across resolution, so it comes from the id rather than the resolved block.
                case "equals" -> args != null && proxy == args[0];
                case "hashCode" -> id.hashCode();
                case "toString" -> resolved == null ? "unresolved " + id : resolved.toString();
                // Every abstract method of [EnumerableDataBlock] is answered above, so what is left
                // here is a default method: run the block's own if there is one, the interface's if not.
                default -> resolved == null
                    ? InvocationHandler.invokeDefault(proxy, method, args)
                    : method.invoke(resolved, args);
            };
        }
    }
}
