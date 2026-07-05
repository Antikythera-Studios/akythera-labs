package com.yesman.akytheralabs.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.gui.panel.main.processors.DataBlockResourceProcessor;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// Central, lazily-built catalogue of the editor-editable [EnumerableDataBlock] types — the single
/// source of truth shared by [com.yesman.akytheralabs.AkytheraProject] (registry creation + loading
/// + dir creation), [com.yesman.akytheralabs.gui.panel.main.ExplorerPanel] (tree roots), and
/// [com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor#forTag] (tag → processor).
///
/// Each type maps to a per-type [DataBlockHolder] whose `makeObject()` yields the real engine
/// object. The labs **registry key** is the engine manager's `directoryKey()` directly
/// (`ResourceKey<Registry<T>>`, path `datablock/<enumName>`) — used as the `ProjectObjectRegistry`
/// key and the on-disk `data/<path>/` directory.
///
/// Adding a new editable type (a new `EnumerableDataBlockSync#LISTENERS` entry) means adding a
/// [DataBlockHolder] subclass and a [#register] line here.
public final class DataBlockRegistries {
    private DataBlockRegistries() {}

    /// One editable data block type: its manager, the per-type holder factory + codec, and a bound
    /// [DataBlockResourceProcessor].
    public static final class Entry<T extends EnumerableDataBlock, H extends DataBlockHolder<T>> {
        private final EnumerableDataBlockManager<T> manager;
        private final Supplier<H> holderFactory;
        private final Codec<H> codec;
        private final DataBlockResourceProcessor<T> processor;

        private Entry(EnumerableDataBlockManager<T> manager, Supplier<H> holderFactory, Codec<H> codec) {
            this.manager = manager;
            this.holderFactory = holderFactory;
            this.codec = codec;
            this.processor = new DataBlockResourceProcessor<>(manager.directoryKey(), manager);
        }

        public ResourceKey<Registry<T>> registryKey() {
            return manager.directoryKey();
        }

        public EnumerableDataBlockManager<T> manager() {
            return manager;
        }

        public Supplier<H> holderFactory() {
            return holderFactory;
        }

        public Codec<H> codec() {
            return codec;
        }

        public DataBlockResourceProcessor<T> processor() {
            return processor;
        }
    }

    private static @Nullable Map<ResourceKey<? extends Registry<?>>, Entry<?, ?>> entries;

    private static Map<ResourceKey<? extends Registry<?>>, Entry<?, ?>> entries() {
        if (entries == null) {
            Map<ResourceKey<? extends Registry<?>>, Entry<?, ?>> built = new LinkedHashMap<>();
            register(built, FSMAnimationState.ENUM_MANAGER, FSMAnimationStateHolder::new, FSMAnimationStateHolder.CODEC);
            register(built, AnimationLayerSignature.ENUM_MANAGER, AnimationLayerSignatureHolder::new, AnimationLayerSignatureHolder.CODEC);
            entries = built;
        }

        return entries;
    }

    private static <T extends EnumerableDataBlock, H extends DataBlockHolder<T>> void register(
        Map<ResourceKey<? extends Registry<?>>, Entry<?, ?>> into,
        EnumerableDataBlockManager<T> manager,
        Supplier<H> holderFactory,
        Codec<H> codec
    ) {
        into.put(manager.directoryKey(), new Entry<>(manager, holderFactory, codec));
    }

    public static Collection<Entry<?, ?>> all() {
        return entries().values();
    }

    /// Tag → processor dispatch for [com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor#forTag].
    /// The tag for a data block tree root is its labs registry key.
    public static @Nullable DataBlockResourceProcessor<?> forKey(Object tag) {
        Entry<?, ?> entry = entries().get(tag);
        return entry == null ? null : entry.processor();
    }
}
