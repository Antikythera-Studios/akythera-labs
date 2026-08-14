/// The editor's persisted configuration, held as `forgeconfigapiport` config specs.
///
/// The `neoforge` namespace this class imports from is NeoForge's own; `forgeconfigapiport` supplies
/// it on the non-Forge platforms.

package com.yesman.akytheralabs.config;

import com.yesman.akytheralabs.editor.keybind.EditorKeyMapping;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AkytheraLabsConfig {
    private AkytheraLabsConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // config specs
    public static final ModConfigSpec.IntValue EDITOR_GUI_SCALE = BUILDER
        .comment("A custom GUI scale adjustment for Akythera Labs editor independent from Minecraft's GuiScale")
        .defineInRange("gui.editor_gui_scale", 2, 1, 6);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> KEY_MAPPINGS = BUILDER
        .comment("Editor key mappings, each encoded as 'id=modBits,keyCode'")
        .defineListAllowEmpty("input.key_mappings", List.of(), () -> "", o -> o instanceof String s && s.indexOf('=') > 0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // config values
    private static int editorGuiScale;

    public static int getEditorGuiScale() {
        return editorGuiScale;
    }

    public static void setEditorGuiScale(int value) {
        editorGuiScale = value;
    }

    /// Loads the in-memory values from the config specs.
    public static void initConfigValues() {
        editorGuiScale = EDITOR_GUI_SCALE.getAsInt();
        applyKeyMappingsFromSpec();
    }

    /// Writes the in-memory values back into the config specs and saves them.
    public static void saveConfigValues() {
        setAndSave(EDITOR_GUI_SCALE, editorGuiScale);
        setAndSave(KEY_MAPPINGS, serializeKeyMappings());
    }

    private static List<String> serializeKeyMappings() {
        List<String> out = new ArrayList<>();

        for (Map.Entry<String, EditorKeyMapping> e : EditorKeyMappings.byId().entrySet()) {
            EditorKeyMapping m = e.getValue();
            out.add(e.getKey() + "=" + m.getModifiers() + "," + m.getKeyCode());
        }

        return out;
    }

    private static void applyKeyMappingsFromSpec() {
        EditorKeyMappings.resetAll();

        Map<String, EditorKeyMapping> byId = EditorKeyMappings.byId();

        for (String entry : KEY_MAPPINGS.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;

            String id = entry.substring(0, eq);
            EditorKeyMapping mapping = byId.get(id);
            if (mapping == null) continue;

            int comma = entry.indexOf(',', eq + 1);
            if (comma <= eq) continue;

            try {
                int mods = Integer.parseInt(entry.substring(eq + 1, comma));
                int key = Integer.parseInt(entry.substring(comma + 1));
                mapping.set(key, mods);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static <T> void setAndSave(ModConfigSpec.ConfigValue<T> configSpec, T value) {
        if (!Objects.equals(configSpec.get(), value)) {
            configSpec.set(value);
            configSpec.save();
        }
    }
}