package com.yesman.akytheralabs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/// The open project's tags, one vanilla tag file each under `data/tags/<registry>/` of the project. Every tag is in
/// the project's namespace.
@NullMarked
public final class ProjectTags {
    /// The folder under the project's `data` holding every tag file.
    public static final String DIRECTORY = "tags";

    /// The registries some editor row takes a [TagKey] of.
    public static final List<ResourceKey<? extends Registry<?>>> REGISTRIES = List.of(Registries.ENTITY_TYPE);

    /// The tags of each of [#REGISTRIES], by path.
    private final Map<ResourceKey<? extends Registry<?>>, SortedMap<String, TagFile>> tags = new HashMap<>();

    /// The paths of the tags removed or renamed since the last [#save], by registry.
    private final Map<ResourceKey<? extends Registry<?>>, Set<String>> removedPaths = new HashMap<>();

    public static ProjectTags load(final Path projectRoot) {
        ProjectTags projectTags = new ProjectTags();

        for (ResourceKey<? extends Registry<?>> registry : REGISTRIES) {
            Path directory = directoryOf(projectRoot, registry);
            Codec<TagFile> codec = TagFile.codec(registry);

            FileSystemHelper.forEachFile(directory, file -> file.toString().endsWith(".json"), file -> {
                JsonObject json = FileSystemHelper.readJsonObject(file);
                if (json == null) return;

                String path = FileSystemHelper.stem(FileSystemHelper.relativeIdPath(directory, file));

                codec.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> AkytheraLabs.LOGGER.error("Failed at decoding {}: {}", file, error))
                    .ifPresent(tag -> projectTags.tagsOf(registry).put(path, tag));
            });
        }

        return projectTags;
    }

    /// Writes every tag, and deletes the files of the tags removed since the last save.
    public void save(final Path projectRoot) {
        removedPaths.forEach((registry, paths) -> paths.stream()
            .filter(path -> !tagsOf(registry).containsKey(path))
            .forEach(path -> FileSystemHelper.deleteRecursive(fileOf(projectRoot, registry, path))));
        removedPaths.clear();

        tags.forEach((registry, byPath) -> {
            Codec<TagFile> codec = TagFile.codec(registry);

            byPath.forEach((path, tag) -> FileSystemHelper.writeJson(fileOf(projectRoot, registry, path), codec, tag));
        });
    }

    /// The tags of `registry`, by path.
    public SortedMap<String, TagFile> tagsOf(final ResourceKey<? extends Registry<?>> registry) {
        return tags.computeIfAbsent(registry, _ -> new TreeMap<>());
    }

    /// The keys of the tags of `registry` in `namespace`.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<TagKey<?>> tagKeysOf(final ResourceKey<? extends Registry<?>> registry, final String namespace) {
        return tagsOf(registry).keySet().stream()
            .<TagKey<?>>map(path -> TagKey.create((ResourceKey) registry, Identifier.fromNamespaceAndPath(namespace, path)))
            .toList();
    }

    public void put(final ResourceKey<? extends Registry<?>> registry, final String path, final TagFile tag) {
        tagsOf(registry).put(path, tag);
    }

    public void remove(final ResourceKey<? extends Registry<?>> registry, final String path) {
        if (tagsOf(registry).remove(path) != null) removedPathsOf(registry).add(path);
    }

    /// Moves the tag `from` of `registry` to `to`. False when `to` is taken or `from` is missing.
    public boolean rename(final ResourceKey<? extends Registry<?>> registry, final String from, final String to) {
        SortedMap<String, TagFile> byPath = tagsOf(registry);

        if (byPath.containsKey(to) || !byPath.containsKey(from)) return false;

        byPath.put(to, byPath.remove(from));
        removedPathsOf(registry).add(from);

        return true;
    }

    /// Rewrites every value in namespace `from` to namespace `to`.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void remapNamespace(final String from, final String to) {
        tags.values().forEach(byPath -> byPath.replaceAll((_, tag) -> tag.withValues(tag.values().stream().<Value>map(value -> switch (value) {
            case Element(ResourceKey<?> id, boolean required) when id.identifier().getNamespace().equals(from) ->
                new Element(ResourceKey.create((ResourceKey) id.registryKey(), Identifier.fromNamespaceAndPath(to, id.identifier().getPath())), required);
            case Tag(TagKey<?> id, boolean required) when id.location().getNamespace().equals(from) ->
                new Tag(TagKey.create((ResourceKey) id.registry(), Identifier.fromNamespaceAndPath(to, id.location().getPath())), required);
            default -> value;
        }).toList())));
    }

    /// The tag files of every tag in vanilla's form, by their path under `data/<namespace>/`. Values not picked yet
    /// are left out.
    public Map<String, JsonElement> exportedTagFiles() {
        Map<String, JsonElement> files = new HashMap<>();

        tags.forEach((registry, byPath) -> {
            Codec<TagFile> codec = TagFile.codec(registry);

            byPath.forEach((path, tag) -> files.put(
                Registries.tagsDirPath(registry) + "/" + path + ".json",
                codec.encodeStart(JsonOps.INSTANCE, tag.withValues(tag.values().stream().filter(Value::picked).toList())).getOrThrow()
            ));
        });

        return files;
    }

    private Set<String> removedPathsOf(final ResourceKey<? extends Registry<?>> registry) {
        return removedPaths.computeIfAbsent(registry, _ -> new HashSet<>());
    }

    private static Path directoryOf(final Path projectRoot, final ResourceKey<? extends Registry<?>> registry) {
        return projectRoot.resolve("data").resolve(FileSystemHelper.toOsString(Registries.tagsDirPath(registry)));
    }

    private static Path fileOf(final Path projectRoot, final ResourceKey<? extends Registry<?>> registry, final String path) {
        return directoryOf(projectRoot, registry).resolve(FileSystemHelper.toOsString(path) + ".json");
    }

    /// A tag: the values it holds, and whether it replaces the same tag of the packs below.
    public record TagFile(List<Value> values, boolean replace) {
        public static final TagFile EMPTY = new TagFile(List.of(), false);

        public TagFile {
            values = Collections.unmodifiableList(values);
        }

        public static Codec<TagFile> codec(final ResourceKey<? extends Registry<?>> registry) {
            return RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(TagFile::replace),
                Value.codec(registry).listOf().fieldOf("values").forGetter(TagFile::values)
            ).apply(instance, (replace, values) -> new TagFile(values, replace)));
        }

        public TagFile withValues(final List<Value> values) {
            return new TagFile(values, replace);
        }

        public TagFile withReplace(final boolean replace) {
            return new TagFile(values, replace);
        }
    }

    /// One value of a tag: an entry of the registry, or another tag of it, written `#<id>`. A value not
    /// [#required] is skipped while what it names is missing, and is written as a `{"id", "required"}` object.
    public sealed interface Value permits Element, Tag {
        /// The [ResourceKey] of the entry, or the [TagKey] of the tag.
        Object id();

        boolean required();

        Value withRequired(boolean required);

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Codec<Value> codec(final ResourceKey<? extends Registry<?>> registry) {
            Codec<Value> idCodec = Codec.STRING.comapFlatMap(
                string -> string.startsWith("#")
                    ? Identifier.read(string.substring(1)).map(id -> new Tag(TagKey.create((ResourceKey) registry, id), true))
                    : Identifier.read(string).map(id -> new Element(ResourceKey.create((ResourceKey) registry, id), true)),
                value -> switch (value) {
                    case Element element -> element.id().identifier().toString();
                    case Tag tag -> "#" + tag.id().location();
                }
            );
            Codec<Value> objectCodec = RecordCodecBuilder.create(instance -> instance.group(
                idCodec.fieldOf("id").forGetter(value -> value),
                Codec.BOOL.optionalFieldOf("required", true).forGetter(Value::required)
            ).apply(instance, Value::withRequired));

            return Codec.either(idCodec, objectCodec).xmap(
                Either::unwrap,
                value -> value.required() ? Either.left(value) : Either.right(value)
            );
        }

        /// Whether this value names something, not [DataHolder#EMPTY_REFERENCE].
        default boolean picked() {
            Identifier id = switch (this) {
                case Element element -> element.id().identifier();
                case Tag tag -> tag.id().location();
            };

            return !id.equals(DataHolder.EMPTY_REFERENCE);
        }
    }

    public record Element(ResourceKey<?> id, boolean required) implements Value {
        @Override
        public Element withRequired(final boolean required) {
            return new Element(id, required);
        }
    }

    public record Tag(TagKey<?> id, boolean required) implements Value {
        @Override
        public Tag withRequired(final boolean required) {
            return new Tag(id, required);
        }
    }
}
