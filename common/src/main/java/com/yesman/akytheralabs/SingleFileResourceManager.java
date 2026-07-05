package com.yesman.akytheralabs;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Resource manager for temporary files (drag & drop check)
@NullMarked
public record SingleFileResourceManager(Path filePath) implements ResourceManager {
    @Override
    public Set<String> getNamespaces() {
        return Set.of("validate");
    }

    @Override
    public Optional<Resource> getResource(Identifier id) {
        if (!matchesHeldFile(id)) return Optional.empty();

        return Optional.of(new Resource(DUMMY_PACK, IoSupplier.create(filePath)));
    }

    @Override
    public List<Resource> getResourceStack(Identifier id) {
        if (!matchesHeldFile(id)) return List.of();

        return List.of(new Resource(DUMMY_PACK, IoSupplier.create(filePath)));
    }

    /// Only respond to requests whose path suffix matches the held file's extension.
    /// `AnimationLoader.getLoader` probes `<base>.efasset` first, then `<base>.json` —
    /// without this gate, a dropped `.json` file would be returned for the `.efasset`
    /// probe and the binary loader would try to parse JSON bytes.
    private boolean matchesHeldFile(Identifier id) {
        String heldName = filePath.getFileName().toString().toLowerCase();
        String requested = id.getPath().toLowerCase();

        if (heldName.endsWith(".efasset")) return requested.endsWith(".efasset");
        if (heldName.endsWith(".json")) return requested.endsWith(".json");
        return false;
    }

    @Override @Deprecated
    public Map<Identifier, Resource> listResources(String dir, Predicate<Identifier> filter) {
        return Map.of();
    }

    @Override @Deprecated
    public Map<Identifier, List<Resource>> listResourceStacks(String dir, Predicate<Identifier> filter) {
        return Map.of();
    }

    @Override @Deprecated
    public Stream<PackResources> listPacks() {
        return Stream.empty();
    }

    private static final PackResources DUMMY_PACK = new PackResources() {
        @Override @Deprecated
        public @Nullable IoSupplier<InputStream> getRootResource(String... p) {
            return null;
        }

        @Override @Deprecated
        public @Nullable IoSupplier<InputStream> getResource(PackType t, Identifier l) {
            return null;
        }

        @Override @Deprecated
        public void listResources(PackType t, String ns, String dir, ResourceOutput out) {
        }

        @Override @Deprecated
        public Set<String> getNamespaces(PackType t) {
            return Set.of();
        }

        @Override @Deprecated
        public @Nullable <T> T getMetadataSection(MetadataSectionType<T> t) {
            return null;
        }

        @Override @Deprecated
        public PackLocationInfo location() {
            return null;// new PackLocationInfo(VALIDATION, Component.literal(VALIDATION), PackSource.BUILT_IN, Optional.empty());
        }

        @Override @Deprecated
        public void close() {
        }
    };
}
