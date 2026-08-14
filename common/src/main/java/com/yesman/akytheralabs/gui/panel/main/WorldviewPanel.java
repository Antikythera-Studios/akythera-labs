package com.yesman.akytheralabs.gui.panel.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dev.ProjectReloader;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.main.WorldViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class WorldviewPanel extends AdjustablePanelManager.PanelArea {
    private static final Identifier PAUSE_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/pause");
    private static final Identifier RELOAD_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/reload");
    private static final Identifier RESUME_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/resume");
    private static final int BTN = 30;

    private final Font font;
    private final WorldViewport worldViewport;
    private final AnchoredButton resumeButton;
    private final AnchoredButton pauseButton;
    private final AnchoredButton reloadButton;

    private PlayState playState;

    public WorldviewPanel(Font font, WorldViewport worldViewport) {
        this.font = font;
        this.worldViewport = worldViewport;

        resumeButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onResume())
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> this.playState == PlayState.PLAYING)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .sprite(RESUME_SPRITE, 30, 30)
            .build();

        pauseButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onPause())
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel(BTN + 10), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> this.playState == PlayState.PAUSED)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .sprite(PAUSE_SPRITE, 30, 30)
            .build();

        reloadButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> ProjectReloader.reload(font))
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel((BTN + 5) * 2 + 5), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> false)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .sprite(RELOAD_SPRITE, 30, 30)
            .build();

        addWidget(worldViewport);
        addWidget(resumeButton);
        addWidget(pauseButton);
        addWidget(reloadButton);
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        worldViewport.relocate(screenRectangle);
        resumeButton.relocate(screenRectangle);
        pauseButton.relocate(screenRectangle);
        reloadButton.relocate(screenRectangle);
    }

    private void onResume() {
        Minecraft mc = Minecraft.getInstance();

        if (playState != PlayState.PLAYING || mc.gui.screen() != null) {
            // Remember mouse x and y pos to revert
            double xPos = mc.mouseHandler.xpos();
            double yPos = mc.mouseHandler.ypos();

            mc.gui.setScreen(null);
            // Release mouse
            mc.mouseHandler.releaseMouse();
            // Revert the mouse cursor that is modified to center in setScreen(null)
            GLFW.glfwSetCursorPos(mc.getWindow().handle(), xPos, yPos);

            playState = PlayState.PLAYING;
        }
    }

    private void onPause() {
        if (playState != PlayState.PAUSED) {
            Minecraft mc = Minecraft.getInstance();
            mc.pauseGame(true);
            playState = PlayState.PAUSED;
        }
    }

    enum PlayState {
        PLAYING, PAUSED
    }
}
