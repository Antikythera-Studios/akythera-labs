package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMapping;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.script.WireStyle;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// The [McpTool]s doing what the Language and Editor Preferences modals of the top menu do.
final class McpPreferenceTools {
    private McpPreferenceTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_editor_preferences",
            "Returns what the Language and Editor Preferences modals edit: the game 'language' and the codes it offers, "
                + "the editor 'guiScale', the node graph 'wireStyle' and the styles offered, and the editor 'keyMappings', "
                + "each by id with its GLFW 'key' code, its 'modifiers' bit mask and the keys it reads as.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> McpTools.onClient(() -> {
                JsonObject result = new JsonObject();
                result.addProperty("language", Minecraft.getInstance().getLanguageManager().getSelected());
                result.add("languages", McpTools.PRETTY_GSON.toJsonTree(Minecraft.getInstance().getLanguageManager().getLanguages().keySet().stream().sorted().toList()));
                result.addProperty("guiScale", AkytheraLabsConfig.getEditorGuiScale());
                result.addProperty("wireStyle", AkytheraLabsConfig.getWireStyle().name().toLowerCase(Locale.ROOT));
                result.add("wireStyles", McpTools.PRETTY_GSON.toJsonTree(Arrays.stream(WireStyle.values()).map(style -> style.name().toLowerCase(Locale.ROOT)).toList()));

                JsonObject keyMappings = new JsonObject();

                EditorKeyMappings.byId().forEach((id, mapping) -> {
                    JsonObject mappingJson = new JsonObject();
                    mappingJson.addProperty("key", mapping.getKeyCode());
                    mappingJson.addProperty("modifiers", mapping.getModifiers());
                    mappingJson.addProperty("reads", mapping.getDisplayName().getString());
                    keyMappings.add(id, mappingJson);
                });

                result.add("keyMappings", keyMappings);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "set_editor_preferences",
            "Changes what the Language and Editor Preferences modals edit, and saves it, as their Apply and Save do. A "
                + "key mapping set to null goes back to its default; 'resetKeyMappings' resets every one first. A new "
                + "'language' reloads the resource packs. Nothing is written when one value is invalid.",
            """
            {
              "type": "object",
              "properties": {
                "language": {"type": "string", "description": "A language code, e.g. 'en_us'."},
                "guiScale": {"type": "integer"},
                "wireStyle": {"type": "string"},
                "keyMappings": {"type": "object", "additionalProperties": {"type": ["object", "null"], "properties": {"key": {"type": "integer"}, "modifiers": {"type": "integer"}}}},
                "resetKeyMappings": {"type": "boolean", "description": "Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                List<Runnable> writes = new ArrayList<>();

                if (arguments.has("resetKeyMappings") && arguments.get("resetKeyMappings").getAsBoolean()) writes.add(EditorKeyMappings::resetAll);

                if (arguments.has("guiScale")) {
                    int scale = arguments.get("guiScale").getAsInt();

                    if (scale < EditorPreferencesModalGenerator.MIN_SCALE || scale > EditorPreferencesModalGenerator.MAX_SCALE) {
                        throw new IllegalArgumentException("The GUI scale goes from " + EditorPreferencesModalGenerator.MIN_SCALE + " to " + EditorPreferencesModalGenerator.MAX_SCALE);
                    }

                    writes.add(() -> {
                        AkytheraLabsConfig.setEditorGuiScale(scale);
                        AkytheraEditorLayoutManager.getInstance().rescaleScreens(scale);
                    });
                }

                if (arguments.has("wireStyle")) {
                    WireStyle style = WireStyle.valueOf(arguments.get("wireStyle").getAsString().toUpperCase(Locale.ROOT));
                    writes.add(() -> AkytheraLabsConfig.setWireStyle(style));
                }

                if (arguments.has("keyMappings")) {
                    for (Map.Entry<String, JsonElement> entry : arguments.getAsJsonObject("keyMappings").entrySet()) {
                        EditorKeyMapping mapping = EditorKeyMappings.byId().get(entry.getKey());

                        if (mapping == null) throw new IllegalArgumentException("No editor key mapping " + entry.getKey());

                        if (entry.getValue().isJsonNull()) {
                            writes.add(mapping::reset);
                        } else {
                            JsonObject keys = entry.getValue().getAsJsonObject();
                            int key = keys.get("key").getAsInt();
                            int modifiers = keys.has("modifiers") ? keys.get("modifiers").getAsInt() : 0;
                            writes.add(() -> mapping.set(key, modifiers));
                        }
                    }
                }

                String language = arguments.has("language") ? arguments.get("language").getAsString() : null;

                if (language != null && Minecraft.getInstance().getLanguageManager().getLanguage(language) == null) {
                    throw new IllegalArgumentException("Not a language the game offers: " + language);
                }

                writes.forEach(Runnable::run);
                AkytheraLabsConfig.saveConfigValues();

                if (language != null) LanguageSelectionModal.applyLanguage(language);

                return McpToolResult.text("Saved the editor preferences" + (language == null ? "" : "; the resource packs reload for the language"));
            })
        ));
    }
}
