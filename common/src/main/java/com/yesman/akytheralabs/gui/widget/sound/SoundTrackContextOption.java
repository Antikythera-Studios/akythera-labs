package com.yesman.akytheralabs.gui.widget.sound;

import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import net.minecraft.network.chat.Component;

/// Every option [SoundPlayTrack]'s row right-click menu offers.
public enum SoundTrackContextOption implements UniversalMenu.MenuOption {
    DELETE(LangKeys.GUI_SOUND_ASSET_SCREEN_TRACK_CONTEXT_DELETE);

    private final Component displayName;

    SoundTrackContextOption(final String langKey) {
        this.displayName = Component.translatable(langKey);
    }

    @Override
    public Component displayName() {
        return displayName;
    }
}
