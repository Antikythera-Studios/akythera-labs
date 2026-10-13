package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import net.minecraft.network.chat.Component;

/// Every option [LocalizationManagerModal]'s key tree right-click menu can offer. The ones the press has
/// no answer for render greyed out.
public enum LocalizationContextOption implements UniversalMenu.MenuOption {
    ADD_ENTRY(LangKeys.GUI_LOCALIZATION_MODAL_MENU_ADD_ENTRY),
    REMOVE_ENTRY(LangKeys.GUI_LOCALIZATION_MODAL_MENU_REMOVE_ENTRY);

    private final Component displayName;

    LocalizationContextOption(final String langKey) {
        this.displayName = Component.translatable(langKey);
    }

    @Override
    public Component displayName() {
        return displayName;
    }
}
