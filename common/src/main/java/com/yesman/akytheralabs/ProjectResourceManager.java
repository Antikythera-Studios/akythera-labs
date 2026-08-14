package com.yesman.akytheralabs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Reads assets and data objects out of the project folders under `akythera_projects`.
///
/// A project carries exactly one namespace, its own name, so two projects can never collide. The
/// namespace names the project directory and the `assets` / `data` directories sit inside it, which
/// leaves a project rename a single directory move.
///
/// Path layout, namespace-first and so reversed from vanilla:
/// - Vanilla: `{root}/assets/{namespace}/{path}`
/// - Project: `{projectsRoot}/{namespace}/assets/{path}`
///
/// Stateless: the namespace is read from the incoming [Identifier] every time, so switching
/// projects rebuilds nothing.
@NullMarked
public final class ProjectResourceManager implements ResourceManager {
    /// Only called while a project is active — safe to resolve lazily.
    @Override
    public Set<String> getNamespaces() {
        return Set.of(AkytheraProject.getCurrentProject().getProjectName());
    }

    @Override
    public Optional<Resource> getResource(Identifier id) {
        Path resolved = resolve(id);

        if (resolved == null) return Optional.empty();

        return Optional.of(new Resource(new ProjectPackResources(id.getNamespace()), IoSupplier.create(resolved)));
    }

    @Override
    public List<Resource> getResourceStack(Identifier id) {
        return getResource(id).map(List::of).orElse(List.of());
    }

    @Override
    public Map<Identifier, Resource> listResources(String dir, Predicate<Identifier> filter) {
        String namespace = AkytheraProject.getCurrentProject().getProjectName();
        ProjectPackResources pack = new ProjectPackResources(namespace);
        Path projectRoot = AkytheraProject.getProjectsRootDirectory().resolve(namespace);
        Map<Identifier, Resource> result = new LinkedHashMap<>();

        collectFromDir(projectRoot.resolve("assets").resolve(dir), namespace, dir, filter, pack, result);
        collectFromDir(projectRoot.resolve("data").resolve(dir), namespace, dir, filter, pack, result);

        return result;
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String dir, Predicate<Identifier> filter) {
        return listResources(dir, filter).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
    }

    @Override
    public Stream<PackResources> listPacks() {
        return Stream.of(new ProjectPackResources(AkytheraProject.getCurrentProject().getProjectName()));
    }

    /// Resolves an identifier to a concrete file path by trying assets/ then data/.
    /// Returns null if the file does not exist under either directory.
    private static @Nullable Path resolve(Identifier id) {
        Path projectRoot = AkytheraProject.getProjectsRootDirectory().resolve(id.getNamespace());
        Path assetPath = projectRoot.resolve("assets").resolve(id.getPath());

        if (Files.isRegularFile(assetPath)) return assetPath;

        Path dataPath = projectRoot.resolve("data").resolve(id.getPath());

        if (Files.isRegularFile(dataPath)) return dataPath;

        return null;
    }

    private static void collectFromDir(
        Path dir,
        String namespace,
        String prefix,
        Predicate<Identifier> filter,
        ProjectPackResources pack,
        Map<Identifier, Resource> out
    ) {
        FileSystemHelper.forEachFile(dir, file -> {
            String relFromDir = FileSystemHelper.relativeIdPath(dir, file);
            String path = prefix.isEmpty() ? relFromDir : prefix + "/" + relFromDir;
            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);

            if (filter.test(id)) {
                out.put(id, new Resource(pack, IoSupplier.create(file)));
            }
        });
    }

    private record ProjectPackResources(String namespace) implements PackResources {
        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            Path resolved = ProjectResourceManager.resolve(location);

            return resolved != null ? IoSupplier.create(resolved) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            Path projectRoot = AkytheraProject.getProjectsRootDirectory().resolve(namespace);
            Path dir = projectRoot.resolve(type.getDirectory()).resolve(directory);

            FileSystemHelper.forEachFile(dir, file -> {
                String relFromDir = FileSystemHelper.relativeIdPath(dir, file);
                String path = directory + "/" + relFromDir;
                output.accept(Identifier.fromNamespaceAndPath(namespace, path), IoSupplier.create(file));
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of(namespace);
        }

        @Override
        public @Nullable <T> T getMetadataSection(MetadataSectionType<T> type) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            String id = "akythera_project/" + namespace;
            return new PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty());
        }

        @Override
        public void close() {
        }
    }
}