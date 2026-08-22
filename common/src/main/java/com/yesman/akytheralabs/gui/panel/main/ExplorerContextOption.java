package com.yesman.akytheralabs.gui.panel.main;

import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import net.minecraft.network.chat.Component;

/// Every option [ExplorerPanel]'s tree right-click menu can offer. The menu lays all of them out the
/// same way whichever node was pressed; the ones that node has no answer for render greyed out.
public enum ExplorerContextOption implements UniversalMenu.MenuOption {
    OPEN_IN_FILE_EXPLORER(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_OPEN_IN_FILE_EXPLORER),
    COMPILE(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_COMPILE),
    ADD_SUBDIR(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_ADD_SUBDIR),
    REMOVE_DIR(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_REMOVE_DIR);

    private final Component displayName;

    ExplorerContextOption(final String langKey) {
        this.displayName = Component.translatable(langKey);
    }

    @Override
    public Component displayName() {
        return displayName;
    }
}
