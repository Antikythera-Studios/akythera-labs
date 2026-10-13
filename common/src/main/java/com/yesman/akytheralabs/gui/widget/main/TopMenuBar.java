package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/// Top menu bar with File/Edit/View/Tools/Help menus and dropdown support.
public final class TopMenuBar extends AbstractContainerWidget implements AnchoredWidget {
    private static final int ITEM_PAD = 8;
    private static final int TITLE_PAD = 6;
    private static final int DROPDOWN_W = 120;

    /// The menu bar every screen shows, with every option at a fixed position. A screen differs only
    /// in which [TopMenuOption]s its [UniversalMenu.Actions] wires up — the rest stay in place,
    /// greyed out and unpressable.
    private static final List<Menu> MENUS = List.of(
        new Menu(LocalizedMessage.translatable(LangKeys.GUI_MENU_BAR_FILE), List.of(
            new UniversalMenu.OptionRow<>(TopMenuOption.SAVE),
            new UniversalMenu.OptionRow<>(TopMenuOption.COMPILE),
            new UniversalMenu.GroupRow<TopMenuOption>(Component.translatable(LangKeys.GUI_MENU_BAR_FILE_EXPORT), List.of(
                new UniversalMenu.OptionRow<>(TopMenuOption.EXPORT_AS_PACK),
                new UniversalMenu.OptionRow<>(TopMenuOption.EXPORT_AS_SOURCE_CODE)
            )),
            new UniversalMenu.OptionRow<>(TopMenuOption.CHANGE_PARENT),
            new UniversalMenu.OptionRow<>(TopMenuOption.DELETE_PROJECT)
        )),
        new Menu(LocalizedMessage.translatable(LangKeys.GUI_MENU_BAR_EDIT), List.of(
            new UniversalMenu.OptionRow<>(TopMenuOption.LANGUAGE),
            new UniversalMenu.OptionRow<>(TopMenuOption.LOCALIZATION),
            new UniversalMenu.OptionRow<>(TopMenuOption.PROJECT_SETTINGS),
            new UniversalMenu.OptionRow<>(TopMenuOption.EDITOR_PREFERENCES),
            new UniversalMenu.OptionRow<>(TopMenuOption.ENTITY_PREVIEW_MODEL_MAPPING)
        )),
        new Menu(LocalizedMessage.translatable(LangKeys.GUI_MENU_BAR_VIEW), List.of(
            new UniversalMenu.OptionRow<>(TopMenuOption.SELECT_PREVIEW_MODEL),
            new UniversalMenu.OptionRow<>(TopMenuOption.SELECT_PREVIEW_ANIMATION),
            new UniversalMenu.OptionRow<>(TopMenuOption.SELECT_PREVIEW_ITEM),
            new UniversalMenu.OptionRow<>(TopMenuOption.FRAME_CAMERA)
        )),
        new Menu(LocalizedMessage.translatable(LangKeys.GUI_MENU_BAR_TOOLS), List.of(
            new UniversalMenu.OptionRow<>(TopMenuOption.ANIMATION_RETARGET),
            new UniversalMenu.OptionRow<>(TopMenuOption.MODEL_TRANSFORMER),
            new UniversalMenu.OptionRow<>(TopMenuOption.MODEL_SKINNING),
            new UniversalMenu.OptionRow<>(TopMenuOption.DEBUG),
            new UniversalMenu.OptionRow<>(TopMenuOption.CONSOLE)
        )),
        new Menu(LocalizedMessage.translatable(LangKeys.GUI_MENU_BAR_HELP), List.of(
            new UniversalMenu.OptionRow<>(TopMenuOption.DOCS),
            new UniversalMenu.OptionRow<>(TopMenuOption.ISSUE_REPORT)
        ))
    );

    private final Font font;
    private final AkytheraButton closeScreenButton;
    private final List<AkytheraButton> menuButtonList;
    private final AkytheraButton[] menuButtons;

    /// One dropdown per [#MENUS] entry, in the same order. Each is a [ContextMenuContainer] so
    /// options can nest into sub-lists (`Export > As Pack` / `As Source Code`).
    private final ContextMenuContainer[] dropdowns;

    /// Optional unsaved-changes probe. When it returns `true` a `*` is drawn in front of the title.
    /// Null means always clean.
    private @Nullable BooleanSupplier dirtyChecker;

    public TopMenuBar(
        Font font,
        Component title,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType,
        Runnable onClose,
        UniversalMenu.Actions<TopMenuOption> actions
    ) {
        super(0, 0, 0, 0, title, AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        Minecraft minecraft = Minecraft.getInstance();

        menuButtons = new AkytheraButton[MENUS.size()];
        dropdowns = new ContextMenuContainer[MENUS.size()];

        for (int index = 0; index < MENUS.size(); index++) {
            final int i = index;
            Menu menu = MENUS.get(i);

            menuButtons[i] = AkytheraButton.buttonBuilder(font, menu.name(), () -> onMenuClick(i))
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
                .isHighlighted(_ -> {
                    AbstractWidget w = ModalManager.getInstance().getModal();
                    return w != null && w == dropdowns[i];
                })
                .build();

            ContextMenuContainer.PageBuilder page = ContextMenuContainer.builder(minecraft).pageWidth(DROPDOWN_W);
            UniversalMenu.populate(page, menu.rows(), actions, DROPDOWN_W);
            dropdowns[i] = page.build();
        }

        closeScreenButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.handwritten("X"), onClose)
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(1))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_BOTTOM)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        menuButtonList = Stream.concat(Stream.of(menuButtons), Stream.of(closeScreenButton)).toList();
    }

    /// Installs the unsaved-changes probe driving the title's `*` marker. See [#dirtyChecker].
    public void setDirtyChecker(BooleanSupplier dirtyChecker) {
        this.dirtyChecker = dirtyChecker;
    }

    private void onMenuClick(int i) {
        ModalManager manager = ModalManager.getInstance();

        if (manager.getModal() == dropdowns[i]) {
            manager.closeModal();
            return;
        }

        // Switching menus: pop whatever dropdown was up so they don't stack.
        if (manager.getModal() != null) {
            manager.closeModal();
        }

        // openModalWidget repositions the container (and closes any open sub-pages).
        manager.openModalWidget(dropdowns[i], menuButtons[i].getX(), getY() + getHeight());
    }

    private void layoutButtons() {
        int curX = getX();

        for (AkytheraButton menuButton : menuButtons) {
            int w = font.width(menuButton.getMessage()) + ITEM_PAD * 2;
            menuButton.setX(curX);
            menuButton.setY(getY());
            menuButton.setWidth(w);
            menuButton.setHeight(getHeight() - 1);
            curX += w;
        }

        closeScreenButton.setX1(AnchoredWidget.pixel(getWidth() - (getHeight() - 1)));
        closeScreenButton.setX2(AnchoredWidget.pixel(getHeight() - 1));
        closeScreenButton.relocate(getRectangle());
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return menuButtonList;
    }

    /// Content fits entirely within the bar height — disables scrolling.
    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        graphics.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF2E2E2E);

        for (AkytheraButton button : menuButtons) {
            button.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        // Prefix a `*` while the screen has unsaved changes (the save point no longer matches the
        // history stack's top), dropped again once a save re-marks the point.
        Component title = dirtyChecker != null && dirtyChecker.getAsBoolean()
            ? Component.literal("*").append(getMessage())
            : getMessage();
        int titleWidth = font.width(title);
        graphics.text(font, title, closeScreenButton.getX() - titleWidth - TITLE_PAD, y + (h - font.lineHeight) / 2, 0xFFCCCCCC);

        closeScreenButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {}

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        layoutButtons();
    }

    public static UniversalMenu.Actions<TopMenuOption> actions() {
        return UniversalMenu.actions();
    }

    /// One top-level menu of [#MENUS] — the bar button plus the rows of its dropdown.
    private record Menu(LocalizedMessage name, List<UniversalMenu.Row<TopMenuOption>> rows) {}

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
