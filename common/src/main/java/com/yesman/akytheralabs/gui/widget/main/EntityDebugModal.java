package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.FloatingModal;
import com.yesman.akythera.client.api.gui.modal.FrameResizer;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.modal.ResizableFrame;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dev.ProjectReloader;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.DebuggableScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WorldViewportHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A floating second viewport: the in-game frame buffer, sized to this modal's content area, while the
/// player possesses an entity through [EntityDebugSession].
///
/// Movable by its header and resizable by its frame. As a [FloatingModal] it leaves the editor screen
/// under it fully usable — the debugging it feeds is read off that screen — and the `X` button is the
/// only way out.
///
/// Pointer handling comes for free: while the cursor is inside the viewport
/// [AkytheraEditorLayoutManager#snatchInputs] answers false, so the click reaches the game, which grabs the
/// pointer. [EditorKeyMappings#UNGRAB] gives it back.
///
/// Over a screen whose scripts run on the server too ([DebuggableScreen#mirrorsServer]), the header
/// carries a switch between mirroring the client's runs and the server's.
public final class EntityDebugModal extends AbstractContainerWidget
    implements AnchoredWidget, FloatingModal, ResizableFrame, ResizeCompleteCallbackListener, CloseableWidget, WorldViewportHost {

    private static final Identifier RELOAD_SPRITE = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "widget/reload");
    private static final int TITLE_H = 20;
    private static final int BORDER = 1;
    private static final int BUTTON_SIZE = TITLE_H - 4;
    private static final int BUTTON_MARGIN = 2;
    private static final int MIN_WIDTH = 220;
    private static final int MIN_HEIGHT = 160;
    private static final int DEFAULT_WIDTH = 426;
    private static final int DEFAULT_HEIGHT = 260;
    private static final int TEXT_BUTTON_PAD = 8; // room around a text button's label

    /// Possesses `entity` and opens the viewport centred on the editor screen on top.
    public static void open(final Font font, final Entity entity) {
        AkytheraEditorLayoutManager layoutManager = AkytheraEditorLayoutManager.getInstance();
        Screen screen = layoutManager.getRecentEditorScreen();
        if (screen == null) return;

        EntityDebugModal modal = new EntityDebugModal(font, entity);
        int x = (screen.width - DEFAULT_WIDTH) / 2;
        int y = (screen.height - DEFAULT_HEIGHT) / 2;

        modal.setX1(AnchoredWidget.pixel(x));
        modal.setY1(AnchoredWidget.pixel(y));

        EntityDebugSession.getInstance().begin(entity);

        // Pushing a world-viewport modal sizes the world framebuffer to it, so there is nothing to
        // initialize here.
        ModalManager.getInstance().openModalWidget(modal, x, y);
    }

    private final Font font;
    private final WorldViewport worldViewport;
    private final AkytheraButton reloadButton;
    private final AkytheraButton sideButton;
    private final AkytheraButton closeButton;
    private final FrameResizer frameResizer;

    private boolean moveByDragging;
    private double xAccumul;
    private double yAccumul;

    private EntityDebugModal(final Font font, final Entity entity) {
        super(
            0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT,
            Component.translatable(LangKeys.GUI_ENTITY_DEBUG_MODAL_TITLE, entity.getPlainTextName()),
            AbstractScrollArea.defaultSettings(0)
        );

        this.font = font;
        this.frameResizer = new FrameResizer(this, MIN_WIDTH, MIN_HEIGHT);

        this.worldViewport = new WorldViewport(
            AnchoredWidget.pixel(BORDER), AnchoredWidget.pixel(BORDER),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(BORDER),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_GAMEPLAY_VIEWPORT)
        );

        this.closeButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(BUTTON_MARGIN), AnchoredWidget.pixel(BUTTON_SIZE))
            .yUnits(AnchoredWidget.pixel(BUTTON_MARGIN), AnchoredWidget.pixel(BUTTON_SIZE))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> false)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        this.reloadButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.EMPTY, () -> ProjectReloader.reload(font))
            .xUnits(AnchoredWidget.pixel(BUTTON_MARGIN * 2 + BUTTON_SIZE), AnchoredWidget.pixel(BUTTON_SIZE))
            .yUnits(AnchoredWidget.pixel(BUTTON_MARGIN), AnchoredWidget.pixel(BUTTON_SIZE))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> false)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .sprite(RELOAD_SPRITE, BUTTON_SIZE - 4, BUTTON_SIZE - 4)
            .build();

        int sideWidth = Math.max(
            font.width(Component.translatable(LangKeys.GUI_ENTITY_DEBUG_MODAL_SIDE_CLIENT)),
            font.width(Component.translatable(LangKeys.GUI_ENTITY_DEBUG_MODAL_SIDE_SERVER))
        ) + TEXT_BUTTON_PAD;

        this.sideButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_ENTITY_DEBUG_MODAL_SIDE_CLIENT), () -> {
                EntityDebugSession session = EntityDebugSession.getInstance();
                session.setMirrorServer(!session.isMirroringServer());
            })
            .xUnits(AnchoredWidget.pixel(BUTTON_MARGIN * 3 + BUTTON_SIZE * 2), AnchoredWidget.pixel(sideWidth))
            .yUnits(AnchoredWidget.pixel(BUTTON_MARGIN), AnchoredWidget.pixel(BUTTON_SIZE))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .isHighlighted(_ -> false)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(worldViewport, sideButton, reloadButton, closeButton);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    /// The frame and its resize grip, and nothing else — every other input belongs to the editor
    /// screen this floats over.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive() && frameResizer.withinGripBounds(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (frameResizer.mousePressed(event.x(), event.y())) {
            return true;
        }

        // A press in the viewport hands the pointer to the game, which is what puts look, movement
        // and the POV key through to the possessed entity.
        if (pointerInWorldViewport((int)event.x(), (int)event.y())) {
            Minecraft.getInstance().mouseHandler.grabMouse();
            return true;
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (isMouseOverHeader(event.x(), event.y())) {
            moveByDragging = true;
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (frameResizer.mouseDragged(event.x(), event.y())) {
            return true;
        }

        if (moveByDragging) {
            xAccumul += dx;
            yAccumul += dy;
            int xMove = (int)xAccumul;
            int yMove = (int)yAccumul;

            if (xMove != 0 || yMove != 0) {
                moveBy(xMove, yMove);
                xAccumul -= xMove;
                yAccumul -= yMove;
            }

            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (frameResizer.mouseReleased()) {
            AkytheraEditorLayoutManager.getInstance().resizeWorldViewportToHost();
            return true;
        }

        if (moveByDragging) {
            moveByDragging = false;
            xAccumul = 0;
            yAccumul = 0;

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);

        sideButton.visible = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof DebuggableScreen debuggable
            && debuggable.mirrorsServer();
        sideButton.setMessage(Component.translatable(EntityDebugSession.getInstance().isMirroringServer()
            ? LangKeys.GUI_ENTITY_DEBUG_MODAL_SIDE_SERVER
            : LangKeys.GUI_ENTITY_DEBUG_MODAL_SIDE_CLIENT));

        int titleRoom = Math.max((sideButton.visible ? sideButton : reloadButton).getX() - getX() - 12, 0);
        String title = font.plainSubstrByWidth(getMessage().getString(), titleRoom);
        graphics.text(font, title, getX() + 8, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        worldViewport.extractRenderState(graphics, mouseX, mouseY, a);
        sideButton.extractRenderState(graphics, mouseX, mouseY, a);
        reloadButton.extractRenderState(graphics, mouseX, mouseY, a);
        closeButton.extractRenderState(graphics, mouseX, mouseY, a);

        frameResizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    public FrameResizer frameResizer() {
        return frameResizer;
    }

    @Override
    public void onResizeCompleted() {
        AkytheraEditorLayoutManager.getInstance().resizeWorldViewportToHost();
    }

    /// Popping a world-viewport modal hands the world framebuffer back to whatever hosts it next, so
    /// only the possession has to be ended here.
    @Override
    public void destroy() {
        EntityDebugSession.getInstance().end();
    }

    @Override
    public ScreenRectangle getWorldViewportRectangle() {
        return worldViewport.getRectangle();
    }

    /// The modal floats over the editor screen, so a pointer crossing its viewport must not silently
    /// stop reaching the header — [#mouseClicked] grabs on a press instead.
    @Override
    public boolean routesHoverToGame() {
        return false;
    }

    private boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= (double)getX()
            && mouseY >= (double)getY()
            && mouseX < (double)getRight()
            && mouseY < (double)getY() + TITLE_H;
    }

    /// Shifts the frame by rewriting its anchor units — the rect [AnchoredWidget#relocate] re-derives
    /// from them is the only one that survives.
    private void moveBy(final int xMove, final int yMove) {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (screen == null) {
            return;
        }

        setX1(AnchoredWidget.pixel(getX() + xMove));
        setY1(AnchoredWidget.pixel(getY() + yMove));
        relocate(screen.getRectangle());
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1 = AnchoredWidget.pixel(0);
    private Unit x2 = AnchoredWidget.pixel(DEFAULT_WIDTH);
    private Unit y1 = AnchoredWidget.pixel(0);
    private Unit y2 = AnchoredWidget.pixel(DEFAULT_HEIGHT);
    private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
