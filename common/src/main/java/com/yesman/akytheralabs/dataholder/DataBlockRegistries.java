package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.datablock.GuardedDataBlockCodec;
import com.yesman.akytheralabs.akythera.dataholder.datablock.AnimationInterfaceKeyHolder;
import com.yesman.akytheralabs.akythera.dataholder.datablock.AnimationLayerSignatureHolder;
import com.yesman.akytheralabs.akythera.dataholder.datablock.FSMAnimationStateHolder;
import com.yesman.akytheralabs.akythera.dataholder.datablock.WeaponStanceHolder;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.panel.main.processors.DataBlockResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Central, lazily-built catalogue of the editor-editable [EnumerableDataBlock] types — the single source of
/// truth shared by [AkytheraProject] (registry creation + loading + dir creation), [ExplorerPanel] (tree
/// roots), and [ResourceProcessor#forTag] (tag → processor).
///
/// Each type maps to a per-type [DataBlockHolder] whose `makeObject()` yields the real engine
/// object. The labs **registry key** is the engine manager's `directoryKey()` directly
/// (`ResourceKey<Registry<T>>`, path `datablock/<enumName>`) — used as the `ProjectObjectRegistry`
/// key and the on-disk `data/<path>/` directory.
///
/// **Open registry**: external mods (compat plugins) register their own data block types through
/// [#register] during client init, alongside their component holders — before the first project is
/// created or loaded, which is when [#all] is first read. Built-ins register from this class's static
/// initializer.
public final class DataBlockRegistries {
    private DataBlockRegistries() {}

    /// One editable data block type: its manager, the per-type holder factory + codec, and a bound
    /// [DataBlockResourceProcessor].
    public static final class Entry<T extends EnumerableDataBlock, H extends DataBlockHolder<T>> {
        private final EnumerableDataBlockManager<T> manager;
        private final Supplier<H> holderFactory;
        private final Codec<H> codec;
        private final DataBlockResourceProcessor<T> processor;
        private final Class<T> blockType;

        private Entry(EnumerableDataBlockManager<T> manager, Supplier<H> holderFactory, Codec<H> codec) {
            this.manager = manager;
            this.holderFactory = holderFactory;
            this.codec = codec;
            this.processor = new DataBlockResourceProcessor<>(manager.directoryKey(), manager);
            this.blockType = blockTypeOf(holderFactory.get().getClass());
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

        /// The engine enum interface this type's entries are, e.g. `FSMAnimationState`.
        public Class<T> blockType() {
            return blockType;
        }
    }

    /// Reads `T` back off a holder class's `extends DataBlockHolder<T>` clause — the one place the
    /// erased type argument survives.
    @SuppressWarnings("unchecked")
    private static <T extends EnumerableDataBlock> Class<T> blockTypeOf(Class<?> holderClass) {
        for (Class<?> cls = holderClass; cls != null; cls = cls.getSuperclass()) {
            if (cls.getGenericSuperclass() instanceof ParameterizedType parameterized
                && parameterized.getRawType() == DataBlockHolder.class) {
                return (Class<T>) parameterized.getActualTypeArguments()[0];
            }
        }

        throw new IllegalArgumentException(holderClass.getName() + " does not extend DataBlockHolder");
    }

    /// Insertion-ordered, so the explorer tree lists built-ins before addon types.
    private static final Map<ResourceKey<? extends Registry<?>>, Entry<?, ?>> ENTRIES = new LinkedHashMap<>();

    static {
        register(FSMAnimationState.ENUM_MANAGER, FSMAnimationStateHolder::new, FSMAnimationStateHolder.CODEC);
        register(AnimationLayerSignature.ENUM_MANAGER, AnimationLayerSignatureHolder::new, AnimationLayerSignatureHolder.CODEC);
        register(AnimationInterfaceKey.ENUM_MANAGER, AnimationInterfaceKeyHolder::new, AnimationInterfaceKeyHolder.CODEC);
        register(WeaponStance.ENUM_MANAGER, WeaponStanceHolder::new, WeaponStanceHolder.CODEC);
    }

    /// Registers the editor holder for one data block type: the engine manager the type is registered
    /// under, a factory for freshly created entries, and the editor persistence codec.
    ///
    /// `manager` must carry a `MapCodec` — a type built through `EnumerableDataBlockManager(String)`
    /// has no data pack form and cannot be authored here.
    ///
    /// Synchronized: [GuardedDataBlockCodec] reaches [#entryOf] from whichever worker thread
    /// `RegistryDataLoader` decodes a data pack registry on.
    public static synchronized <T extends EnumerableDataBlock, H extends DataBlockHolder<T>> void register(
        EnumerableDataBlockManager<T> manager,
        Supplier<H> holderFactory,
        Codec<H> codec
    ) {
        if (manager.getMapCodec() == null) {
            throw new IllegalArgumentException(
                "Data block type " + manager.directoryKey().identifier() + " has no data pack codec");
        }

        if (ENTRIES.putIfAbsent(manager.directoryKey(), new Entry<>(manager, holderFactory, codec)) != null) {
            throw new IllegalStateException(
                "Data block holder already registered for " + manager.directoryKey().identifier());
        }
    }

    public static synchronized Collection<Entry<?, ?>> all() {
        return List.copyOf(ENTRIES.values());
    }

    /// The catalogue entry for a data block tree root's tag — its labs registry key. Null for any
    /// other tag.
    public static synchronized @Nullable Entry<?, ?> entryOf(Object tag) {
        return ENTRIES.get(tag);
    }

    /// Tag → processor dispatch for [ResourceProcessor#forTag]. The tag for a data block tree root is its
    /// labs registry key.
    public static @Nullable DataBlockResourceProcessor<?> forKey(Object tag) {
        Entry<?, ?> entry = entryOf(tag);
        return entry == null ? null : entry.processor();
    }
}
