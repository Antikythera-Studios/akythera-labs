package com.yesman.akytheralabs.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.widget.WidgetImageRef;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.ProjectObjectHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// A value stored in its type's codec JSON form, holding the shared live instance of every project entry, data
/// block and texture it names, so [#json] writes their current ids after a rename.
///
/// Read by [ValueShape]: a registry entry or a data block is an [Entry], a [WidgetImage] an [Image], a list, set or
/// map is walked element by element, and anything else stays [Plain].
public sealed interface StoredJson {
    /// The value as it is written now.
    JsonElement json();

    /// The shared rename-tracking instances this value names.
    Stream<EditorReference> referencedReferences();

    /// The ids this value names that resolve to nothing.
    Stream<Identifier> missingReferences();

    /// `json` read as a value of `type`. A type with no codec stays plain.
    static StoredJson of(final ScriptType type, final JsonElement json) {
        return type.codec() == null ? new Plain(json) : of(ValueShape.of(type), json);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static StoredJson of(final ValueShape shape, final JsonElement json) {
        switch (shape) {
            case ValueShape.RegistryLeaf(var registry) when idOf(json) != null -> {
                return entry(EditorHolderCodecs.resolve((ResourceKey) registry, idOf(json)), json);
            }
            case ValueShape.Leaf leaf when idOf(json) != null && EnumerableDataBlock.class.isAssignableFrom(leaf.type()) -> {
                EnumerableDataBlockManager<?> manager = DataBlockRegistries.managerOf(leaf.type());

                return manager == null
                    ? new Plain(json)
                    : entry(AkytheraProject.getCurrentProject().getDataBlockHolderOrDangling(manager, idOf(json)), json);
            }
            case ValueShape imageShape when imageShape.type() == WidgetImage.class -> {
                return WidgetImage.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .filter(image -> image.texture() != null)
                    .<StoredJson>map(image -> new Image(WidgetImageRef.of(
                        image, !image.texture().getPath().startsWith(AkytheraProject.TEXTURES_DIRECTORY))))
                    .orElseGet(() -> new Plain(json));
            }
            case ValueShape.Sequence sequence when json.isJsonArray() -> {
                List<StoredJson> items = json.getAsJsonArray().asList().stream()
                    .map(item -> sequence.container() == ScriptType.Container.MAP
                        ? mapEntry(((ValueShape.RecordShape) sequence.element()).shapes(), item)
                        : of(sequence.element(), item))
                    .toList();

                return items.stream().allMatch(Plain.class::isInstance) ? new Plain(json) : new Array(items);
            }
            default -> {
                return new Plain(json);
            }
        }
    }

    /// The id `json` holds when it is a string that parses as one.
    private static @Nullable Identifier idOf(final JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() ? Identifier.tryParse(json.getAsString()) : null;
    }

    /// An [Entry] when `holder` is a project entry, which alone can be renamed.
    private static StoredJson entry(final Holder<?> holder, final JsonElement json) {
        return holder instanceof ProjectObjectHolder<?> projectHolder ? new Entry(projectHolder) : new Plain(json);
    }

    /// A map entry written as a `key` / `value` object, read by `shapes`, the key's and the value's.
    private static StoredJson mapEntry(final List<ValueShape> shapes, final JsonElement item) {
        if (!item.isJsonObject() || !item.getAsJsonObject().has("key") || !item.getAsJsonObject().has("value")) return new Plain(item);

        Map<String, StoredJson> members = new LinkedHashMap<>();
        members.put("key", of(shapes.get(0), item.getAsJsonObject().get("key")));
        members.put("value", of(shapes.get(1), item.getAsJsonObject().get("value")));

        return new Members(members);
    }

    /// A value naming nothing that can be renamed.
    record Plain(JsonElement json) implements StoredJson {
        @Override
        public Stream<EditorReference> referencedReferences() {
            return Stream.empty();
        }

        @Override
        public Stream<Identifier> missingReferences() {
            return Stream.empty();
        }
    }

    /// A project registry entry or data block, written as its current id.
    record Entry(ProjectObjectHolder<?> holder) implements StoredJson {
        @Override
        public JsonElement json() {
            return new JsonPrimitive(holder.key().identifier().toString());
        }

        @Override
        public Stream<EditorReference> referencedReferences() {
            return Stream.of(holder);
        }

        @Override
        public Stream<Identifier> missingReferences() {
            return holder.isBound() ? Stream.empty() : Stream.of(holder.key().identifier());
        }
    }

    /// A [WidgetImage] naming a texture. A texture under `textures/` is named by its full path, any other by its
    /// GUI sprite id.
    record Image(WidgetImageRef image) implements StoredJson {
        @Override
        public JsonElement json() {
            return WidgetImage.CODEC.encodeStart(JsonOps.INSTANCE, image.makeObject()).getOrThrow(IllegalStateException::new);
        }

        @Override
        public Stream<EditorReference> referencedReferences() {
            return image.referencedReferences();
        }

        @Override
        public Stream<Identifier> missingReferences() {
            return Stream.empty();
        }
    }

    /// The elements of a list, set or map, in order.
    record Array(List<StoredJson> items) implements StoredJson {
        @Override
        public JsonElement json() {
            JsonArray array = new JsonArray();
            items.forEach(item -> array.add(item.json()));

            return array;
        }

        @Override
        public Stream<EditorReference> referencedReferences() {
            return items.stream().flatMap(StoredJson::referencedReferences);
        }

        @Override
        public Stream<Identifier> missingReferences() {
            return items.stream().flatMap(StoredJson::missingReferences);
        }
    }

    /// An object's members, by name.
    record Members(Map<String, StoredJson> members) implements StoredJson {
        @Override
        public JsonElement json() {
            JsonObject object = new JsonObject();
            members.forEach((name, member) -> object.add(name, member.json()));

            return object;
        }

        @Override
        public Stream<EditorReference> referencedReferences() {
            return members.values().stream().flatMap(StoredJson::referencedReferences);
        }

        @Override
        public Stream<Identifier> missingReferences() {
            return members.values().stream().flatMap(StoredJson::missingReferences);
        }
    }
}
