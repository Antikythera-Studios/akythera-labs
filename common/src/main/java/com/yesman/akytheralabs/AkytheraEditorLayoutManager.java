package com.yesman.akytheralabs;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.screen.LoadAkytheraProjectScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.WorldViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;
import java.util.function.Consumer;

/// A manager class for all screens in Akythera editor mode
@ApiStatus.Internal
public final class AkytheraEditorLayoutManager {
    private static final AkytheraEditorLayoutManager INSTANCE = new AkytheraEditorLayoutManager();

    public static AkytheraEditorLayoutManager getInstance() {
        return INSTANCE;
    }

    /// Walk the container's focus chain to the leaf and call `setFocused(false)`
    /// on it. Used by editor screens at the top of `mouseClicked` so any pending
    /// commit (e.g. [ClampedNumberBox]'s inner EditBox focus loss) pushes its
    /// history BEFORE the newly-clicked widget's handler can push its own —
    /// keeps undo entries in temporal order.
    public static void blurFocusChainLeaf(ContainerEventHandler container) {
        GuiEventListener cur = container.getFocused();
        if (cur == null) return;

        while (cur instanceof ContainerEventHandler nested && nested.getFocused() != null) {
            cur = nested.getFocused();
        }

        cur.setFocused(false);
    }

    private final Minecraft minecraft;

    /// Modal layer drawn after the editor screen. First element is the bottom-most
    /// modal; last is the topmost. Inputs route to the topmost only, every layer
    /// renders bottom→top. Use [#openModalWidget] / [#openModalWidgetAtCenter]
    /// to push, [#closeModal] to pop the top, [#closeAllModals] to wipe.
    private final Deque<AbstractWidget> modalStack = new ArrayDeque<>();

    /// Main screen render target for AkytheraEditorLayoutManager editor
    private final RenderTarget editorRenderTarget;

    /// Custom implementation of screen open history
    private final Stack<Screen> screenStacks = new Stack<> ();

    /// Root screen of AkytheraEditorLayoutManager editor
    private MainEditorScreen mainScreen;

    /// Toggle to snatch [Minecraft#mainRenderTarget] to draw [GuiGraphicsExtractor] content to [#editorRenderTarget]
    private boolean snapMainRenderTarget;

    public AkytheraEditorLayoutManager() {
        minecraft = Minecraft.getInstance();
        editorRenderTarget = new MainTarget(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    /// Opens a layout of main Epic Engine screen.
    ///
    /// If no project has loaded yet, shows [LoadAkytheraProjectScreen] first
    /// so the user can select or create a project before entering the editor.
    public void openEditorLayout() {
        if (mainScreen == null) {
            // Close minecraft screen
            minecraft.setScreen(null);

            editorRenderTarget.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

            int scale = AkytheraLabsConfig.getEditorGuiScale();
            mainScreen = new MainEditorScreen();

            // Init at editor scale, not the game's current GUI scale
            int guiW = minecraft.getWindow().getScreenWidth() / scale;
            int guiH = minecraft.getWindow().getScreenHeight() / scale;
            mainScreen.init(guiW, guiH);
            screenStacks.push(mainScreen);
            minecraft.mouseHandler.releaseMouse();

            // Restore saved focus after screen initialized (content browser init by ifLastScreen)
            AkytheraProject currentProject = AkytheraProject.getCurrentProject();
            Path focusedPath = currentProject.getProjectRoot().resolve(currentProject.getSavedFocusedNode());
            mainScreen.explorerPanel.focusNodeByPath(focusedPath);
        }
    }

    /// Closes all editor screens and go back to the fullscreen world view
    public void closeEditorLayout() {
        if (mainScreen != null) {
            while (!screenStacks.isEmpty()) {
                screenStacks.lastElement().onClose();
            }

            mainScreen = null;
            setFullScreenWorldViewport();
            closeAllModals();
        }
    }

    /// Restores the full-screened world viewport
    public void setFullScreenWorldViewport() {
        // Restore the fullscreen size
        minecraft.getWindow().setWidth(editorRenderTarget.width);
        minecraft.getWindow().setHeight(editorRenderTarget.height);
        minecraft.getMainRenderTarget().resize(editorRenderTarget.width, editorRenderTarget.height);
        minecraft.resizeGui();
    }

    /// Opens a new screen and push to history stack
    public void openNewScreen(final Screen screen) {
        closeAllModals();
        HistoryManager.getInstance().requestSavePointOnNextAction();

        int scale = AkytheraLabsConfig.getEditorGuiScale();
        // Init at editor scale, not the game's current GUI scale
        int guiW = minecraft.getWindow().getScreenWidth() / scale;
        int guiH = minecraft.getWindow().getScreenHeight() / scale;
        screen.init(guiW, guiH);
        screenStacks.push(screen);
    }

    /// Closes the last screen on stack
    public void closeLastScreen() {
        if (!screenStacks.isEmpty()) {
            closeAllModals();
            screenStacks.lastElement().onClose();
        }
    }

    /// Removes screen on the last stack
    ///
    /// This method is intended to event callback when [Screen#onClose] is called.
    /// If you want to close the screen, the UI control, use [#closeLastScreen].
    public void removeLastScreen() {
        closeAllModals();
        HistoryManager.getInstance().popScreen();
        screenStacks.pop();
    }

    public @Nullable Screen getRecentEditorScreen() {
        return screenStacks.isEmpty() ? null : screenStacks.lastElement();
    }

    /// Returns a last editor screen from open history
    @SuppressWarnings("unchecked")
    public <T extends Screen> void ifLastScreen(Consumer<T> task) {
        if (screenStacks.isEmpty()) {
            return;
        }

        try {
            task.accept((T)screenStacks.lastElement());
        } catch (ClassCastException _) {
        }
    }

    /// Push a modal at the desired location on top of the stack. While the stack
    /// is non-empty, all inputs intercept to the topmost modal. Lower layers stay
    /// visible behind it.
    ///
    /// If the modal is a [WidgetContainerModal] built with [WidgetContainerModal.Builder#saveHistory()],
    /// the layout manager requests a [com.yesman.akytheralabs.editor.historytrack.ScreenSavePoint]
    /// at open and pops the modal's bracket at close. Otherwise (pickers /
    /// context menus / confirmations), no save point — callback pushes land
    /// on the parent screen's history.
    ///
    /// Callers that need the existing topmost modal to be replaced (e.g. the
    /// `TopMenuBar` switching between dropdowns) must call [#closeModal] first.
    public void openModalWidget(final @NonNull AbstractWidget modal, final int x, final int y) {
        if (!isActive()) {
            throw new IllegalStateException("Tried to open a modal before project opened");
        }

        if (modal instanceof WidgetContainerModal wcm && wcm.saveHistory()) {
            HistoryManager.getInstance().requestSavePointOnNextAction();
        }

        modal.setPosition(x, y);
        modal.active = true;
        modal.visible = true;

        if (modal instanceof AnchoredWidget anchoredWidget) {
            anchoredWidget.relocate(screenStacks.getLast().getRectangle());
        }

        modalStack.addLast(modal);
    }

    /// Push a modal at the center of the screen. Save-history opt-in
    /// is read off the modal — see [#openModalWidget] for semantics.
    public void openModalWidgetAtCenter(final @NonNull AbstractWidget modal) {
        if (!isActive()) {
            throw new IllegalStateException("Tried to open a modal before project opened");
        }

        if (modal instanceof WidgetContainerModal wcm && wcm.saveHistory()) {
            HistoryManager.getInstance().requestSavePointOnNextAction();
        }

        int scale = AkytheraLabsConfig.getEditorGuiScale();
        int x = minecraft.getWindow().getScreenWidth() / scale / 2 - modal.getWidth() / 2;
        int y = minecraft.getWindow().getScreenHeight() / scale / 2 - modal.getHeight() / 2;
        modal.setPosition(x, y);
        modal.active = true;
        modal.visible = true;

        if (modal instanceof AnchoredWidget anchoredWidget) {
            anchoredWidget.relocate(screenStacks.getLast().getRectangle());
        }

        modalStack.addLast(modal);
    }

    /// Pop the topmost modal widget. When the modal opted in via
    /// [WidgetContainerModal.Builder#saveHistory()], also pops its
    /// `ScreenSavePoint` bracket off the history stack.
    public void closeModal() {
        if (modalStack.isEmpty()) return;

        AbstractWidget top = modalStack.removeLast();

        if (top instanceof WidgetContainerModal wcm && wcm.saveHistory()) {
            HistoryManager.getInstance().popScreen();
        }

        top.active = false;
        top.visible = false;
        top.setFocused(false);

        if (top instanceof CloseableWidget cw) cw.destroy();
    }

    /// Pop every modal off the stack. Used by editor-close and screen-change
    /// flows where the modal layer should not survive.
    public void closeAllModals() {
        while (!modalStack.isEmpty()) closeModal();
    }

    /// Returns the topmost modal, or `null` when the stack is empty.
    public @Nullable AbstractWidget getModal() {
        return modalStack.peekLast();
    }

    /// All open modals in render order (bottom-most first, topmost last).
    public Iterable<AbstractWidget> getModals() {
        return modalStack;
    }

    /// Whether any modal is currently open.
    public boolean hasModal() {
        return !modalStack.isEmpty();
    }

    /// Returns editor main screen
    public @Nullable MainEditorScreen getMainScreen() {
        return mainScreen;
    }

    public RenderTarget getEditorRenderTarget() {
        return editorRenderTarget;
    }

    public int getLayoutGuiScale() {
        return AkytheraLabsConfig.getEditorGuiScale();
    }

    /// Returns whether editor activated
    public boolean isActive() {
        return !screenStacks.isEmpty();
    }

    /// Whether a mouse button should drive a drag. Vanilla widgets only drag with the
    /// left button (button 0); inside the editor we additionally treat the right button
    /// (button 1) as a drag button so canvases like the node graph can pan on a right-drag.
    /// Gated on [#isActive] so vanilla / non-editor screens keep their stock behaviour.
    public static boolean acceptsDragButton(MouseButtonEvent event) {
        return event.button() == 0 || (getInstance().isActive() && event.button() == 1);
    }

    /// Toggle the main render target snatch mode. If turned on, [GuiRenderer] will no longer
    /// render UI to [Minecraft#mainRenderTarget] but [#editorRenderTarget].
    ///
    /// Useful for rescaling the world viewport to fit the size of [WorldViewport]
    public void setInterceptMainRenderTarget(final boolean flag) {
        snapMainRenderTarget = flag;
    }

    /// Returns the main render target snatch state
    public boolean shouldInterceptRenderTarget() {
        return snapMainRenderTarget;
    }

    /// Resize AkytheraEditorLayoutManager main display. This is called on [GameRenderer#resize]
    /// so it's safe to use on graphic driver resize calls.
    ///
    /// left `guiScale` parameter in case we correct editor scale based on Minecraft's gui scale.
    public void onScreenResized(final int width, final int height, int guiScale) {
        if (mainScreen != null) {
            editorRenderTarget.resize(width, height);
            int scale = AkytheraLabsConfig.getEditorGuiScale();

            for (Screen screen : screenStacks) {
                screen.resize(width / scale, height / scale);
            }

            ScreenRectangle topRect = screenStacks.getLast().getRectangle();

            for (AbstractWidget m : modalStack) {
                if (m instanceof AnchoredWidget anchoredModal) {
                    anchoredModal.relocate(topRect);
                }

                if (m instanceof ResizeCompleteCallbackListener resizeCallbackListener) {
                    resizeCallbackListener.onResizeCompleted();
                }
            }
        }
    }

    public void rescaleScreens(final int scale) {
        if (!isActive()) {
            return;
        }

        for (Screen screen : screenStacks) {
            int guiW = Minecraft.getInstance().getWindow().getScreenWidth() / scale;
            int guiH = Minecraft.getInstance().getWindow().getScreenHeight() / scale;
            screen.init(guiW, guiH);

            // Re-anchor every modal layer, not just the top.
            for (AbstractWidget modal : AkytheraEditorLayoutManager.getInstance().getModals()) {
                if (modal instanceof AnchoredWidget anchoredWidget) {
                    anchoredWidget.relocate(screen.getRectangle());
                }
            }
        }
    }

    /// Resizes the game world framebuffer to match the viewport widget dimensions.
    /// Called when the user finishes dragging a panel divider.
    public void resizeWorldViewport(final int vpW, final int vpH) {
        int scale = AkytheraLabsConfig.getEditorGuiScale();
        minecraft.getWindow().setWidth(vpW * scale);
        minecraft.getWindow().setHeight(vpH * scale);
        minecraft.resizeGui();
    }

    /// Checks whether the inputs should be intercepted by editor screen
    ///
    /// If mouse pointer is grabbed inputs won't be intercepted.
    ///
    /// If mouse pointer is in main screen's gameplay viewport, inputs won't be intercepted.
    public boolean snatchInputs() {
        if (mainScreen == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHandler = minecraft.mouseHandler;

        if (mouseHandler.isMouseGrabbed()) {
            return false;
        }

        // Always snatch inputs if not the main screen (where world viewport exists)
        if (!screenStacks.isEmpty() && screenStacks.getLast() != mainScreen) {
            return true;
        }

        // During divider drag the mouse can briefly appear inside the viewport boundary
        // always snatch to prevent dropped drag events at the edge
        if (mainScreen.isEditorDragging()) return true;

        int scale = AkytheraLabsConfig.getEditorGuiScale();
        int x = (int)Math.round(minecraft.mouseHandler.xpos() * (1.0D / scale));
        int y = (int)Math.round(minecraft.mouseHandler.ypos() * (1.0D / scale));

        // Any open modal grabs every input.
        if (!modalStack.isEmpty()) {
            return true;
        }

        return !mainScreen.pointerInGameplayViewport(x, y);
    }

    /// Render the editor layout based on custom screen [#mainScreen]
    public void renderEditorLayout(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        if (!isActive()) {
            throw new IllegalStateException("Can't render AkytheraEditorLayoutManager editor since it's deactivated");
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            editorRenderTarget.getColorTexture(),
            0,
            editorRenderTarget.getDepthTexture(),
            1.0
        );

        // Restore info
        int fullscreenWidth = minecraft.getWindow().getWidth();
        int fullscreenHeight = minecraft.getWindow().getHeight();
        int fullscreenGuiScale = minecraft.getWindow().getGuiScale();

        int scale = AkytheraLabsConfig.getEditorGuiScale();

        // Set to real screen size
        minecraft.getWindow().setWidth(minecraft.getWindow().getScreenWidth());
        minecraft.getWindow().setHeight(minecraft.getWindow().getScreenHeight());
        minecraft.getWindow().setGuiScale(scale);

        boolean anyModal = !modalStack.isEmpty();

        // Force mouse pointer out of the screen when any modal is open. This prevents
        // activating hover effects for objects behind the modal layer.
        screenStacks.lastElement().extractRenderStateWithTooltipAndSubtitles(
            graphics,
            anyModal ? -1 : mouseX,
            anyModal ? -1 : mouseY,
            a
        );

        // Render every layer bottom→top. Only the topmost sees real mouse coords
        // so hover/tooltip belongs to it; lower layers run with (-1, -1).
        AbstractWidget top = modalStack.peekLast();
        for (AbstractWidget m : modalStack) {
            if (!m.isActive()) continue;
            boolean isTop = m == top;
            m.extractRenderState(graphics, isTop ? mouseX : -1, isTop ? mouseY : -1, a);
        }

        if (anyModal) {
            graphics.extractDeferredElements(mouseX, mouseY, a);
        }

        // Toasts on top of the editor layout. The in-game extraction is suppressed while the editor is
        // active (see MixinGameRenderer#extractToasts); re-extracting here draws them into the editor
        // render target at the editor gui scale, so a toast sits top-right of the layout, not the world
        // viewport. The window is already sized to the screen at the editor scale above, so the toast's
        // own xPos/yPos resolve against the editor layout.
        minecraft.getToastManager().extractRenderState(graphics);

        // Apply cursor
        graphics.applyCursor(minecraft.getWindow());

        minecraft.gameRenderer.getGameRenderState().windowRenderState.width = minecraft.getWindow().getScreenWidth();
        minecraft.gameRenderer.getGameRenderState().windowRenderState.height = minecraft.getWindow().getScreenHeight();
        minecraft.gameRenderer.getGameRenderState().windowRenderState.guiScale = scale;

        // By turning on intercept mode, GuiRenderer will start to drawing submitted nodes by GuiGraphicsExtractor.
        setInterceptMainRenderTarget(true);
        minecraft.gameRenderer.guiRenderer.render(minecraft.gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        // Recover intercepting state
        setInterceptMainRenderTarget(false);

        minecraft.gameRenderer.guiRenderer.endFrame();
        editorRenderTarget.blitToScreen();

        // Restore the viewport size
        minecraft.getWindow().setWidth(fullscreenWidth);
        minecraft.getWindow().setHeight(fullscreenHeight);
        minecraft.getWindow().setGuiScale(fullscreenGuiScale);

        // Restore window state
        minecraft.gameRenderer.getGameRenderState().windowRenderState.width = fullscreenWidth;
        minecraft.gameRenderer.getGameRenderState().windowRenderState.height = fullscreenHeight;
        minecraft.gameRenderer.getGameRenderState().windowRenderState.guiScale = fullscreenGuiScale;
    }
}
