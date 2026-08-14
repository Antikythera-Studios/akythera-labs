package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.LoadAkytheraProjectScreen;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {
    @Unique
    private static final WidgetSprites EDITOR_OPEN_BUTTON = new WidgetSprites(Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/open_editor"));

    @Shadow
    @Final
    private boolean showPauseMenu;

    protected MixinPauseScreen() {
        super(Component.empty());
    }

    @Inject(at = @At("TAIL"), method = "init()V")
    private void addEditorButton(CallbackInfo ci) {
        if (!showPauseMenu) return;

        SpriteIconButton openEditorButton = SpriteIconButton.builder(
            Component.translatable(LangKeys.GUI_MESSAGE_OPEN_EDITOR),
            _ -> minecraft.gui.setScreen(new LoadAkytheraProjectScreen()),
            true
        )
        .sprite(EDITOR_OPEN_BUTTON, 20, 20)
        .withTootip()
        .build();

        // Align with the "Back to Game" button (PauseScreen#RETURN_TO_GAME), whose Y can be
        // shifted by mod loaders such as NeoForge inserting an extra "Mods" button into the menu.
        Component returnToGame = Component.translatable("menu.returnToGame");
        int backToGameY = children().stream()
            .filter(child -> child instanceof AbstractButton)
            .map(child -> (AbstractButton) child)
            .filter(button -> returnToGame.equals(button.getMessage()))
            .mapToInt(AbstractButton::getY)
            .findFirst()
            .orElse(height / 4 + 8);

        openEditorButton.setPosition(width / 2 + 106, backToGameY);
        openEditorButton.setSize(20, 20);
        openEditorButton.active = !AkytheraEditorLayoutManager.getInstance().isActive() && minecraft.isLocalServer();

        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            openEditorButton.setTooltip(Tooltip.create(Component.translatable(LangKeys.GUI_MESSAGE_ALREADY_OPENED)));
        } else if (!minecraft.isLocalServer()) {
            openEditorButton.setTooltip(Tooltip.create(Component.translatable(LangKeys.GUI_MESSAGE_NOT_A_SINGLEPLAYER)));
        }

        addRenderableWidget(openEditorButton);
    }
}
