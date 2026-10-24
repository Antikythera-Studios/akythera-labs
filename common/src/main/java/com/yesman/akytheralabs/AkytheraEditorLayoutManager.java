package com.yesman.akytheralabs;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.client.api.event.BuiltInClientEventHooks;
import com.yesman.akythera.client.api.event.gui.ModalEvent;
import com.yesman.akythera.client.api.gui.ClampedNumberBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.NodeClipboard;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ScreenSavePoint;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.screen.LoadAkytheraProjectScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.WorldViewportHost;
import com.yesman.akytheralabs.gui.widget.main.WorldViewport;
import com.yesman.akytheralabs.mixinaccessor.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Stack;
import java.util.function.Consumer;

/// Owns every screen of the Akythera editor and hosts the modal layer of [ModalManager] over them.
@ApiStatus.Internal
public final class AkytheraEditorLayoutManager {
    private static final Vector4f TRANSPARENT_CLEAR_COLOR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    /// Initialization-on-demand holder: the constructor reads the game window, which does not exist yet
    /// when [#registerEvents] runs from the mod entry point.
    private static final class InstanceHolder {
        private static final AkytheraEditorLayoutManager INSTANCE = new AkytheraEditorLayoutManager();
    }

    public static AkytheraEditorLayoutManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /// Walks the container's focus chain to the leaf and calls `setFocused(false)` on it, so a
    /// pending commit — [ClampedNumberBox]'s inner EditBox losing focus, say — pushes its history
    /// entry before whatever the click itself pushes, keeping the undo stack in temporal order.
    ///
    /// A click landing back on the leaf is left alone: it brings no history of its own to order
    /// against, and blurring it would commit and re-focus the widget the user is still editing.
    public static void blurFocusChainLeaf(ContainerEventHandler container, double mouseX, double mouseY) {
        GuiEventListener cur = container.getFocused();
        if (cur == null) return;

        while (cur instanceof ContainerEventHandler nested && nested.getFocused() != null) {
            cur = nested.getFocused();
        }

        if (cur.isMouseOver(mouseX, mouseY)) return;

        cur.setFocused(false);
    }

    private final Minecraft minecraft;

    /// The render target the editor layout draws into.
    private final RenderTarget editorRenderTarget;

    /// The editor's own open-screen history.
    private final Stack<Screen> screenStacks = new Stack<> ();

    /// The editor's root screen.
    private MainEditorScreen mainScreen;

    /// Toggle to snatch [Minecraft#mainRenderTarget] to draw [GuiGraphicsExtractor] content to [#editorRenderTarget]
    private boolean snapMainRenderTarget;

    /// Whether the game is shown full screen in place of the editor layout, see [#suspendLayout].
    private boolean suspended;

    /// Whether the last frame presented [#editorRenderTarget] rather than the game's render target.
    private boolean layoutPresented;

    public AkytheraEditorLayoutManager() {
        minecraft = Minecraft.getInstance();
        editorRenderTarget = new MainTarget(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    /// Reacts to the editor's modals while the editor is up: a [WidgetContainerModal] built with
    /// [WidgetContainerModal.Builder#saveHistory()] brackets its edits in a [ScreenSavePoint], and a
    /// [WorldViewportHost] hands the game its viewport rect.
    public static void registerEvents() {
        BuiltInClientEventHooks.Gui.MODAL_OPENED.registerEvent(AkytheraEditorLayoutManager::onModalOpened);
        BuiltInClientEventHooks.Gui.MODAL_CLOSED.registerEvent(AkytheraEditorLayoutManager::onModalClosed);
    }

    private static void onModalOpened(final ModalEvent.Opened event) {
        if (!getInstance().isActive()) return;

        if (event.getModal() instanceof WidgetContainerModal wcm && wcm.saveHistory()) {
            HistoryManager.getInstance().requestSavePointOnNextAction();
        }

        if (event.getModal() instanceof WorldViewportHost) {
            getInstance().resizeWorldViewportToHost();
        }
    }

    private static void onModalClosed(final ModalEvent.Closed event) {
        if (!getInstance().isActive()) return;

        if (event.getModal() instanceof WidgetContainerModal wcm && wcm.saveHistory()) {
            HistoryManager.getInstance().popScreen();
        }

        if (event.getModal() instanceof WorldViewportHost) {
            getInstance().resizeWorldViewportToHost();
        }
    }

    /// Opens a layout of main Epic Engine screen.
    ///
    /// If no project has loaded yet, shows [LoadAkytheraProjectScreen] first
    /// so the user can select or create a project before entering the editor.
    public void openEditorLayout() {
        if (mainScreen == null) {
            // Close minecraft screen
            minecraft.gui.setScreen(null);

            editorRenderTarget.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

            int scale = AkytheraLabsConfig.getEditorGuiScale();
            mainScreen = new MainEditorScreen();

            // Init at editor scale, not the game's current GUI scale
            int guiW = minecraft.getWindow().getScreenWidth() / scale;
            int guiH = minecraft.getWindow().getScreenHeight() / scale;
            mainScreen.init(guiW, guiH);
            screenStacks.push(mainScreen);
            ModalManager.getInstance().setHostScreen(this::getRecentEditorScreen);
            minecraft.mouseHandler.releaseMouse();

            // Restore saved focus after screen initialized (content browser init by ifLastScreen)
            AkytheraProject currentProject = AkytheraProject.getCurrentProject();
            Path focusedPath = currentProject.getProjectRoot().resolve(currentProject.getSavedFocusedNode());
            mainScreen.explorerPanel.focusNodeByPath(focusedPath);
        }
    }

    /// Closes every editor screen and returns to the fullscreen world view.
    public void closeEditorLayout() {
        if (mainScreen != null) {
            while (!screenStacks.isEmpty()) {
                screenStacks.lastElement().onClose();
            }

            mainScreen = null;
            suspended = false;
            setFullScreenWorldViewport();
            ModalManager.getInstance().closeAllModals();
            ModalManager.getInstance().setHostScreen(null);
        }
    }

    /// Restores the full-screened world viewport
    public void setFullScreenWorldViewport() {
        // Restore the fullscreen size
        minecraft.getWindow().setWidth(editorRenderTarget.width);
        minecraft.getWindow().setHeight(editorRenderTarget.height);
        minecraft.gameRenderer.mainRenderTarget().resize(editorRenderTarget.width, editorRenderTarget.height);
        minecraft.resizeGui();
    }

    /// Opens a screen and pushes it onto the open-screen history.
    public void openNewScreen(final Screen screen) {
        ModalManager.getInstance().closeAllModals();
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
            ModalManager.getInstance().closeAllModals();
            screenStacks.lastElement().onClose();
        }
    }

    /// Removes screen on the last stack
    ///
    /// This method is intended to event callback when [Screen#onClose] is called.
    /// If you want to close the screen, the UI control, use [#closeLastScreen].
    public void removeLastScreen() {
        ModalManager.getInstance().closeAllModals();
        HistoryManager.getInstance().popScreen();
        NodeClipboard.getInstance().cancelCut();
        screenStacks.pop();
    }

    public @Nullable Screen getRecentEditorScreen() {
        return screenStacks.isEmpty() ? null : screenStacks.lastElement();
    }

    /// The most recently opened editor screen.
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

    /// The editor's main screen.
    public @Nullable MainEditorScreen getMainScreen() {
        return mainScreen;
    }

    /// The host whose world viewport the pointer can fall into: the topmost modal when it owns one,
    /// otherwise the editor screen on top. Null means every input belongs to the editor.
    public @Nullable WorldViewportHost getWorldViewportHost() {
        AbstractWidget topModal = ModalManager.getInstance().getModal();

        if (topModal != null) {
            return topModal instanceof WorldViewportHost host ? host : null;
        }

        return getRecentEditorScreen() instanceof WorldViewportHost host ? host : null;
    }

    public RenderTarget getEditorRenderTarget() {
        return editorRenderTarget;
    }

    public int getLayoutGuiScale() {
        return AkytheraLabsConfig.getEditorGuiScale();
    }

    /// Whether the editor is currently up.
    public boolean isActive() {
        return !screenStacks.isEmpty();
    }

    /// Whether the editor layout is drawn and takes inputs: the editor is up and not suspended.
    public boolean isLayoutShown() {
        return isActive() && !suspended;
    }

    /// Shows the game full screen in place of the editor layout until [#resumeLayout]. The editor screens stay open
    /// but are neither drawn nor given inputs.
    public void suspendLayout() {
        if (!isActive() || suspended) return;

        suspended = true;
        setFullScreenWorldViewport();
    }

    /// Shows the editor layout again after [#suspendLayout].
    public void resumeLayout() {
        if (!suspended) return;

        suspended = false;

        if (isActive()) resizeWorldViewportToHost();
    }

    /// Records whether this frame presented the editor layout.
    public void markPresented(final boolean layout) {
        layoutPresented = layout;
    }

    /// The render target the last frame presented: the editor layout's, or the game's.
    public RenderTarget getPresentedRenderTarget() {
        return layoutPresented ? editorRenderTarget : minecraft.gameRenderer.mainRenderTarget();
    }

    /// Whether a mouse button should drive a drag. Vanilla widgets drag on the left button (0)
    /// alone; while [#isActive] the editor also drags on the right button (1), which is what lets a
    /// canvas such as the node graph pan on a right-drag.
    public static boolean acceptsDragButton(MouseButtonEvent event) {
        return event.button() == 0 || (getInstance().isActive() && event.button() == 1);
    }

    /// Toggles the main render target snatch mode. While on, [GuiRenderer] renders the UI into
    /// [#editorRenderTarget] instead of [Minecraft#mainRenderTarget], which is what lets the world
    /// viewport be rescaled to the size of [WorldViewport].
    public void setInterceptMainRenderTarget(final boolean flag) {
        snapMainRenderTarget = flag;
    }

    /// Whether the main render target is currently snatched, see [#setSnatchMainRenderTarget].
    public boolean shouldInterceptRenderTarget() {
        return snapMainRenderTarget;
    }

    /// Resizes the editor's main display, from [GameRenderer#resize] and so safely on a graphics
    /// driver resize.
    public void onScreenResized(final int width, final int height, int guiScale) {
        if (mainScreen != null) {
            editorRenderTarget.resize(width, height);
            int scale = AkytheraLabsConfig.getEditorGuiScale();

            for (Screen screen : screenStacks) {
                screen.resize(width / scale, height / scale);
            }

            ModalManager.getInstance().relocateModals();
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
            for (AbstractWidget modal : ModalManager.getInstance().getModals()) {
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
        int width = vpW * scale;
        int height = vpH * scale;

        minecraft.getWindow().setWidth(width);
        minecraft.getWindow().setHeight(height);
        // The window carries the size; the render target is what the world is actually drawn into, and
        // is otherwise only resized by a real window resize (see [#setFullScreenWorldViewport]).
        minecraft.gameRenderer.mainRenderTarget().resize(width, height);
        minecraft.resizeGui();
    }

    /// Resizes the game world framebuffer to the viewport of the current [#getWorldViewportHost].
    ///
    /// There is not always a host to ask. An editor screen that shows no world hosts no viewport, so
    /// closing a world-viewport modal over one leaves nothing on top that owns the rect — the size then
    /// comes from [#mainScreen], the viewport the game goes back to. With no main screen either the
    /// editor is not up at all, and the whole display belongs to the game.
    public void resizeWorldViewportToHost() {
        WorldViewportHost host = getWorldViewportHost();

        if (host == null) {
            host = mainScreen;
        }

        if (host == null) {
            setFullScreenWorldViewport();
            return;
        }

        ScreenRectangle rectangle = host.getWorldViewportRectangle();

        // Min pixel guard: 10px
        resizeWorldViewport(Math.max(rectangle.width(), 10), Math.max(rectangle.height(), 10));
    }

    /// Checks whether the inputs should be intercepted by editor screen
    ///
    /// If mouse pointer is grabbed inputs won't be intercepted.
    ///
    /// If mouse pointer is in the current [#getWorldViewportHost]'s viewport, inputs won't be intercepted.
    public boolean snatchInputs() {
        if (mainScreen == null || suspended) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHandler = minecraft.mouseHandler;

        if (mouseHandler.isMouseGrabbed()) {
            return false;
        }

        // During divider drag the mouse can briefly appear inside the viewport boundary
        // always snatch to prevent dropped drag events at the edge
        if (mainScreen.isEditorDragging()) return true;

        WorldViewportHost host = getWorldViewportHost();

        // A host that only hands over on an explicit press keeps its own widgets clickable: until it
        // grabs the pointer, every input stays with the editor.
        if (host == null || !host.routesHoverToGame()) {
            return true;
        }

        int scale = AkytheraLabsConfig.getEditorGuiScale();
        int x = (int)Math.round(minecraft.mouseHandler.xpos() * (1.0D / scale));
        int y = (int)Math.round(minecraft.mouseHandler.ypos() * (1.0D / scale));

        return !host.pointerInWorldViewport(x, y);
    }

    /// Hands the pointer back to the editor after the world viewport grabbed it.
    ///
    /// While the pointer is grabbed [#snatchInputs] is false, so no key press ever reaches the
    /// editor screens — `MixinKeyboardHandler` calls this from the head of `keyPress` instead,
    /// before the game gets to act on the key.
    ///
    /// Returns whether the event was consumed, i.e. the pointer was actually released.
    public boolean tryUngrabMouse(final KeyEvent event) {
        if (!isActive() || !minecraft.mouseHandler.isMouseGrabbed() || !EditorKeyMappings.UNGRAB.matches(event)) {
            return false;
        }

        // Puts the cursor back at the window center, where the world viewport usually is
        minecraft.mouseHandler.releaseMouse();

        return true;
    }

    /// Render the editor layout based on custom screen [#mainScreen]
    public void renderEditorLayout(final int mouseX, final int mouseY, final float a) {
        if (!isActive()) {
            throw new IllegalStateException("Can't render AkytheraEditorLayoutManager editor since it's deactivated");
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            editorRenderTarget.getColorTexture(),
            TRANSPARENT_CLEAR_COLOR,
            editorRenderTarget.getDepthTexture(),
            0.0
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

        // Built only once the window carries the editor's size and scale. A GuiGraphicsExtractor
        // seeds its scissor stack with a root rectangle of `guiWidth() x guiHeight()` read off the
        // window right here, and every enableScissor intersects against it — so one built while the
        // window still described the world viewport would clip the layout to the viewport's bounds.
        GuiRenderState guiRenderState = minecraft.gameRenderer.gameRenderState().guiRenderState;
        guiRenderState.reset();

        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, guiRenderState, mouseX, mouseY);

        // Force mouse pointer out of the screen while the pointer belongs to the modal layer. This
        // prevents activating hover effects for objects behind it.
        boolean pointerOnModalLayer = ModalManager.getInstance().claimsPointer(mouseX, mouseY);

        screenStacks.lastElement().extractRenderStateWithTooltipAndSubtitles(
            graphics,
            pointerOnModalLayer ? -1 : mouseX,
            pointerOnModalLayer ? -1 : mouseY,
            a
        );

        ModalManager.getInstance().extractRenderState(graphics, mouseX, mouseY, a);

        // Toasts on top of the editor layout. The in-game extraction is suppressed while the editor is
        // active (see MixinGameRenderer#extractToasts); re-extracting here draws them into the editor
        // render target at the editor gui scale, so a toast sits top-right of the layout, not the world
        // viewport. The window is already sized to the screen at the editor scale above, so the toast's
        // own xPos/yPos resolve against the editor layout.
        minecraft.gui.toastManager().extractRenderState(graphics);

        // Apply cursor
        graphics.applyCursor(minecraft.getWindow());

        minecraft.gameRenderer.gameRenderState().windowRenderState.width = minecraft.getWindow().getScreenWidth();
        minecraft.gameRenderer.gameRenderState().windowRenderState.height = minecraft.getWindow().getScreenHeight();
        minecraft.gameRenderer.gameRenderState().windowRenderState.guiScale = scale;

        // By turning on intercept mode, GuiRenderer will start to drawing submitted nodes by GuiGraphicsExtractor.
        setInterceptMainRenderTarget(true);
        GuiRenderer guiRenderer = ((GameRendererAccessor) minecraft.gameRenderer).akytheralabs$guiRenderer();
        guiRenderer.render();
        // Recover intercepting state
        setInterceptMainRenderTarget(false);

        guiRenderer.endFrame();

        // Restore the viewport size
        minecraft.getWindow().setWidth(fullscreenWidth);
        minecraft.getWindow().setHeight(fullscreenHeight);
        minecraft.getWindow().setGuiScale(fullscreenGuiScale);

        // Restore window state
        minecraft.gameRenderer.gameRenderState().windowRenderState.width = fullscreenWidth;
        minecraft.gameRenderer.gameRenderState().windowRenderState.height = fullscreenHeight;
        minecraft.gameRenderer.gameRenderState().windowRenderState.guiScale = fullscreenGuiScale;
    }
}
