package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;

/// Every option the top menu can offer. [TopMenuBar] lays all of them out the same way on every
/// screen; a screen wires up the ones it handles through [TopMenuBar.Actions] and the rest render
/// greyed out.
public enum TopMenuOption {
    SAVE(LangKeys.GUI_MENU_BAR_FILE_SAVE),
    COMPILE(LangKeys.GUI_MENU_BAR_FILE_COMPILE),
    EXPORT_AS_PACK(LangKeys.GUI_MENU_BAR_FILE_EXPORT_AS_PACK),
    EXPORT_AS_SOURCE_CODE(LangKeys.GUI_MENU_BAR_FILE_EXPORT_AS_SOURCE_CODE),
    CHANGE_PARENT(LangKeys.GUI_MENU_BAR_FILE_CHANGE_PARENT),
    DELETE_PROJECT(LangKeys.GUI_MENU_BAR_FILE_DELETE_PROJECT),

    LANGUAGE(LangKeys.GUI_MENU_BAR_EDIT_LANGUAGE),
    PROJECT_SETTINGS(LangKeys.GUI_MENU_BAR_EDIT_PROJECT_SETTINGS),
    EDITOR_PREFERENCES(LangKeys.GUI_MENU_BAR_EDIT_EDITOR_PREFERENCES),
    MODEL_ARMATURE_MAPPING(LangKeys.GUI_MENU_BAR_EDIT_MODEL_ARMATURE_MAPPING),

    SELECT_PREVIEW_MODEL(LangKeys.GUI_MENU_BAR_VIEW_SELECT_PREVIEW_MODEL),
    FRAME_CAMERA(LangKeys.GUI_MENU_BAR_VIEW_FRAME_CAMERA),

    ANIMATION_RETARGET(LangKeys.GUI_MENU_BAR_TOOLS_ANIMATION_RETARGET),
    DEBUG(LangKeys.GUI_MENU_BAR_TOOLS_DEBUG),

    DOCS(LangKeys.GUI_MENU_BAR_HELP_DOCS),
    ISSUE_REPORT(LangKeys.GUI_MENU_BAR_HELP_ISSUE_REPORT);

    private final Component displayName;

    TopMenuOption(final String langKey) {
        this.displayName = Component.translatable(langKey);
    }

    public Component displayName() {
        return displayName;
    }
}
