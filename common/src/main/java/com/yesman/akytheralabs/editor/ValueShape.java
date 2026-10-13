package com.yesman.akytheralabs.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// How the editor holds and edits a value no field carries, such as a blueprint property: a leaf a widget
/// producer edits, a registry entry held as its [ResourceKey], a list, set or map held as a list, or a record
/// held as a [RecordValue] of its components.
///
/// The editor value is never the live object. A registry entry cannot be resolved in the editor, and a
/// record may normalize or refuse what it is built from, so the live object is built only to encode it.
public sealed interface ValueShape {
    /// The class a widget producer is found by.
    Class<?> type();

    /// The value of a property left unset. Writing it back removes the property.
    @Nullable Object unset();

    /// The value a new element of a sequence starts at.
    @Nullable Object blank();

    /// The editor value of a live `value`.
    @Nullable Object toEditor(@Nullable Object value);

    /// The live value `value` stands for.
    ///
    /// @throws IllegalStateException when `value` is not complete enough to build one
    @Nullable Object fromEditor(@Nullable Object value);

    /// `value` in its codec's JSON form.
    ///
    /// @throws IllegalStateException when `value` does not encode, or this shape has no codec
    JsonElement toJson(@Nullable Object value);

    /// The editor value `json` decodes to.
    ///
    /// @throws IllegalStateException when `json` does not decode, or this shape has no codec
    @Nullable Object fromJson(JsonElement json);

    /// Whether [#toJson] can succeed at all.
    boolean encodable();

    /// A value edited by a widget producer as it is.
    ///
    /// @param codec what [#toJson] and [#fromJson] go through; null inside a record, which encodes whole
    record Leaf(Class<?> type, @Nullable Codec<?> codec) implements ValueShape {
        @Override
        public @Nullable Object unset() {
            return null;
        }

        /// The first enum constant or data block, zero, false, an empty string, the zero vector or an empty
        /// tag set. Null for any other type, and for a data block type with no entry yet.
        @Override
        public @Nullable Object blank() {
            if (type.isEnum()) return type.getEnumConstants()[0];
            if (type == Boolean.class || type == boolean.class) return false;
            if (type == Byte.class || type == byte.class) return (byte) 0;
            if (type == Short.class || type == short.class) return (short) 0;
            if (type == Integer.class || type == int.class) return 0;
            if (type == Long.class || type == long.class) return 0L;
            if (type == Float.class || type == float.class) return 0.0F;
            if (type == Double.class || type == double.class) return 0.0D;
            if (type == String.class) return "";
            if (type == Vec3.class) return Vec3.ZERO;
            if (type == AbilityTagSet.class) return AbilityTagSet.EMPTY;

            if (EnumerableDataBlock.class.isAssignableFrom(type)) {
                EnumerableDataBlockManager<?> manager = DataBlockRegistries.managerOf(type);
                if (manager == null) return null;

                AkytheraProject project = AkytheraProject.getCurrentProject();

                for (Identifier id : project.getMergedDataBlockIdentifiers(manager)) {
                    Holder<? extends EnumerableDataBlock> holder = project.getDataBlockHolder(manager, id);
                    if (holder != null && holder.isBound()) return holder.value();
                }
            }

            return null;
        }

        @Override
        public @Nullable Object toEditor(@Nullable Object value) {
            return value;
        }

        @Override
        public @Nullable Object fromEditor(@Nullable Object value) {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonElement toJson(@Nullable Object value) {
            if (codec == null) throw new IllegalStateException(type.getName() + " has no codec");

            return ((Codec<Object>) codec).encodeStart(JsonOps.INSTANCE, value).getOrThrow(IllegalStateException::new);
        }

        /// A data block is resolved by its id against the current project merged with its manager, so a block
        /// only the project declares decodes too. Anything else goes through [#codec].
        @Override
        public @Nullable Object fromJson(JsonElement json) {
            if (codec == null) throw new IllegalStateException(type.getName() + " has no codec");

            EnumerableDataBlockManager<?> manager = EnumerableDataBlock.class.isAssignableFrom(type)
                ? DataBlockRegistries.managerOf(type)
                : null;

            if (manager == null) return codec.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);

            Identifier id = Identifier.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
            Holder<? extends EnumerableDataBlock> holder = AkytheraProject.getCurrentProject().getDataBlockHolder(manager, id);

            if (holder == null || !holder.isBound()) {
                throw new IllegalStateException("No " + manager.getEnumType() + " " + id);
            }

            return holder.value();
        }

        @Override
        public boolean encodable() {
            return codec != null;
        }
    }

    /// An entry of `registry`, held and written as its id. [DataHolder#EMPTY_REFERENCE] is nothing picked.
    record RegistryLeaf(ResourceKey<? extends Registry<?>> registry) implements ValueShape {
        @Override
        public Class<?> type() {
            return ResourceKey.class;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Object unset() {
            return ResourceKey.create((ResourceKey) registry, DataHolder.EMPTY_REFERENCE);
        }

        @Override
        public Object blank() {
            return unset();
        }

        @Override
        public @Nullable Object toEditor(@Nullable Object value) {
            throw new IllegalStateException("A registry entry is edited by its id only");
        }

        @Override
        public @Nullable Object fromEditor(@Nullable Object value) {
            throw new IllegalStateException("A registry entry is edited by its id only");
        }

        @Override
        public JsonElement toJson(@Nullable Object value) {
            if (!(value instanceof ResourceKey<?> key) || key.identifier().equals(DataHolder.EMPTY_REFERENCE)) {
                throw new IllegalStateException("No " + registry.identifier() + " entry is picked");
            }

            return new JsonPrimitive(key.identifier().toString());
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Object fromJson(JsonElement json) {
            return ResourceKey.create((ResourceKey) registry, Identifier.parse(json.getAsString()));
        }

        @Override
        public boolean encodable() {
            return true;
        }
    }

    /// A list, set or map, held as a list of `element`s in its order. A set or map is built from it only to
    /// encode. A map's element is a [RecordShape] of [MapEntry], written as a `key` / `value` object.
    ///
    /// @param container [ScriptType.Container#LIST], [ScriptType.Container#SET] or [ScriptType.Container#MAP]
    record Sequence(ValueShape element, ScriptType.Container container) implements ValueShape {
        @Override
        public Class<?> type() {
            return switch (container) {
                case SET -> Set.class;
                case MAP -> Map.class;
                default -> List.class;
            };
        }

        @Override
        public Object unset() {
            return List.of();
        }

        @Override
        public Object blank() {
            return List.of();
        }

        @Override
        public Object toEditor(@Nullable Object value) {
            List<@Nullable Object> items = new ArrayList<>();

            if (value instanceof Map<?, ?> map) {
                map.forEach((key, entryValue) -> items.add(element.toEditor(new MapEntry(key, entryValue))));
            } else if (value != null) {
                for (Object item : (Collection<?>) value) items.add(element.toEditor(item));
            }

            return Collections.unmodifiableList(items);
        }

        @Override
        public Object fromEditor(@Nullable Object value) {
            List<@Nullable Object> items = new ArrayList<>();

            for (Object item : (List<?>) value) items.add(element.fromEditor(item));

            try {
                return switch (container) {
                    case SET -> Set.copyOf(items);
                    case MAP -> {
                        Map<Object, Object> map = new LinkedHashMap<>();

                        for (Object item : items) {
                            MapEntry entry = (MapEntry) item;
                            map.put(Objects.requireNonNull(entry.key()), Objects.requireNonNull(entry.value()));
                        }

                        yield Collections.unmodifiableMap(map);
                    }
                    default -> List.copyOf(items);
                };
            } catch (NullPointerException e) {
                throw new IllegalStateException("An element is unset", e);
            }
        }

        @Override
        public JsonElement toJson(@Nullable Object value) {
            JsonArray array = new JsonArray();

            for (Object item : (List<?>) value) {
                if (container != ScriptType.Container.MAP) {
                    array.add(element.toJson(item));
                    continue;
                }

                List<ValueShape> shapes = ((RecordShape) element).shapes();
                List<@Nullable Object> components = ((RecordValue) item).components();
                JsonObject entry = new JsonObject();

                entry.add("key", shapes.get(0).toJson(components.get(0)));
                entry.add("value", shapes.get(1).toJson(components.get(1)));
                array.add(entry);
            }

            return array;
        }

        @Override
        public Object fromJson(JsonElement json) {
            List<@Nullable Object> items = new ArrayList<>();

            for (JsonElement item : json.getAsJsonArray()) {
                if (container != ScriptType.Container.MAP) {
                    items.add(element.fromJson(item));
                    continue;
                }

                List<ValueShape> shapes = ((RecordShape) element).shapes();
                JsonObject entry = item.getAsJsonObject();

                items.add(new RecordValue(Arrays.asList(
                    shapes.get(0).fromJson(entry.get("key")),
                    shapes.get(1).fromJson(entry.get("value"))
                )));
            }

            return Collections.unmodifiableList(items);
        }

        @Override
        public boolean encodable() {
            return element.encodable();
        }
    }

    /// A record, held as a [RecordValue] of its components in declaration order.
    ///
    /// @param shapes one per component
    /// @param codec  what [#toJson] and [#fromJson] go through; null inside another record
    record RecordShape(Class<?> type, List<ValueShape> shapes, @Nullable Codec<?> codec) implements ValueShape {
        @Override
        public Object unset() {
            return new RecordValue(shapes.stream().map(ValueShape::unset).toList());
        }

        @Override
        public Object blank() {
            return new RecordValue(shapes.stream().map(ValueShape::blank).toList());
        }

        @Override
        public Object toEditor(@Nullable Object value) {
            RecordComponent[] components = type.getRecordComponents();
            List<@Nullable Object> values = new ArrayList<>();

            for (int i = 0; i < components.length; i++) {
                try {
                    Method accessor = components[i].getAccessor();
                    accessor.setAccessible(true);

                    values.add(shapes.get(i).toEditor(accessor.invoke(value)));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Unreadable " + type.getName() + "#" + components[i].getName(), e);
                }
            }

            return new RecordValue(values);
        }

        @Override
        public Object fromEditor(@Nullable Object value) {
            RecordComponent[] components = type.getRecordComponents();
            Object[] arguments = new Object[components.length];

            for (int i = 0; i < components.length; i++) {
                arguments[i] = shapes.get(i).fromEditor(((RecordValue) value).components().get(i));
            }

            try {
                Constructor<?> constructor = type.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
                constructor.setAccessible(true);

                return constructor.newInstance(arguments);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(type.getSimpleName() + " refuses its components", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No canonical constructor on " + type.getName(), e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonElement toJson(@Nullable Object value) {
            if (codec == null) throw new IllegalStateException(type.getName() + " has no codec");

            return ((Codec<Object>) codec).encodeStart(JsonOps.INSTANCE, fromEditor(value))
                .getOrThrow(IllegalStateException::new);
        }

        @Override
        public Object fromJson(JsonElement json) {
            if (codec == null) throw new IllegalStateException(type.getName() + " has no codec");

            return toEditor(codec.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new));
        }

        @Override
        public boolean encodable() {
            return codec != null;
        }
    }

    /// A record's components as the editor holds them.
    record RecordValue(List<@Nullable Object> components) {
        public RecordValue {
            components = Collections.unmodifiableList(new ArrayList<>(components));
        }

        /// This value with component `index` replaced by `value`.
        public RecordValue with(int index, @Nullable Object value) {
            List<@Nullable Object> next = new ArrayList<>(components);
            next.set(index, value);

            return new RecordValue(next);
        }
    }

    /// One entry of a map a [Sequence] holds.
    record MapEntry(@Nullable Object key, @Nullable Object value) {
    }

    /// The shape of a value of `type`: a registry entry, or a holder or key of one, by its id, a list or set
    /// by its element's shape, a map by its key's and value's shapes, and anything else by its class, encoded
    /// through `type`'s codec.
    static ValueShape of(ScriptType type) {
        ScriptType element = type.element();

        if (type.registry() != null) return new RegistryLeaf(type.registry());
        if (element != null && (type.type() == Holder.class || type.type() == ResourceKey.class) && element.registry() != null) {
            return new RegistryLeaf(element.registry());
        }
        if (type.container() == ScriptType.Container.MAP) {
            return new Sequence(
                new RecordShape(MapEntry.class, List.of(of(Objects.requireNonNull(type.key())), of(element)), null),
                ScriptType.Container.MAP
            );
        }
        if (element != null && type.container() != ScriptType.Container.SINGLE) {
            return new Sequence(of(element), type.container());
        }

        return switch (of((Type) type.type())) {
            case Leaf leaf -> new Leaf(leaf.type(), type.codec());
            case RecordShape record -> new RecordShape(record.type(), record.shapes(), type.codec());
            case ValueShape other -> other;
        };
    }

    /// The shape of a value declared as `type` inside a record: a list or set by its element's shape, a
    /// record by its components' shapes, and anything else by its class. None of it has a codec of its own.
    static ValueShape of(Type type) {
        if (type instanceof ParameterizedType parameterized
            && parameterized.getActualTypeArguments().length == 1
            && (parameterized.getRawType() == List.class || parameterized.getRawType() == Set.class)
        ) {
            return new Sequence(
                of(parameterized.getActualTypeArguments()[0]),
                parameterized.getRawType() == Set.class ? ScriptType.Container.SET : ScriptType.Container.LIST
            );
        }

        Class<?> raw = type instanceof ParameterizedType parameterized
            ? (Class<?>) parameterized.getRawType()
            : type instanceof Class<?> c ? c : Object.class;

        if (raw.isRecord()) {
            return new RecordShape(
                raw, Arrays.stream(raw.getRecordComponents()).map(c -> of(c.getGenericType())).toList(), null);
        }

        return new Leaf(raw, null);
    }
}
