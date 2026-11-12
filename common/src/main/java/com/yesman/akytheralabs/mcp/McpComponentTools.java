package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorFieldScanner;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

/// The [McpTool]s that read and write the Details rows of a component, on the top editor screen that lists components
/// (an entity patch, an item patch, a widget blueprint). They focus the component the way a click on its row does,
/// and write through [ComponentDetailsPanel#write], so a write claims an inherited component, records one undo step
/// and refreshes the previews as a typed value does.
final class McpComponentTools {
    private McpComponentTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_component_rows",
            "Without 'path', lists the component paths of the top editor screen (an entity patch, item patch or widget "
                + "blueprint), inherited ones included. With 'path', focuses that component like a click on its row and "
                + "returns 'declaration', the component's editor JSON without its children (the form of its entry's .json "
                + "file), and 'rows', its shown Details rows: 'row' (the key set_component_rows takes, nested rows joined "
                + "by '/'), 'label', 'type', 'value' and 'write'. 'write' is 'values' for a row set_component_rows takes "
                + "in 'values' (a quaternion reads as Euler XYZ degrees, as the editor shows it), and 'declaration' for a "
                + "row only written through the declaration. Hidden rows are left out.",
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "A component path, names joined by '/', e.g. 'armature/root_motion'."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ComponentEditorPanel components = McpTools.findWidgets(editorScreen(), ComponentEditorPanel.class).getFirst();

                if (!arguments.has("path")) {
                    JsonArray paths = new JsonArray();
                    components.rowPaths().forEach(paths::add);

                    return McpToolResult.text(McpTools.PRETTY_GSON.toJson(paths));
                }

                ComponentDataHolder<?> holder = focus(components, arguments.get("path").getAsString());
                JsonObject result = new JsonObject();
                JsonArray rows = new JsonArray();

                appendRows(rows, holder, "");
                result.add("declaration", Objects.requireNonNullElse(components.registry().declarationOf(holder), JsonNull.INSTANCE));
                result.add("rows", rows);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "set_component_rows",
            "Writes the component at 'path' on the top editor screen as one undo step, the same as typing into its "
                + "Details rows. 'declaration' takes keys of the declaration get_component_rows returns, each replacing "
                + "that key whole; every shown row whose value it changes is written, so any row type can be set this "
                + "way. 'values' maps a row key whose 'write' is 'values' to its JSON value: a number, a boolean, a "
                + "string, an enum constant name, [x, y, z] for a vector, [x, y, z] Euler degrees for a quaternion. "
                + "'values' is written after 'declaration' and wins on the same row. A row hidden now is written when "
                + "the call's other writes show it. A key another key needs, such as the sizes of a new shape, goes "
                + "in the same call. An inherited component becomes the patch's own override on the first write. The "
                + "entry stays unsaved; a project reload exports it anyway. Returns the declaration and rows after the "
                + "write; 'unwritten', the declaration keys that still differ from what was asked (a hidden row, or a "
                + "key no row edits); and 'dropped', the given declaration keys that changed nothing, left out of the "
                + "declaration before and after the write (a key the component does not read, or one already at its "
                + "default value).",
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "A component path, names joined by '/'."},
                "declaration": {"type": "object", "description": "Declaration keys to replace, e.g. {\\"transform\\": {...}}."},
                "values": {"type": "object", "description": "Row key to value, e.g. {\\"localTransform/rotation\\": [0, 180, 0]}."}
              },
              "required": ["path"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Screen screen = editorScreen();
                ComponentEditorPanel components = McpTools.findWidgets(screen, ComponentEditorPanel.class).getFirst();
                ComponentDetailsPanel details = McpTools.findWidgets(screen, ComponentDetailsPanel.class).getFirst();
                ComponentHolderRegistry registry = components.registry();
                String path = arguments.get("path").getAsString();
                ComponentDataHolder<?> holder = focus(components, path);
                SequencedMap<EditorWidgetFactory.ValueSlot, @Nullable Object> writes = new LinkedHashMap<>();
                JsonElement requested = null;

                if (!details.isShowing(holder)) throw new IllegalStateException("The Details panel does not show " + path);
                if (!(registry.declarationOf(holder) instanceof JsonObject declaration) || !(registry.copyOf(holder) instanceof ComponentDataHolder<?> trial)) {
                    throw new IllegalStateException(path + " has no declaration this editor reads");
                }

                if (arguments.has("declaration")) {
                    JsonObject merged = declaration.deepCopy();
                    arguments.getAsJsonObject("declaration").entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));

                    ComponentDataHolder<?> decoded = registry.codec().parse(JsonOps.INSTANCE, merged)
                        .getOrThrow(message -> new IllegalArgumentException("The declaration does not decode: " + message));

                    if (decoded.getClass() != holder.getClass()) throw new IllegalArgumentException("The declaration changes the component type");

                    // Again until a pass adds nothing, so the rows a written toggle shows are written too.
                    while (collectChanges(writes, registry, trial, holder, trial, decoded)) {}

                    requested = registry.declarationOf(decoded);
                }

                if (arguments.has("values")) {
                    Map<String, JsonElement> pending = new LinkedHashMap<>(arguments.getAsJsonObject("values").asMap());
                    boolean progress = true;

                    while (!pending.isEmpty() && progress) {
                        progress = false;

                        for (Iterator<Map.Entry<String, JsonElement>> it = pending.entrySet().iterator(); it.hasNext(); ) {
                            Map.Entry<String, JsonElement> entry = it.next();
                            EditorWidgetFactory.FieldSlot tried = slotAt(trial, entry.getKey(), true);

                            if (tried == null) continue;

                            Object value = decode(entry.getValue(), tried.type());

                            tried.set(value);
                            writes.put(slotAt(holder, entry.getKey(), false), value);
                            it.remove();
                            progress = true;
                        }
                    }

                    if (!pending.isEmpty()) throw new IllegalArgumentException("Hidden rows: " + pending.keySet());
                }

                details.write(writes);

                JsonElement written = registry.declarationOf(holder);
                JsonObject result = new JsonObject();
                JsonArray rows = new JsonArray();

                appendRows(rows, holder, "");
                result.add("declaration", Objects.requireNonNullElse(written, JsonNull.INSTANCE));
                result.add("rows", rows);

                if (requested instanceof JsonObject asked && written instanceof JsonObject now) {
                    JsonArray unwritten = new JsonArray();

                    asked.keySet().forEach(key -> {
                        if (!Objects.equals(asked.get(key), now.get(key))) unwritten.add(key);
                    });

                    if (!unwritten.isEmpty()) result.add("unwritten", unwritten);
                }

                if (arguments.has("declaration") && written instanceof JsonObject now) {
                    JsonArray dropped = new JsonArray();

                    arguments.getAsJsonObject("declaration").keySet().forEach(key -> {
                        if (!declaration.has(key) && !now.has(key)) dropped.add(key);
                    });

                    if (!dropped.isEmpty()) result.add("dropped", dropped);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));
    }

    private static Screen editorScreen() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (screen == null || McpTools.findWidgets(screen, ComponentEditorPanel.class).isEmpty()
            || McpTools.findWidgets(screen, ComponentDetailsPanel.class).isEmpty()
        ) {
            throw new IllegalStateException("The top editor screen lists no components");
        }

        return screen;
    }

    private static ComponentDataHolder<?> focus(final ComponentEditorPanel components, final String path) {
        ComponentDataHolder<?> holder = components.focusPath(path);

        if (holder == null) throw new IllegalArgumentException("No component at '" + path + "'");

        return holder;
    }

    /// Adds a row for every shown [EditorEditable] field of `owner`, and the rows of a nested editor object under its
    /// field name.
    private static void appendRows(final JsonArray rows, final Object owner, final String prefix) {
        for (EditorFieldScanner.EditorField editorField : EditorFieldScanner.fieldsOf(owner.getClass())) {
            if (!EditorWidgetFactory.isShown(editorField, owner)) continue;

            Class<?> type = editorField.field().getType();
            Object value = EditorWidgetFactory.tryGet(editorField.field(), owner);
            String key = prefix + editorField.field().getName();
            JsonElement encoded = encode(value, type);

            if (encoded == null && EditorWidgetFactory.isNestedObject(editorField, value)) {
                appendRows(rows, value, key + "/");
                continue;
            }

            JsonObject row = new JsonObject();
            row.addProperty("row", key);
            row.addProperty("label", Component.translatable(editorField.label()).getString());
            row.addProperty("type", type.getSimpleName());
            row.add("value", encoded != null ? encoded : preview(value));
            row.addProperty("write", isDecodable(type) ? "values" : "declaration");
            rows.add(row);
        }
    }

    /// Adds to `writes` the slot in `live` of every shown field of `owner` whose value in `decoded` changes the
    /// declaration of `trial`, going down into nested editor objects. True when a slot was added.
    ///
    /// `trial` is a detached copy of the component that takes every value tried, so its rows show as they will
    /// after the write. `owner` sits in `trial`, and `live` is its counterpart in the component.
    private static boolean collectChanges(
        final SequencedMap<EditorWidgetFactory.ValueSlot, @Nullable Object> writes,
        final ComponentHolderRegistry registry,
        final ComponentDataHolder<?> trial,
        final Object live,
        final Object owner,
        final Object decoded
    ) {
        boolean added = false;

        for (EditorFieldScanner.EditorField editorField : EditorFieldScanner.fieldsOf(owner.getClass())) {
            if (!EditorWidgetFactory.isShown(editorField, owner)) continue;

            Object now = EditorWidgetFactory.tryGet(editorField.field(), owner);
            Object next = EditorWidgetFactory.tryGet(editorField.field(), decoded);

            if (Objects.equals(now, next)) continue;

            Object liveNested = EditorWidgetFactory.tryGet(editorField.field(), live);

            if (EditorWidgetFactory.isNestedObject(editorField, now) && next != null && next.getClass() == now.getClass()
                && liveNested != null && liveNested.getClass() == now.getClass()
            ) {
                added |= collectChanges(writes, registry, trial, liveNested, now, next);
                continue;
            }

            JsonElement before = registry.declarationOf(trial);

            new EditorWidgetFactory.FieldSlot(editorField, owner).set(next);

            if (!Objects.equals(before, registry.declarationOf(trial))) {
                writes.put(new EditorWidgetFactory.FieldSlot(editorField, live), next);
                added = true;
            }
        }

        return added;
    }

    /// The slot of the row `key` under `holder`: field names joined by '/', down through nested editor objects. Null
    /// when `shownOnly` and the row or an object above it is hidden.
    private static EditorWidgetFactory.@Nullable FieldSlot slotAt(final Object holder, final String key, final boolean shownOnly) {
        Object owner = holder;
        String[] names = key.split("/");

        for (int i = 0; i < names.length; i++) {
            EditorFieldScanner.EditorField editorField = fieldNamed(owner, names[i], key);

            if (shownOnly && !EditorWidgetFactory.isShown(editorField, owner)) return null;

            if (i == names.length - 1) {
                if (!isDecodable(editorField.field().getType())) {
                    throw new IllegalArgumentException("Row '" + key + "' is written through 'declaration'");
                }

                return new EditorWidgetFactory.FieldSlot(editorField, owner);
            }

            Object nested = EditorWidgetFactory.tryGet(editorField.field(), owner);

            if (!EditorWidgetFactory.isNestedObject(editorField, nested)) throw new IllegalArgumentException("No row '" + key + "'");

            owner = nested;
        }

        throw new IllegalArgumentException("Empty row key");
    }

    private static EditorFieldScanner.EditorField fieldNamed(final Object owner, final String name, final String key) {
        for (EditorFieldScanner.EditorField editorField : EditorFieldScanner.fieldsOf(owner.getClass())) {
            if (editorField.field().getName().equals(name)) return editorField;
        }

        throw new IllegalArgumentException("No row '" + key + "'");
    }

    // ***************************************************************
    // Values
    // ***************************************************************

    /// `value` as the JSON `values` takes, or null for a type written through the declaration.
    private static @Nullable JsonElement encode(final @Nullable Object value, final Class<?> type) {
        if (!isDecodable(type)) return null;

        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Boolean bool -> new JsonPrimitive(bool);
            case Number number -> new JsonPrimitive(number);
            case String string -> new JsonPrimitive(string);
            case Identifier identifier -> new JsonPrimitive(identifier.toString());
            case Enum<?> constant -> new JsonPrimitive(constant.name());
            case Vec3 vec -> vector(vec.x, vec.y, vec.z);
            case Quaterniond quaternion -> {
                Vector3d degrees = QuaternionUtils.toEulerDegreesXYZ(quaternion);
                yield vector(degrees.x, degrees.y, degrees.z);
            }
            default -> null;
        };
    }

    /// `value` as JSON to read: a list by its elements, an editor object by its shown fields, anything else as `values`
    /// takes it or by its text.
    private static JsonElement preview(final @Nullable Object value) {
        if (value instanceof List<?> list) {
            JsonArray elements = new JsonArray();
            list.forEach(element -> elements.add(preview(element)));

            return elements;
        }

        if (value != null && !EditorFieldScanner.fieldsOf(value.getClass()).isEmpty()) {
            JsonObject fields = new JsonObject();

            for (EditorFieldScanner.EditorField editorField : EditorFieldScanner.fieldsOf(value.getClass())) {
                if (EditorWidgetFactory.isShown(editorField, value)) {
                    fields.add(editorField.field().getName(), preview(EditorWidgetFactory.tryGet(editorField.field(), value)));
                }
            }

            return fields;
        }

        JsonElement encoded = value == null ? null : encode(value, value.getClass());

        return encoded != null ? encoded : value == null ? JsonNull.INSTANCE : new JsonPrimitive(String.valueOf(value));
    }

    private static boolean isDecodable(final Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class || type == String.class
            || type == Identifier.class || type.isEnum() || type == Vec3.class || type == Quaterniond.class;
    }

    /// `json` as a value of `type`.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable Object decode(final JsonElement json, final Class<?> type) {
        if (json.isJsonNull()) {
            if (type.isPrimitive()) throw new IllegalArgumentException("A " + type.getSimpleName() + " row takes no null");
            return null;
        }

        if (type == boolean.class || type == Boolean.class) return json.getAsBoolean();
        if (type == int.class || type == Integer.class) return json.getAsInt();
        if (type == long.class || type == Long.class) return json.getAsLong();
        if (type == short.class || type == Short.class) return json.getAsShort();
        if (type == byte.class || type == Byte.class) return json.getAsByte();
        if (type == float.class || type == Float.class) return json.getAsFloat();
        if (type == double.class || type == Double.class) return json.getAsDouble();
        if (type == String.class) return json.getAsString();
        if (type == Identifier.class) return McpTools.requireId(json.getAsString());

        if (type.isEnum()) {
            String name = json.getAsString();

            for (Object constant : type.getEnumConstants()) {
                Enum<?> value = (Enum) constant;

                if (value.name().equalsIgnoreCase(name)) return value;
                if (value instanceof StringRepresentable representable && representable.getSerializedName().equals(name)) return value;
            }

            throw new IllegalArgumentException("No " + type.getSimpleName() + " constant '" + name + "'");
        }

        JsonArray array = json.getAsJsonArray();

        if (array.size() != 3) throw new IllegalArgumentException("A " + type.getSimpleName() + " row takes [x, y, z]");

        double x = array.get(0).getAsDouble();
        double y = array.get(1).getAsDouble();
        double z = array.get(2).getAsDouble();

        return type == Vec3.class ? new Vec3(x, y, z) : QuaternionUtils.fromEulerDegreesXYZ(new Vector3d(x, y, z));
    }

    private static JsonArray vector(final double x, final double y, final double z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);

        return array;
    }
}
