package com.yesman.akytheralabs.editor.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
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

    // Common editor keys
    public static final EditorKeyMapping UNDO = reg("undo", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_UNDO, InputConstants.KEY_Z, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping REDO = reg("redo", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_REDO, InputConstants.KEY_Z, InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT);
    public static final EditorKeyMapping SELECT_ALL = reg("select_all", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_SELECT_ALL, InputConstants.KEY_A, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping COPY = reg("copy", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_COPY, InputConstants.KEY_C, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping CUT = reg("cut", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_CUT, InputConstants.KEY_X, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping PASTE = reg("paste", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_PASTE, InputConstants.KEY_V, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping DELETE = reg("delete", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_DELETE, InputConstants.KEY_DELETE, 0);
    public static final EditorKeyMapping SAVE = reg("save", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_SAVE, InputConstants.KEY_S, InputConstants.MOD_CONTROL);
    public static final EditorKeyMapping RENAME = reg("rename", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_RENAME, InputConstants.KEY_F2, 0);

    // Gives the pointer back once the world viewport has grabbed it. Handled straight from
    // `KeyboardHandler#keyPress` (see [AkytheraEditorLayoutManager#tryUngrabMouse]),
    // because a grabbed pointer stops the editor screens from receiving key presses at all.
    public static final EditorKeyMapping UNGRAB = reg("ungrab", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_UNGRAB, InputConstants.KEY_F1, InputConstants.MOD_SHIFT);

    // Dumps the possessed entity's next recorded animator-script run to the log.
    public static final EditorKeyMapping PRINT_SCRIPT_LOG = reg("print_script_log", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_PRINT_SCRIPT_LOG, InputConstants.KEY_L, InputConstants.MOD_CONTROL);

    // Drive the gizmo without the handles: start a transform at the cursor, or flip the handle
    // basis. Unmodified, so they only fire while the cursor is over the 3D viewer — `Tab` included,
    // which the editor otherwise navigates focus with.
    public static final EditorKeyMapping GIZMO_TRANSLATE = reg("gizmo_translate", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_TRANSLATE, InputConstants.KEY_G, 0);
    public static final EditorKeyMapping GIZMO_ROTATE = reg("gizmo_rotate", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_ROTATE, InputConstants.KEY_R, 0);
    public static final EditorKeyMapping GIZMO_SCALE = reg("gizmo_scale", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_SCALE, InputConstants.KEY_S, 0);
    public static final EditorKeyMapping GIZMO_ORIENTATION = reg("gizmo_orientation", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_ORIENTATION, InputConstants.KEY_TAB, 0);

    // Limit a running gizmo transform to one axis. Only read while such a transform is in flight.

    public static final EditorKeyMapping GIZMO_AXIS_X = reg("gizmo_axis_x", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_AXIS_X, InputConstants.KEY_X, 0);
    public static final EditorKeyMapping GIZMO_AXIS_Y = reg("gizmo_axis_y", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_AXIS_Y, InputConstants.KEY_Y, 0);
    public static final EditorKeyMapping GIZMO_AXIS_Z = reg("gizmo_axis_z", LangKeys.GUI_EDITOR_PREFERENCES_KEY_MAPPING_GIZMO_AXIS_Z, InputConstants.KEY_Z, 0);

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