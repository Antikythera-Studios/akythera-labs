package com.yesman.akytheralabs.gui.panel.main;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dev.DataPackRegistryReloader;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.main.WorldViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public final class WorldviewPanel extends AdjustablePanelManager.PanelArea {
    private static final Identifier PAUSE_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/pause");
    private static final Identifier RELOAD_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/reload");
    private static final Identifier RESUME_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/resume");
    private static final int BTN = 30;

    private final WorldViewport worldViewport;
    private final AnchoredButton resumeButton;
    private final AnchoredButton pauseButton;
    private final AnchoredButton reloadButton;

    private PlayState playState;

    public WorldviewPanel(Font font, WorldViewport worldViewport) {
        this.worldViewport = worldViewport;

        resumeButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onResume())
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(w -> this.playState == PlayState.PLAYING)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .sprite(RESUME_SPRITE, 30, 30)
            .build();

        pauseButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onPause())
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel(BTN + 10), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(w -> this.playState == PlayState.PAUSED)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .sprite(PAUSE_SPRITE, 30, 30)
            .build();

        reloadButton = AnchoredButton.buttonBuilder(font, Component.empty(), _ -> onReload())
            .xUnits(AnchoredWidget.pixel(5), AnchoredWidget.pixel(BTN))
            .yUnits(AnchoredWidget.pixel((BTN + 5) * 2 + 5), AnchoredWidget.pixel(BTN))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(w -> false)
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

        if (playState != PlayState.PLAYING || mc.screen != null) {
            // Remember mouse x and y pos to revert
            double xPos = mc.mouseHandler.xpos();
            double yPos = mc.mouseHandler.ypos();

            mc.setScreen(null);
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

    private void onReload() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project != null) {
            Path dataPackDir = server == null ? null : server.getWorldPath(LevelResource.DATAPACK_DIR);
            String resourcePackName = project.exportAsPacks(mc.getResourcePackDirectory(), dataPackDir);
            PackRepository repository = mc.getResourcePackRepository();
            repository.reload();

            if (repository.addPack("file/" + resourcePackName)) {
                mc.options.updateResourcePacks(repository);
            }
        }

        if (server == null) {
            mc.reloadResourcePacks();
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds())
            .thenRunAsync(() -> {
                // Server thread: re-decode the datapack registries from the freshly reloaded
                // ResourceManager and refill them in place, then rebuild server-side patches so
                // they read the renewed entries.
                DataPackRegistryReloader.reloadServer(server);
                reloadServerEntityPatches(server);

                // Hop to the render thread for the in-process client copy + the single client
                // resource reload (single-player owner only; no networking).
                mc.execute(() -> {
                    RegistryAccess clientAccess = StaticRegistryAccess.getRegistryAccessUnsafe();

                    if (clientAccess != null) {
                        DataPackRegistryReloader.copyServerToClient(server.registryAccess(), clientAccess);
                    }

                    // Client-side patches read reloaded assets, so rebuild them only once the
                    // single resource-pack reload (and its lone loading overlay) completes.
                    mc.reloadResourcePacks().thenRunAsync(() -> reloadClientEntityPatches(mc), mc);
                });
            }, server);
    }

    /// Render-thread: rebuild every client-level entity patch from freshly reloaded data.
    private static void reloadClientEntityPatches(Minecraft mc) {
        ClientLevel level = mc.level;

        if (level == null) {
            return;
        }

        level.entitiesForRendering().forEach(WorldviewPanel::renewEntityPatch);
    }

    /// Server-thread: rebuild every server-level entity patch from freshly reloaded data.
    private static void reloadServerEntityPatches(IntegratedServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getAllEntities().forEach(WorldviewPanel::renewEntityPatch);
        }
    }

    private static void renewEntityPatch(Entity entity) {
        EntityAccessor accessor = (EntityAccessor) entity;

        // TODO: Fill data registries with newly generated entries in editor

        // Reload all components, provided by components factory to get refreshed data pack
        accessor.akythera$setEntityPatch(ComponentsFactory.getImpl().getEntityPatch(entity));
    }

    enum PlayState {
        PLAYING, PAUSED
    }
}
