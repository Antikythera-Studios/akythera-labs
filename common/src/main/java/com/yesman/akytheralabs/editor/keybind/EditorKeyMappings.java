package com.yesman.akytheralabs.editor.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditorKeyMappings {
    private EditorKeyMappings() {}

    private static final Map<String, EditorKeyMapping> BY_ID = new LinkedHashMap<>();
    private static final List<EditorKeyMapping> ALL = new ArrayList<>();

    public static final EditorKeyMapping UNDO = reg("undo", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_UNDO, InputConstants.KEY_Z, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping REDO = reg("redo", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_REDO, InputConstants.KEY_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT);
    public static final EditorKeyMapping SELECT_ALL = reg("select_all", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_SELECT_ALL, InputConstants.KEY_A, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping COPY = reg("copy", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_COPY, InputConstants.KEY_C, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping CUT = reg("cut", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_CUT, InputConstants.KEY_X, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping PASTE = reg("paste", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_PASTE, InputConstants.KEY_V, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping DELETE = reg("delete", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_DELETE, InputConstants.KEY_DELETE, 0);
    public static final EditorKeyMapping SAVE = reg("save", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_SAVE, InputConstants.KEY_S, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping RENAME = reg("rename", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_RENAME, InputConstants.KEY_F2, 0);

    private static EditorKeyMapping reg(String id, String langKey, int key, int mods) {
        EditorKeyMapping m = new EditorKeyMapping(Component.translatable(langKey), key, mods);
        BY_ID.put(id, m);
        ALL.add(m);

        return m;
    }

    public static List<EditorKeyMapping> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Map<String, EditorKeyMapping> byId() {
        return Collections.unmodifiableMap(BY_ID);
    }

    public static void resetAll() {
        ALL.forEach(EditorKeyMapping::reset);
    }
}