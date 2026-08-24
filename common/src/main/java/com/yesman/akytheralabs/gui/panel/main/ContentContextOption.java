package com.yesman.akytheralabs.gui.panel.main;

import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import net.minecraft.network.chat.Component;

/// Every option [ContentBrowserPanel]'s right-click menu can offer. The menu lays all of them out the
/// same way whether empty space, a folder or a content widget was pressed; the ones that context has
/// no answer for render greyed out.
public enum ContentContextOption implements UniversalMenu.MenuOption {
    OPEN(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN),
    OPEN_IN_FILE_EXPLORER(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN_IN_FILE_EXPLORER),
    COMPILE(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_COMPILE),
    CREATE_ANIM_SEQUENCE(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE_ANIM_SEQUENCE),
    CREATE_ANIM_MONTAGE(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE_ANIM_MONTAGE),
    NEW_FOLDER(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_NEW_FOLDER),
    NEW_ITEM(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_NEW_ITEM),
    RENAME(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_RENAME),
    DELETE_SELECTED(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_DELETE_SELECTED);

    private final Component displayName;

    ContentContextOption(final String langKey) {
        this.displayName = Component.translatable(langKey);
    }

    @Override
    public Component displayName() {
        return displayName;
    }
}
